package com.fptpost.modules.guarantee.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.dispute.dto.response.ComplaintPerson;
import com.fptpost.modules.dispute.enums.ComplaintStatus;
import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.dispute.repository.ComplaintDirectory;
import com.fptpost.modules.dispute.repository.ComplaintRepository;
import com.fptpost.modules.guarantee.dto.request.CompensationDecisionRequest;
import com.fptpost.modules.guarantee.dto.request.FundContributionRequest;
import com.fptpost.modules.guarantee.dto.response.CompensationResponse;
import com.fptpost.modules.guarantee.dto.response.GuaranteeFundResponse;
import com.fptpost.modules.guarantee.enums.CompensationDecision;
import com.fptpost.modules.guarantee.enums.CompensationStatus;
import com.fptpost.modules.guarantee.exception.GuaranteeErrorCode;
import com.fptpost.modules.guarantee.model.Compensation;
import com.fptpost.modules.guarantee.model.GuaranteeFundAccount;
import com.fptpost.modules.guarantee.repository.CompensationRepository;
import com.fptpost.modules.guarantee.repository.GuaranteeFundRepository;
import com.fptpost.modules.guarantee.repository.GuaranteeLedgerRepository;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.*;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * UC-52: Admin xét duyệt bồi thường từ Quỹ bảo đảm. Khoản được duyệt chi ngay nếu quỹ đủ và không có khoản nào
 * xếp hàng trước; ngược lại xếp hàng và được chi theo thứ tự phán quyết khi quỹ có thêm tiền (A1, BR-R-079).
 */
@Service
@RequiredArgsConstructor
public class GuaranteeFundService {

    static final String FUND_ACCOUNT = "GUARANTEE_FUND";
    private static final String SOURCE_ACCOUNT = "PLATFORM_REVENUE";
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final List<LedgerTransactionType> FUND_TYPES =
            List.of(LedgerTransactionType.GUARANTEE_FUND_CONTRIBUTION, LedgerTransactionType.GUARANTEE_FUND_PAYOUT);

    private final CompensationRepository compensations;
    private final GuaranteeFundRepository funds;
    private final GuaranteeLedgerRepository fundLedger;
    private final LedgerTransactionRepository ledger;
    private final WalletRepository wallets;
    private final ComplaintRepository complaints;
    private final ComplaintDirectory directory;
    private final OrderRepository orders;
    private final IdentityUserAccess userAccess;
    private final NotificationPublisher notifications;
    private final AuditOutboxService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public GuaranteeFundResponse overview() {
        long balance = funds.findById(GuaranteeFundAccount.MAIN).map(GuaranteeFundAccount::getBalance)
                .orElseGet(() -> funds.ledgerBalance(FUND_ACCOUNT));
        Map<CompensationStatus, long[]> totals = new EnumMap<>(CompensationStatus.class);
        for (Object[] row : compensations.totalsByStatus()) {
            totals.put((CompensationStatus) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        long[] none = {0, 0};
        List<GuaranteeFundResponse.Movement> movements = fundLedger.findTop20ByTypeInOrderByCreatedAtDescIdDesc(FUND_TYPES).stream()
                .map(item -> new GuaranteeFundResponse.Movement(item.getTransactionCode(), item.getType(), item.getAmount(),
                        item.getDescription(), item.getCreatedAt())).toList();
        return new GuaranteeFundResponse(balance,
                totals.getOrDefault(CompensationStatus.PENDING_REVIEW, none)[0], totals.getOrDefault(CompensationStatus.PENDING_REVIEW, none)[1],
                totals.getOrDefault(CompensationStatus.QUEUED, none)[0], totals.getOrDefault(CompensationStatus.QUEUED, none)[1],
                totals.getOrDefault(CompensationStatus.PAID, none)[0], totals.getOrDefault(CompensationStatus.PAID, none)[1],
                movements);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CompensationResponse> list(String status, int page, int size) {
        Set<CompensationStatus> statuses;
        try {
            statuses = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())
                    ? EnumSet.allOf(CompensationStatus.class)
                    : EnumSet.of(CompensationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new AppException(GuaranteeErrorCode.COMPENSATION_FILTER_INVALID);
        }
        Page<Compensation> result = compensations.search(statuses, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return PageResponse.from(responses(result));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CompensationResponse decide(UUID actorId, UUID complaintId, CompensationDecisionRequest request) {
        User actor = userAccess.read(actorId);
        Complaint complaint = complaints.findByPublicId(complaintId)
                .orElseThrow(() -> new AppException(GuaranteeErrorCode.COMPENSATION_NOT_FOUND));
        Compensation item = compensations.findByComplaintIdForUpdate(complaint.getId())
                .orElseThrow(() -> new AppException(GuaranteeErrorCode.COMPENSATION_NOT_FOUND));
        if (item.getStatus() != CompensationStatus.PENDING_REVIEW) throw new AppException(GuaranteeErrorCode.COMPENSATION_STATE_INVALID);
        if (actor.getId().equals(item.getBeneficiaryId())) throw new AppException(GuaranteeErrorCode.COMPENSATION_SELF_FORBIDDEN);
        Instant now = clock.instant();
        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().strip();
        item.setReviewedBy(actor.getId());
        item.setReviewedAt(now);
        item.setNote(note);
        item.setUpdatedAt(now);

        if (request.getDecision() == CompensationDecision.REJECT) {
            if (note == null) throw new AppException(GuaranteeErrorCode.COMPENSATION_NOTE_REQUIRED);
            item.setStatus(CompensationStatus.REJECTED);
            compensations.saveAndFlush(item);
            notifyBeneficiary(item, "rejected", "Đề nghị bồi thường không được duyệt", "Lý do: " + note);
            audit.enqueue("COMPENSATION_REJECTED", "COMPENSATION", item.getPublicId().toString(),
                    Map.of("status", CompensationStatus.PENDING_REVIEW.name()), Map.of("status", item.getStatus().name()));
            return response(item);
        }

        long amount = request.getAmount() == null ? item.getRequestedAmount() : request.getAmount();
        if (amount < 1 || amount > item.getRequestedAmount()) throw new AppException(GuaranteeErrorCode.COMPENSATION_AMOUNT_INVALID);
        item.setApprovedAmount(amount);
        GuaranteeFundAccount fund = lockFund(now);
        if (compensations.countByStatus(CompensationStatus.QUEUED) == 0 && fund.getBalance() >= amount) {
            pay(item, fund, now);
        } else {
            item.setStatus(CompensationStatus.QUEUED);
            item.setQueuedAt(now);
            notifyBeneficiary(item, "queued", "Bồi thường đã được duyệt và đang chờ quỹ",
                    "Khoản " + money(amount) + " sẽ được chi theo thứ tự phán quyết khi Quỹ bảo đảm đủ số dư.");
        }
        compensations.saveAndFlush(item);
        audit.enqueue("COMPENSATION_APPROVED", "COMPENSATION", item.getPublicId().toString(),
                Map.of("status", CompensationStatus.PENDING_REVIEW.name()),
                Map.of("status", item.getStatus().name(), "approvedAmount", amount));
        return response(item);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GuaranteeFundResponse contribute(FundContributionRequest request) {
        Instant now = clock.instant();
        GuaranteeFundAccount fund = lockFund(now);
        long before = fund.getBalance();
        LedgerTransaction transaction = post(LedgerTransactionType.GUARANTEE_FUND_CONTRIBUTION, "FUND_CONTRIB_" + UUID.randomUUID(),
                null, request.getAmount(), null, "GUARANTEE_FUND", null,
                "Trích vào Quỹ bảo đảm: " + request.getNote().strip(), SOURCE_ACCOUNT, "REVENUE", FUND_ACCOUNT);
        fund.setBalance(before + request.getAmount());
        fund.setUpdatedAt(now);
        audit.enqueue("GUARANTEE_FUND_CONTRIBUTED", "GUARANTEE_FUND", transaction.getTransactionCode(),
                Map.of("balance", before), Map.of("balance", fund.getBalance(), "amount", request.getAmount()));
        drainQueue(fund, now);
        return overview();
    }

    /** Chi các khoản đang xếp hàng theo thứ tự phán quyết; dừng ở khoản đầu tiên quỹ chưa đủ để giữ đúng thứ tự. */
    @Transactional
    public int processQueue() {
        Instant now = clock.instant();
        if (compensations.countByStatus(CompensationStatus.QUEUED) == 0) return 0;
        return drainQueue(lockFund(now), now);
    }

    /** Tạo khoản bồi thường chờ duyệt cho phán quyết cuối cùng có đề nghị bồi thường; gọi lại nhiều lần không tạo trùng. */
    @Transactional
    public void register(Long complaintId, UUID complaintPublicId, Long orderId, Long beneficiaryId, long amount, Instant ruledAt) {
        if (amount <= 0 || beneficiaryId == null || compensations.existsByComplaintId(complaintId)) return;
        Instant now = clock.instant();
        Compensation item = compensations.saveAndFlush(Compensation.builder().publicId(UUID.randomUUID()).complaintId(complaintId)
                .orderId(orderId).beneficiaryId(beneficiaryId).requestedAmount(amount).status(CompensationStatus.PENDING_REVIEW)
                .ruledAt(ruledAt == null ? now : ruledAt).createdAt(now).updatedAt(now).build());
        String code = complaints.findById(complaintId).map(Complaint::getCode).orElse(complaintPublicId.toString());
        for (UUID admin : directory.activeStaff("ADMIN")) {
            notifications.publish(admin, NotificationCategory.PAYMENT, "compensation:" + item.getPublicId() + ":review",
                    "Đề nghị bồi thường chờ duyệt", "Khiếu nại " + code + " đề nghị chi " + money(amount)
                            + " từ Quỹ bảo đảm.", "/admin/guarantee");
        }
    }

    /** Bổ sung khoản bồi thường cho khiếu nại đã đóng trước khi phân hệ này hoạt động. */
    @Transactional
    public int registerMissing() {
        int created = 0;
        for (Complaint complaint : compensations.closedWithoutCompensation(ComplaintStatus.CLOSED)) {
            Order order = orders.findById(complaint.getOrderId()).orElse(null);
            if (order == null) continue;
            register(complaint.getId(), complaint.getPublicId(), order.getId(), order.getSenderId(),
                    complaint.getCompensationAmount(), complaint.getRuledAt());
            created++;
        }
        return created;
    }

    private int drainQueue(GuaranteeFundAccount fund, Instant now) {
        int paid = 0;
        for (Compensation item : compensations.queue()) {
            if (fund.getBalance() < item.getApprovedAmount()) break;
            pay(item, fund, now);
            item.setUpdatedAt(now);
            compensations.save(item);
            paid++;
        }
        return paid;
    }

    private void pay(Compensation item, GuaranteeFundAccount fund, Instant now) {
        long amount = item.getApprovedAmount();
        LedgerTransaction transaction = post(LedgerTransactionType.GUARANTEE_FUND_PAYOUT, "COMPENSATION_PAYOUT_" + item.getId(),
                item.getOrderId(), amount, item.getReviewedBy(), "COMPENSATION", item.getPublicId().toString(),
                "Chi bồi thường từ Quỹ bảo đảm cho đơn #" + item.getOrderId(), FUND_ACCOUNT, "LIABILITY",
                "USER_WALLET_" + item.getBeneficiaryId());
        Wallet wallet = wallets.findByUserIdForUpdate(item.getBeneficiaryId()).orElseGet(() -> wallets.save(Wallet.builder()
                .userId(item.getBeneficiaryId()).available(0L).escrowHeld(0L).pendingWithdraw(0L).build()));
        wallet.setAvailable((wallet.getAvailable() == null ? 0 : wallet.getAvailable()) + amount);
        fund.setBalance(fund.getBalance() - amount);
        fund.setUpdatedAt(now);
        item.setStatus(CompensationStatus.PAID);
        item.setPaidAt(now);
        item.setLedgerTransactionId(transaction.getId());
        notifyBeneficiary(item, "paid", "Đã nhận bồi thường từ Quỹ bảo đảm", money(amount) + " đã được cộng vào ví FPTPost của bạn.");
    }

    private LedgerTransaction post(LedgerTransactionType type, String key, Long orderId, long amount, Long createdBy, String refType,
                                   String refId, String description, String debitAccount, String debitType, String creditAccount) {
        LedgerTransaction transaction = LedgerTransaction.builder()
                .transactionCode("LTX-GF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .type(type)
                .status(LedgerTransactionStatus.COMPLETED)
                .idempotencyKey(key)
                .orderId(orderId)
                .refType(refType)
                .refId(refId)
                .amount(amount)
                .createdBy(createdBy)
                .description(description.length() <= 500 ? description : description.substring(0, 499) + "…")
                .build();
        transaction.addEntry(LedgerEntry.builder().accountCode(debitAccount).accountType(debitType).debit(amount).credit(0L).build());
        transaction.addEntry(LedgerEntry.builder().accountCode(creditAccount).accountType("LIABILITY").debit(0L).credit(amount).build());
        return ledger.save(transaction);
    }

    private GuaranteeFundAccount lockFund(Instant now) {
        return funds.findForUpdate(GuaranteeFundAccount.MAIN).orElseGet(() -> {
            funds.saveAndFlush(GuaranteeFundAccount.builder().id(GuaranteeFundAccount.MAIN)
                    .balance(funds.ledgerBalance(FUND_ACCOUNT)).updatedAt(now).build());
            return funds.findForUpdate(GuaranteeFundAccount.MAIN).orElseThrow();
        });
    }

    private void notifyBeneficiary(Compensation item, String step, String title, String body) {
        directory.reachable(item.getBeneficiaryId()).ifPresent(recipient -> notifications.publish(recipient, NotificationCategory.PAYMENT,
                "compensation:" + item.getPublicId() + ":" + step, title, body, "/wallet"));
    }

    private Page<CompensationResponse> responses(Page<Compensation> page) {
        List<Compensation> items = page.getContent();
        Map<Long, Complaint> byComplaint = complaints.findAllById(items.stream().map(Compensation::getComplaintId).toList()).stream()
                .collect(Collectors.toMap(Complaint::getId, Function.identity()));
        Map<Long, Order> byOrder = orders.findAllById(items.stream().map(Compensation::getOrderId).distinct().toList()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        Set<Long> people = items.stream().flatMap(item -> Stream.of(item.getBeneficiaryId(), item.getReviewedBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ComplaintPerson> byPerson = people.isEmpty() ? Map.of() : directory.people(people).stream()
                .collect(Collectors.toMap(ComplaintPerson::id, Function.identity()));
        List<Long> queued = compensations.queue().stream().map(Compensation::getId).toList();
        return page.map(item -> toResponse(item, byComplaint.get(item.getComplaintId()), byOrder.get(item.getOrderId()), byPerson, queued));
    }

    private CompensationResponse response(Compensation item) {
        return responses(new org.springframework.data.domain.PageImpl<>(List.of(item))).getContent().getFirst();
    }

    private static CompensationResponse toResponse(Compensation item, Complaint complaint, Order order,
                                                   Map<Long, ComplaintPerson> people, List<Long> queued) {
        int position = queued.indexOf(item.getId());
        return new CompensationResponse(item.getPublicId(), complaint == null ? null : complaint.getPublicId(),
                complaint == null ? null : complaint.getCode(), order == null ? null : order.getOrderCode(),
                order == null ? null : order.getDeclaredValue(), people.get(item.getBeneficiaryId()), item.getRequestedAmount(),
                item.getApprovedAmount(), item.getStatus(), position < 0 ? null : position + 1, item.getNote(), item.getRuledAt(),
                item.getReviewedBy() == null ? null : people.get(item.getReviewedBy()), item.getReviewedAt(), item.getQueuedAt(),
                item.getPaidAt());
    }

    private static String money(long amount) {
        return NumberFormat.getIntegerInstance(VIETNAMESE).format(amount) + " ₫";
    }
}

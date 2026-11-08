package com.fptpost.modules.payment.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.dto.response.UserAccountEntry;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.payment.dto.*;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.model.WithdrawalRequest;
import com.fptpost.modules.payment.model.WithdrawalStatus;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.repository.WithdrawalRequestRepository;
import com.fptpost.modules.payment.service.payout.PayoutProvider;
import com.fptpost.modules.payment.service.payout.PayoutResult;
import com.fptpost.modules.payment.util.CsvSupport;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * UC-41: hàng đợi duyệt rút tiền, duyệt hai cấp (maker–checker), chi trả qua PayoutProvider,
 * xuất lệnh chi và nhập kết quả chi hàng loạt.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWithdrawalService {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<WithdrawalStatus> OPEN = EnumSet.of(WithdrawalStatus.PENDING_REVIEW,
            WithdrawalStatus.APPROVED_L1, WithdrawalStatus.APPROVED, WithdrawalStatus.PROCESSING);
    private static final Set<WithdrawalStatus> REVIEWABLE = EnumSet.of(WithdrawalStatus.PENDING_REVIEW, WithdrawalStatus.APPROVED_L1);
    private static final Set<WithdrawalStatus> SETTLEABLE = EnumSet.of(WithdrawalStatus.APPROVED, WithdrawalStatus.PROCESSING);
    private static final List<String> EXPORT_HEADER = List.of("request_code", "bank_name", "account_number",
            "account_holder_name", "amount", "transfer_content", "vietqr_url", "result", "reference", "reason");
    private static final int EXPORT_LIMIT = 200;
    private static final int IMPORT_ROW_LIMIT = 500;
    private static final long IMPORT_MAX_BYTES = 1024 * 1024;
    private static final Map<String, String> VIETQR_BANKS = Map.ofEntries(
            Map.entry("vietcombank", "VCB"), Map.entry("vcb", "VCB"),
            Map.entry("techcombank", "TCB"), Map.entry("tcb", "TCB"),
            Map.entry("bidv", "BIDV"), Map.entry("vietinbank", "ICB"),
            Map.entry("agribank", "VBA"), Map.entry("mbbank", "MB"), Map.entry("mb", "MB"),
            Map.entry("acb", "ACB"), Map.entry("tpbank", "TPB"), Map.entry("vpbank", "VPB"),
            Map.entry("sacombank", "STB"), Map.entry("vib", "VIB"), Map.entry("shb", "SHB"),
            Map.entry("hdbank", "HDB"), Map.entry("ocb", "OCB"), Map.entry("msb", "MSB"));

    private final WithdrawalRequestRepository withdrawals;
    private final WalletRepository wallets;
    private final LedgerService ledgerService;
    private final PayoutProvider payoutProvider;
    private final ParameterService parameterService;
    private final UserDirectoryLookup directory;
    private final IdentityUserAccess userAccess;
    private final NotificationRecipientLookup recipients;
    private final NotificationPublisher notifications;
    private final AuditOutboxService audit;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminWithdrawalResponse> list(UUID actorPublicId, AdminWithdrawalSearchRequest request) {
        User admin = userAccess.read(actorPublicId);
        Set<WithdrawalStatus> statuses = statuses(request.getStatus());
        String pattern = pattern(request.getQ());
        Specification<WithdrawalRequest> filter = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statuses != null) predicates.add(root.get("status").in(statuses));
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("requestCode")), pattern, '!'),
                        cb.like(cb.lower(root.get("accountNumber")), pattern, '!'),
                        cb.like(cb.lower(root.get("accountHolderName")), pattern, '!')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Sort.Direction direction = OPEN.equals(statuses) ? Sort.Direction.ASC : Sort.Direction.DESC;
        int page = request.getPage() == null ? 0 : request.getPage();
        int size = request.getSize() == null ? 20 : request.getSize();
        Page<WithdrawalRequest> result = withdrawals.findAll(filter,
                PageRequest.of(page, size, Sort.by(direction, "createdAt").and(Sort.by(direction, "id"))));
        View view = view(admin, result.getContent());
        return PageResponse.from(result.map(item -> response(item, view, false)));
    }

    @Transactional(readOnly = true)
    public AdminWithdrawalResponse detail(UUID actorPublicId, Long id) {
        User admin = userAccess.read(actorPublicId);
        WithdrawalRequest withdrawal = withdrawals.findById(id)
                .orElseThrow(() -> new AppException(PaymentErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));
        return response(withdrawal, view(admin, List.of(withdrawal)), true);
    }

    @Transactional
    public AdminWithdrawalResponse approve(UUID actorPublicId, Long id) {
        User admin = userAccess.read(actorPublicId);
        WithdrawalRequest withdrawal = lock(id);
        requireOtherAccount(withdrawal, admin);
        Instant now = clock.instant();
        Map<String, Object> before = state(withdrawal);
        long dualApprovalThreshold = parameterService.getSnapshot().integer("DUAL_APPROVE");

        if (withdrawal.getStatus() == WithdrawalStatus.PENDING_REVIEW && withdrawal.getAmount() >= dualApprovalThreshold) {
            withdrawal.setStatus(WithdrawalStatus.APPROVED_L1);
            withdrawal.setFirstApprovedBy(admin.getId());
            withdrawal.setFirstApprovedAt(now);
            audit.enqueue("WITHDRAWAL_APPROVED_L1", "WITHDRAWAL_REQUEST", withdrawal.getRequestCode(), before, state(withdrawal));
        } else if (withdrawal.getStatus() == WithdrawalStatus.PENDING_REVIEW || withdrawal.getStatus() == WithdrawalStatus.APPROVED_L1) {
            if (admin.getId().equals(withdrawal.getFirstApprovedBy())) {
                throw new AppException(PaymentErrorCode.WITHDRAWAL_SECOND_APPROVER_REQUIRED);
            }
            withdrawal.setStatus(WithdrawalStatus.APPROVED);
            withdrawal.setApprovedBy(admin.getId());
            withdrawal.setApprovedAt(now);
            audit.enqueue("WITHDRAWAL_APPROVED", "WITHDRAWAL_REQUEST", withdrawal.getRequestCode(), before, state(withdrawal));
            dispatch(withdrawal, admin, now);
        } else {
            throw new AppException(PaymentErrorCode.WITHDRAWAL_STATUS_INVALID);
        }
        withdrawals.saveAndFlush(withdrawal);
        return response(withdrawal, view(admin, List.of(withdrawal)), true);
    }

    @Transactional
    public AdminWithdrawalResponse reject(UUID actorPublicId, Long id, WithdrawalReasonRequest input) {
        User admin = userAccess.read(actorPublicId);
        WithdrawalRequest withdrawal = lock(id);
        requireOtherAccount(withdrawal, admin);
        if (!REVIEWABLE.contains(withdrawal.getStatus())) throw new AppException(PaymentErrorCode.WITHDRAWAL_STATUS_INVALID);
        Instant now = clock.instant();
        Map<String, Object> before = state(withdrawal);
        refund(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setRejectionReason(input.getReason().strip());
        withdrawal.setRejectedBy(admin.getId());
        withdrawal.setRejectedAt(now);
        withdrawals.saveAndFlush(withdrawal);
        audit.enqueue("WITHDRAWAL_REJECTED", "WITHDRAWAL_REQUEST", withdrawal.getRequestCode(), before, state(withdrawal));
        notifyApplicant(withdrawal, "Yêu cầu rút tiền bị từ chối",
                "Yêu cầu " + withdrawal.getRequestCode() + " không được duyệt. Số tiền đã được hoàn về số dư khả dụng.");
        return response(withdrawal, view(admin, List.of(withdrawal)), true);
    }

    @Transactional
    public AdminWithdrawalResponse markPaid(UUID actorPublicId, Long id, WithdrawalPaidRequest input) {
        User admin = userAccess.read(actorPublicId);
        WithdrawalRequest withdrawal = lock(id);
        settlePaid(withdrawal, admin, input.getReference().strip());
        return response(withdrawal, view(admin, List.of(withdrawal)), true);
    }

    @Transactional
    public AdminWithdrawalResponse markFailed(UUID actorPublicId, Long id, WithdrawalReasonRequest input) {
        User admin = userAccess.read(actorPublicId);
        WithdrawalRequest withdrawal = lock(id);
        settleFailed(withdrawal, admin, input.getReason().strip());
        return response(withdrawal, view(admin, List.of(withdrawal)), true);
    }

    /** Moves approved requests to PROCESSING and returns a bank-transfer sheet; re-import it with result columns filled. */
    @Transactional
    public WithdrawalPayoutFile export(UUID actorPublicId, List<Long> ids) {
        User admin = userAccess.read(actorPublicId);
        List<WithdrawalRequest> items;
        if (ids == null || ids.isEmpty()) {
            items = withdrawals.findByStatusForUpdate(WithdrawalStatus.APPROVED, PageRequest.of(0, EXPORT_LIMIT)).stream()
                    .filter(item -> !item.getUserId().equals(admin.getId())).toList();
        } else {
            Set<Long> requested = new LinkedHashSet<>(ids);
            items = withdrawals.findByStatusAndIdInForUpdate(WithdrawalStatus.APPROVED, requested);
            if (items.size() != requested.size()) throw new AppException(PaymentErrorCode.WITHDRAWAL_STATUS_INVALID);
            items.forEach(item -> requireOtherAccount(item, admin));
        }
        if (items.isEmpty()) throw new AppException(PaymentErrorCode.WITHDRAWAL_EXPORT_EMPTY);

        Instant now = clock.instant();
        StringBuilder csv = new StringBuilder("﻿").append(String.join(",", EXPORT_HEADER)).append("\r\n");
        for (WithdrawalRequest item : items) {
            Map<String, Object> before = state(item);
            item.setStatus(WithdrawalStatus.PROCESSING);
            item.setPayoutProvider(payoutProvider.code());
            item.setPayoutSubmittedAt(now);
            audit.enqueue("WITHDRAWAL_PAYOUT_EXPORTED", "WITHDRAWAL_REQUEST", item.getRequestCode(), before, state(item));
            long net = netAmount(item);
            String content = "FPTPOST " + item.getRequestCode();
            csv.append(CsvSupport.row(List.of(item.getRequestCode(), item.getBankName(), item.getAccountNumber(),
                    item.getAccountHolderName(), Long.toString(net), content,
                    vietQrUrl(item, net, content), "", "", ""))).append("\r\n");
        }
        withdrawals.saveAllAndFlush(items);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(now.atZone(VIETNAM));
        return new WithdrawalPayoutFile("lenh-chi-" + stamp + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8), items.size());
    }

    /** Each row settles in its own transaction so one bad line does not undo the rest of the bank statement. */
    public WithdrawalImportResultResponse importResults(UUID actorPublicId, MultipartFile file) {
        User admin = userAccess.read(actorPublicId);
        List<String> lines = readLines(file);
        List<String> header = CsvSupport.parse(stripBom(lines.get(0))).stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).toList();
        int codeColumn = header.indexOf("request_code");
        int resultColumn = header.indexOf("result");
        int referenceColumn = header.indexOf("reference");
        int reasonColumn = header.indexOf("reason");
        if (codeColumn < 0 || resultColumn < 0) throw new AppException(PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<WithdrawalImportErrorResponse> errors = new ArrayList<>();
        int paid = 0;
        int failed = 0;
        int processed = 0;
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            int lineNumber = index + 1;
            String code = null;
            try {
                List<String> cells = CsvSupport.parse(line);
                code = cell(cells, codeColumn);
                String result = cell(cells, resultColumn).toUpperCase(Locale.ROOT);
                if (code.isEmpty()) throw new AppException(PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID);
                if (result.isEmpty()) continue;
                processed++;
                String requestCode = code;
                if ("PAID".equals(result)) {
                    String reference = cell(cells, referenceColumn);
                    if (!reference.matches("[A-Za-z0-9._/-]{1,100}")) throw new AppException(PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID);
                    transaction.executeWithoutResult(status -> settlePaid(lockByCode(requestCode), admin, reference));
                    paid++;
                } else if ("FAILED".equals(result)) {
                    String reason = cell(cells, reasonColumn);
                    if (reason.isEmpty() || reason.length() > 500) throw new AppException(PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID);
                    transaction.executeWithoutResult(status -> settleFailed(lockByCode(requestCode), admin, reason));
                    failed++;
                } else {
                    throw new AppException(PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID);
                }
            } catch (AppException exception) {
                errors.add(new WithdrawalImportErrorResponse(lineNumber, code, exception.getErrorCode().getCode()));
            } catch (IllegalArgumentException exception) {
                errors.add(new WithdrawalImportErrorResponse(lineNumber, code, PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID.getCode()));
            }
        }
        return WithdrawalImportResultResponse.builder().processed(processed).paid(paid).failed(failed).errors(errors).build();
    }

    private void dispatch(WithdrawalRequest withdrawal, User admin, Instant now) {
        PayoutResult result = payoutProvider.submit(withdrawal);
        withdrawal.setPayoutProvider(payoutProvider.code());
        if (result.outcome() == PayoutResult.Outcome.AWAITING_MANUAL_TRANSFER) return;
        withdrawal.setStatus(WithdrawalStatus.PROCESSING);
        withdrawal.setPayoutSubmittedAt(now);
        switch (result.outcome()) {
            case PAID -> settlePaid(withdrawal, admin, result.reference());
            case FAILED -> settleFailed(withdrawal, admin, result.failureReason());
            default -> { }
        }
    }

    private void settlePaid(WithdrawalRequest withdrawal, User admin, String reference) {
        requireOtherAccount(withdrawal, admin);
        if (!SETTLEABLE.contains(withdrawal.getStatus())) throw new AppException(PaymentErrorCode.WITHDRAWAL_STATUS_INVALID);
        if (withdrawals.existsByPayoutReference(reference)) throw new AppException(PaymentErrorCode.WITHDRAWAL_REFERENCE_DUPLICATED);
        Instant now = clock.instant();
        Map<String, Object> before = state(withdrawal);
        Wallet wallet = wallets.findByUserIdForUpdate(withdrawal.getUserId())
                .orElseThrow(() -> new AppException(PaymentErrorCode.WALLET_NOT_FOUND));
        wallet.setPendingWithdraw(Math.max(0L, balance(wallet.getPendingWithdraw()) - withdrawal.getAmount()));
        wallets.save(wallet);
        if (withdrawal.getPayoutProvider() == null) withdrawal.setPayoutProvider(payoutProvider.code());
        if (withdrawal.getPayoutSubmittedAt() == null) withdrawal.setPayoutSubmittedAt(now);
        withdrawal.setStatus(WithdrawalStatus.PAID);
        withdrawal.setPayoutReference(reference);
        withdrawal.setSettledBy(admin.getId());
        withdrawal.setSettledAt(now);
        withdrawals.saveAndFlush(withdrawal);
        ledgerService.recordWithdrawalPayout(withdrawal.getUserId(), withdrawal.getAmount(), withdrawal.getRequestCode(), reference);
        audit.enqueue("WITHDRAWAL_PAID", "WITHDRAWAL_REQUEST", withdrawal.getRequestCode(), before, state(withdrawal));
        notifyApplicant(withdrawal, "Yêu cầu rút tiền đã được chi",
                "Yêu cầu " + withdrawal.getRequestCode() + " đã được chuyển khoản. Mã tham chiếu: " + reference + ".");
    }

    private void settleFailed(WithdrawalRequest withdrawal, User admin, String reason) {
        requireOtherAccount(withdrawal, admin);
        if (!SETTLEABLE.contains(withdrawal.getStatus())) throw new AppException(PaymentErrorCode.WITHDRAWAL_STATUS_INVALID);
        Instant now = clock.instant();
        Map<String, Object> before = state(withdrawal);
        refund(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.FAILED);
        withdrawal.setFailureReason(reason);
        withdrawal.setSettledBy(admin.getId());
        withdrawal.setSettledAt(now);
        withdrawals.saveAndFlush(withdrawal);
        audit.enqueue("WITHDRAWAL_FAILED", "WITHDRAWAL_REQUEST", withdrawal.getRequestCode(), before, state(withdrawal));
        notifyApplicant(withdrawal, "Chi trả rút tiền không thành công",
                "Yêu cầu " + withdrawal.getRequestCode() + " chưa chuyển khoản được. Số tiền đã được hoàn về số dư khả dụng.");
    }

    private void refund(WithdrawalRequest withdrawal) {
        Wallet wallet = wallets.findByUserIdForUpdate(withdrawal.getUserId())
                .orElseThrow(() -> new AppException(PaymentErrorCode.WALLET_NOT_FOUND));
        long amount = withdrawal.getAmount();
        wallet.setPendingWithdraw(Math.max(0L, balance(wallet.getPendingWithdraw()) - amount));
        wallet.setAvailable(balance(wallet.getAvailable()) + amount);
        wallets.save(wallet);
        ledgerService.recordWithdrawalRefund(withdrawal.getUserId(), amount, withdrawal.getRequestCode());
    }

    private void notifyApplicant(WithdrawalRequest withdrawal, String title, String body) {
        UserAccountEntry account = directory.accounts(Set.of(withdrawal.getUserId())).get(withdrawal.getUserId());
        if (account == null) return;
        boolean reachable = recipients.find(account.publicId()).map(NotificationRecipientLookup.Recipient::canReceive).orElse(false);
        if (!reachable) return;
        String eventKey = "withdrawal:" + withdrawal.getRequestCode() + ":" + withdrawal.getStatus().name().toLowerCase(Locale.ROOT);
        notifications.publish(account.publicId(), NotificationCategory.PAYMENT, eventKey, title, body, "/wallet");
    }

    private WithdrawalRequest lock(Long id) {
        return withdrawals.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(PaymentErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));
    }

    private WithdrawalRequest lockByCode(String requestCode) {
        return withdrawals.findByRequestCodeForUpdate(requestCode)
                .orElseThrow(() -> new AppException(PaymentErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));
    }

    private void requireOtherAccount(WithdrawalRequest withdrawal, User admin) {
        if (withdrawal.getUserId().equals(admin.getId())) {
            throw new AppException(PaymentErrorCode.WITHDRAWAL_SELF_REVIEW_FORBIDDEN);
        }
    }

    private Set<WithdrawalStatus> statuses(String value) {
        if (value == null || value.isBlank() || "OPEN".equalsIgnoreCase(value)) return OPEN;
        if ("ALL".equalsIgnoreCase(value)) return null;
        try {
            return EnumSet.of(WithdrawalStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new AppException(PaymentErrorCode.WITHDRAWAL_REVIEW_FILTER_INVALID);
        }
    }

    private String pattern(String query) {
        if (query == null || query.isBlank()) return null;
        String text = query.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
        return "%" + text + "%";
    }

    private View view(User admin, List<WithdrawalRequest> items) {
        Set<Long> ids = new HashSet<>();
        items.forEach(item -> Stream.of(item.getUserId(), item.getFirstApprovedBy(), item.getApprovedBy(),
                item.getRejectedBy(), item.getSettledBy()).filter(Objects::nonNull).forEach(ids::add));
        var snapshot = parameterService.getSnapshot();
        return new View(admin.getId(), directory.accounts(ids), snapshot.integer("DUAL_APPROVE"),
                snapshot.integer("WD_SLA"), clock.instant());
    }

    private AdminWithdrawalResponse response(WithdrawalRequest item, View view, boolean withReconciliation) {
        WithdrawalStatus status = item.getStatus();
        boolean ownRequest = item.getUserId().equals(view.actorId());
        Instant dueAt = addBusinessDays(item.getCreatedAt(), view.slaDays());
        return AdminWithdrawalResponse.builder()
                .id(item.getId())
                .requestCode(item.getRequestCode())
                .status(status)
                .amount(item.getAmount())
                .feeAmount(balance(item.getFeeAmount()))
                .netAmount(netAmount(item))
                .bankName(item.getBankName())
                .accountNumber(item.getAccountNumber())
                .accountHolderName(item.getAccountHolderName())
                .applicant(party(view, item.getUserId()))
                .dualApprovalRequired(item.getAmount() >= view.dualApprovalThreshold())
                .firstApprover(party(view, item.getFirstApprovedBy()))
                .firstApprovedAt(item.getFirstApprovedAt())
                .approver(party(view, item.getApprovedBy()))
                .approvedAt(item.getApprovedAt())
                .rejectedBy(party(view, item.getRejectedBy()))
                .rejectedAt(item.getRejectedAt())
                .rejectionReason(item.getRejectionReason())
                .payoutProvider(item.getPayoutProvider())
                .payoutReference(item.getPayoutReference())
                .payoutSubmittedAt(item.getPayoutSubmittedAt())
                .settledBy(party(view, item.getSettledBy()))
                .settledAt(item.getSettledAt())
                .failureReason(item.getFailureReason())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .dueAt(dueAt)
                .overdue(OPEN.contains(status) && view.now().isAfter(dueAt))
                .canApprove(REVIEWABLE.contains(status) && !ownRequest && !view.actorId().equals(item.getFirstApprovedBy()))
                .canReject(REVIEWABLE.contains(status) && !ownRequest)
                .canSettle(SETTLEABLE.contains(status) && !ownRequest)
                .reconciliation(withReconciliation ? reconciliation(item.getUserId()) : null)
                .build();
    }

    private WithdrawalReconciliationResponse reconciliation(Long userId) {
        Wallet wallet = wallets.findByUserId(userId).orElse(null);
        return WithdrawalReconciliationResponse.builder()
                .walletAvailable(wallet == null ? 0L : balance(wallet.getAvailable()))
                .walletPendingWithdraw(wallet == null ? 0L : balance(wallet.getPendingWithdraw()))
                .walletEscrowHeld(wallet == null ? 0L : balance(wallet.getEscrowHeld()))
                .paidCount(withdrawals.countByUserIdAndStatus(userId, WithdrawalStatus.PAID))
                .paidTotal(withdrawals.sumAmountByUserIdAndStatus(userId, WithdrawalStatus.PAID))
                .unsuccessfulCount(withdrawals.countByUserIdAndStatusIn(userId,
                        EnumSet.of(WithdrawalStatus.REJECTED, WithdrawalStatus.FAILED)))
                .lastPaidAt(withdrawals.findLastSettledAt(userId, WithdrawalStatus.PAID).orElse(null))
                .build();
    }

    private WithdrawalPartyResponse party(View view, Long userId) {
        if (userId == null) return null;
        UserAccountEntry account = view.accounts().get(userId);
        if (account == null) return null;
        return WithdrawalPartyResponse.builder().publicId(account.publicId()).fullName(account.fullName())
                .email(account.email()).build();
    }

    private Map<String, Object> state(WithdrawalRequest item) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", item.getStatus().name());
        state.put("amount", item.getAmount());
        if (item.getPayoutReference() != null) state.put("payoutReference", item.getPayoutReference());
        if (item.getRejectionReason() != null) state.put("rejectionReason", item.getRejectionReason());
        if (item.getFailureReason() != null) state.put("failureReason", item.getFailureReason());
        return state;
    }

    private static Instant addBusinessDays(Instant start, long days) {
        ZonedDateTime cursor = start.atZone(VIETNAM);
        long added = 0;
        while (added < days) {
            cursor = cursor.plusDays(1);
            DayOfWeek day = cursor.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) added++;
        }
        return cursor.toInstant();
    }

    private static String vietQrUrl(WithdrawalRequest item, long amount, String content) {
        String key = item.getBankName().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        String bank = VIETQR_BANKS.get(key);
        if (bank == null || !item.getAccountNumber().matches("[0-9]{6,19}")) return "";
        return "https://img.vietqr.io/image/" + bank + "-" + item.getAccountNumber() + "-compact2.png?amount=" + amount
                + "&addInfo=" + URLEncoder.encode(content, StandardCharsets.UTF_8)
                + "&accountName=" + URLEncoder.encode(item.getAccountHolderName(), StandardCharsets.UTF_8);
    }

    private static List<String> readLines(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > IMPORT_MAX_BYTES) {
            throw new AppException(PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().limit(IMPORT_ROW_LIMIT + 2L).toList();
            if (lines.isEmpty() || lines.size() > IMPORT_ROW_LIMIT + 1) throw new AppException(PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID);
            return lines;
        } catch (IOException exception) {
            throw new AppException(PaymentErrorCode.WITHDRAWAL_IMPORT_INVALID);
        }
    }

    private static String stripBom(String value) {
        return value.startsWith("﻿") ? value.substring(1) : value;
    }

    private static String cell(List<String> cells, int column) {
        return column >= 0 && column < cells.size() ? cells.get(column).strip() : "";
    }

    private static long netAmount(WithdrawalRequest item) {
        return Math.max(0L, item.getAmount() - balance(item.getFeeAmount()));
    }

    private static long balance(Long value) {
        return value == null ? 0L : value;
    }

    private record View(Long actorId, Map<Long, UserAccountEntry> accounts, long dualApprovalThreshold,
                        long slaDays, Instant now) {
    }
}

package com.fptpost.modules.dispute.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.PrivateFileStorage;
import com.fptpost.modules.dispute.dto.request.OpenComplaintCommand;
import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import com.fptpost.modules.dispute.enums.ComplaintRole;
import com.fptpost.modules.dispute.enums.ComplaintStatus;
import com.fptpost.modules.dispute.event.ComplaintClosedEvent;
import com.fptpost.modules.dispute.exception.ComplaintErrorCode;
import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.dispute.model.ComplaintEvidence;
import com.fptpost.modules.dispute.model.ComplaintMessage;
import com.fptpost.modules.dispute.repository.ComplaintEvidenceRepository;
import com.fptpost.modules.dispute.repository.ComplaintMessageRepository;
import com.fptpost.modules.dispute.repository.ComplaintRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Vòng đời khiếu nại phía hệ thống: tạo hồ sơ và đóng băng ký quỹ (gọi từ UC-50), đóng hồ sơ và xử lý tiền,
 * đóng hồ sơ hết hạn kháng nghị và cảnh báo quá hạn SLA.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintLifecycle {

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private final ComplaintRepository complaints;
    private final ComplaintMessageRepository messages;
    private final ComplaintEvidenceRepository evidence;
    private final OrderRepository orders;
    private final ComplaintSettlement settlement;
    private final ComplaintDeadlines deadlines;
    private final ComplaintNotifier notifier;
    private final PrivateFileStorage storage;
    private final AuditOutboxService audit;
    private final ApplicationEventPublisher events;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * Tạo hồ sơ cho người gửi hoặc người giao của đơn, đóng băng ký quỹ và chuyển đơn sang DISPUTED.
     * Thời hạn khiếu nại, mô tả và số bằng chứng tối thiểu (BR-R-076) do UC-50 kiểm tra trước khi gọi.
     */
    @Transactional
    public Complaint open(OpenComplaintCommand command) {
        Order order = orders.findByIdForUpdate(command.orderId())
                .orElseThrow(() -> new AppException(ComplaintErrorCode.ORDER_INVALID));
        if (order.getCourierId() == null) throw new AppException(ComplaintErrorCode.ORDER_INVALID);
        ComplaintRole role = command.openedBy().equals(order.getSenderId()) ? ComplaintRole.SENDER
                : command.openedBy().equals(order.getCourierId()) ? ComplaintRole.COURIER : null;
        if (role == null) throw new AppException(ComplaintErrorCode.FORBIDDEN);
        if (complaints.existsByOrderIdAndOpenedByAndStatusIn(order.getId(), command.openedBy(), ComplaintStatus.UNSETTLED)) {
            throw new AppException(ComplaintErrorCode.ALREADY_OPEN);
        }
        Instant now = clock.instant();
        Complaint complaint = complaints.saveAndFlush(Complaint.builder()
                .publicId(UUID.randomUUID())
                .code("KN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .orderId(order.getId())
                .openedBy(command.openedBy())
                .openerRole(role)
                .againstUserId(role == ComplaintRole.SENDER ? order.getCourierId() : order.getSenderId())
                .reason(command.reason())
                .description(blankToNull(command.description()))
                .status(ComplaintStatus.OPEN)
                .firstResponseDueAt(deadlines.firstResponseDue(now))
                .resolveDueAt(deadlines.resolveDue(now))
                .createdAt(now)
                .updatedAt(now)
                .build());
        ComplaintMessage opening = message(complaint, command.openedBy(), role,
                Objects.requireNonNullElse(blankToNull(command.description()), "Mở khiếu nại."), now);
        attach(complaint, opening, command.openedBy(), command.evidence(), now);
        settlement.freeze(order.getId());
        order.setStatus(OrderStatus.DISPUTED);

        notifier.staff(complaint, "MODERATOR", null, "opened", "Khiếu nại mới " + complaint.getCode(),
                "Đơn " + order.getOrderCode() + " có khiếu nại mới, cần phản hồi đầu tiên trước hạn.");
        notifier.parties(complaint, List.of(complaint.getAgainstUserId()), "opened", "Đơn của bạn có khiếu nại",
                "Đơn " + order.getOrderCode() + " đang được khiếu nại. Tiền ký quỹ tạm đóng băng đến khi có phán quyết.");
        auditIfActor("COMPLAINT_OPENED", complaint, Map.of(), Map.of("status", complaint.getStatus().name(),
                "orderId", order.getId(), "reason", complaint.getReason().name()));
        return complaint;
    }

    /** Đóng hồ sơ: thực hiện bút toán, chuyển đơn sang COMPLETED và phát {@link ComplaintClosedEvent}. */
    public void close(Complaint complaint, Instant now) {
        Order order = orders.findByIdForUpdate(complaint.getOrderId())
                .orElseThrow(() -> new AppException(ComplaintErrorCode.ORDER_INVALID));
        ComplaintSettlement.Result result = settlement.settle(complaint, order.getSenderId(), order.getCourierId(), now);
        complaint.setClawbackAmount(result.clawedBack());
        complaint.setUnrecoveredAmount(result.unrecovered());
        complaint.setStatus(ComplaintStatus.CLOSED);
        complaint.setClosedAt(now);
        complaint.setUpdatedAt(now);
        order.setStatus(OrderStatus.COMPLETED);
        if (order.getEndedAt() == null) order.setEndedAt(now);
        complaints.saveAndFlush(complaint);

        message(complaint, null, ComplaintRole.SYSTEM, settlementText(complaint, result), now);
        notifier.parties(complaint, List.of(order.getSenderId(), order.getCourierId()), "closed",
                "Khiếu nại " + complaint.getCode() + " đã kết thúc", settlementText(complaint, result));
        if (result.unrecovered() > 0) {
            notifier.staff(complaint, "ADMIN", null, "unrecovered", "Khoản hoàn chưa thu hồi đủ",
                    "Khiếu nại " + complaint.getCode() + " còn " + money(result.unrecovered())
                            + " chưa thu hồi được từ ví người giao.");
        }
        events.publishEvent(new ComplaintClosedEvent(complaint.getId(), complaint.getPublicId(), order.getId(),
                order.getSenderId(), order.getCourierId(), complaint.getOutcome(),
                complaint.getCompensationAmount() == null ? 0 : complaint.getCompensationAmount(), complaint.getRuledAt(), now));
    }

    /** Đóng các hồ sơ đã có phán quyết mà hết thời hạn kháng nghị; mỗi hồ sơ một giao dịch riêng. */
    public int closeExpiredAppealWindows() {
        Instant now = clock.instant();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        int closed = 0;
        for (Long id : complaints.appealWindowEnded(ComplaintStatus.RESOLVED, now)) {
            try {
                Boolean done = transaction.execute(status -> complaints.findByIdForUpdate(id)
                        .filter(item -> item.getStatus() == ComplaintStatus.RESOLVED && !item.getAppealDeadline().isAfter(now))
                        .map(item -> { close(item, now); return true; }).orElse(false));
                if (Boolean.TRUE.equals(done)) closed++;
            } catch (RuntimeException exception) {
                log.warn("Closing complaint {} failed; the next run will retry", id, exception);
            }
        }
        return closed;
    }

    /** Cảnh báo một lần khi quá hạn phản hồi đầu tiên hoặc quá hạn xử lý (BR-R-078). */
    public int alertOverdue() {
        Instant now = clock.instant();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        int alerts = 0;
        for (Long id : complaints.responseOverdue(ComplaintStatus.ACTIVE, now)) {
            alerts += alert(transaction, id, now, true);
        }
        for (Long id : complaints.resolutionOverdue(ComplaintStatus.ACTIVE, now)) {
            alerts += alert(transaction, id, now, false);
        }
        return alerts;
    }

    private int alert(TransactionTemplate transaction, Long id, Instant now, boolean response) {
        try {
            Boolean sent = transaction.execute(status -> complaints.findByIdForUpdate(id).filter(item ->
                    ComplaintStatus.ACTIVE.contains(item.getStatus())
                            && (response ? item.getFirstResponseAt() == null && item.getResponseAlertedAt() == null
                            : item.getResolveAlertedAt() == null)).map(item -> {
                String title = (response ? "Quá hạn phản hồi khiếu nại " : "Quá hạn xử lý khiếu nại ") + item.getCode();
                String body = response ? "Khiếu nại chưa có phản hồi đầu tiên của kiểm duyệt viên trong thời hạn quy định."
                        : "Khiếu nại chưa có phán quyết trong thời hạn xử lý quy định.";
                String step = response ? "response-overdue" : "resolve-overdue";
                if (item.getAssignedTo() != null) notifier.person(item.getAssignedTo(), item, step, title, body);
                else notifier.staff(item, "MODERATOR", null, step, title, body);
                notifier.staff(item, "ADMIN", null, step, title, body);
                if (response) item.setResponseAlertedAt(now);
                else item.setResolveAlertedAt(now);
                return true;
            }).orElse(false));
            return Boolean.TRUE.equals(sent) ? 1 : 0;
        } catch (RuntimeException exception) {
            log.warn("Complaint {} overdue alert failed; the next run will retry", id, exception);
            return 0;
        }
    }

    public ComplaintMessage message(Complaint complaint, Long authorId, ComplaintRole role, String body, Instant now) {
        return messages.saveAndFlush(ComplaintMessage.builder().publicId(UUID.randomUUID()).complaintId(complaint.getId())
                .authorId(authorId).authorRole(role).body(body).createdAt(now).build());
    }

    public void attach(Complaint complaint, ComplaintMessage message, Long authorId, List<ValidatedImage> images, Instant now) {
        if (images == null) return;
        for (ValidatedImage image : images) {
            evidence.save(ComplaintEvidence.builder().publicId(UUID.randomUUID()).complaintId(complaint.getId())
                    .messageId(message.getId()).authorId(authorId).storageKey(storage.put(image.bytes()))
                    .mediaType(image.mediaType()).createdAt(now).build());
        }
    }

    public void auditIfActor(String action, Complaint complaint, Map<String, Object> before, Map<String, Object> after) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) return;
        audit.enqueue(action, "COMPLAINT", complaint.getPublicId().toString(), before, after);
    }

    static String outcomeText(Complaint complaint) {
        ComplaintOutcome outcome = complaint.getOutcome();
        if (outcome == null) return "Chưa có phán quyết";
        return switch (outcome) {
            case SENDER_FAVOR -> "Hoàn tiền cho người gửi";
            case COURIER_FAVOR -> "Trả tiền cho người giao";
            case SPLIT -> "Chia ký quỹ: người gửi " + complaint.getSenderPercent() + "%";
        };
    }

    static String money(long amount) {
        return NumberFormat.getIntegerInstance(VIETNAMESE).format(amount) + " ₫";
    }

    private static String settlementText(Complaint complaint, ComplaintSettlement.Result result) {
        StringBuilder text = new StringBuilder("Phán quyết cuối cùng: ").append(outcomeText(complaint)).append('.');
        if (result.refunded() > 0) text.append(" Hoàn người gửi ").append(money(result.refunded())).append('.');
        if (result.courierPaid() > 0) text.append(" Trả người giao ").append(money(result.courierPaid())).append('.');
        if (result.clawedBack() > 0) text.append(" Thu hồi từ người giao ").append(money(result.clawedBack())).append('.');
        if (result.unrecovered() > 0) text.append(" Còn ").append(money(result.unrecovered())).append(" chờ Admin xử lý.");
        long compensation = complaint.getCompensationAmount() == null ? 0 : complaint.getCompensationAmount();
        if (compensation > 0) text.append(" Đề nghị bồi thường từ Quỹ bảo đảm ").append(money(compensation)).append(" chờ Admin duyệt.");
        return text.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

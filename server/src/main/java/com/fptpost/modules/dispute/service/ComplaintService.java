package com.fptpost.modules.dispute.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.ImageValidator;
import com.fptpost.common.file.service.PrivateFileStorage;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.dispute.dto.request.*;
import com.fptpost.modules.dispute.dto.response.*;
import com.fptpost.modules.dispute.enums.*;
import com.fptpost.modules.dispute.exception.ComplaintErrorCode;
import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.dispute.model.ComplaintEvidence;
import com.fptpost.modules.dispute.model.ComplaintMessage;
import com.fptpost.modules.dispute.repository.*;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.Escrow;
import com.fptpost.modules.payment.model.EscrowStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** UC-51: kiểm duyệt viên tiếp nhận, trao đổi ba bên, phán quyết; các bên kháng nghị một lần và Admin xem xét lại. */
@Service
@RequiredArgsConstructor
public class ComplaintService {

    private static final int MAX_IMAGES = 3;
    private static final Set<ComplaintStatus> RULING_STATES = EnumSet.of(ComplaintStatus.INVESTIGATING, ComplaintStatus.AWAITING_INFO);

    private final ComplaintRepository complaints;
    private final ComplaintMessageRepository messages;
    private final ComplaintEvidenceRepository evidence;
    private final ComplaintDirectory directory;
    private final ComplaintLifecycle lifecycle;
    private final ComplaintSettlement settlement;
    private final ComplaintDeadlines deadlines;
    private final ComplaintNotifier notifier;
    private final ComplaintChatReader chat;
    private final OrderRepository orders;
    private final IdentityUserAccess userAccess;
    private final ImageValidator imageValidator;
    private final PrivateFileStorage storage;
    private final Clock clock;

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ComplaintSummaryResponse> mine(UUID actorId, int page, int size) {
        User actor = userAccess.read(actorId);
        Page<Complaint> result = complaints.involving(actor.getId(), pageRequest(page, size, 50));
        return summaries(result, false);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public PageResponse<ComplaintSummaryResponse> queue(UUID actorId, String filter, boolean onlyMine, int page, int size) {
        User actor = userAccess.read(actorId);
        ComplaintQueueFilter group;
        try {
            group = filter == null || filter.isBlank() ? ComplaintQueueFilter.ACTIVE
                    : ComplaintQueueFilter.valueOf(filter.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AppException(ComplaintErrorCode.FILTER_INVALID);
        }
        Page<Complaint> result = complaints.queue(group.statuses(), onlyMine ? actor.getId() : null, pageRequest(page, size, 100));
        return summaries(result, true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ComplaintDetailResponse detail(UUID actorId, UUID complaintId) {
        User actor = userAccess.read(actorId);
        Complaint complaint = complaints.findByPublicId(complaintId).orElseThrow(this::notFound);
        Order order = order(complaint);
        return view(complaint, order, viewer(actor, order));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ValidatedImage evidence(UUID actorId, UUID complaintId, UUID evidenceId) {
        User actor = userAccess.read(actorId);
        Complaint complaint = complaints.findByPublicId(complaintId).orElseThrow(this::notFound);
        viewer(actor, order(complaint));
        ComplaintEvidence item = evidence.findByPublicIdAndComplaintId(evidenceId, complaint.getId()).orElseThrow(this::notFound);
        return new ValidatedImage(storage.read(item.getStorageKey()), item.getMediaType());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ComplaintChatHistory chat(UUID actorId, UUID complaintId) {
        User actor = userAccess.read(actorId);
        Complaint complaint = complaints.findByPublicId(complaintId).orElseThrow(this::notFound);
        Order order = order(complaint);
        if (!viewer(actor, order).staff()) throw new AppException(ComplaintErrorCode.PARTY_CONFLICT);
        return chat.read(order);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ComplaintDetailResponse postMessage(UUID actorId, UUID complaintId, ComplaintMessageRequest request, List<MultipartFile> files) {
        User actor = userAccess.read(actorId);
        String body = request == null || request.getBody() == null ? "" : request.getBody().strip();
        List<MultipartFile> attachments = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if ((body.isEmpty() && attachments.isEmpty()) || attachments.size() > MAX_IMAGES) {
            throw new AppException(ComplaintErrorCode.MESSAGE_INVALID);
        }
        List<ValidatedImage> images = attachments.stream().map(imageValidator::validate).toList();
        Complaint complaint = locked(complaintId);
        Order order = order(complaint);
        Viewer viewer = viewer(actor, order);
        if (complaint.getStatus() == ComplaintStatus.CLOSED) throw new AppException(ComplaintErrorCode.STATE_INVALID);
        if (viewer.staff() && !canStaffMessage(complaint, viewer)) throw new AppException(ComplaintErrorCode.NOT_ASSIGNED);

        Instant now = clock.instant();
        ComplaintMessage message = lifecycle.message(complaint, actor.getId(), viewer.role(), body.isEmpty() ? null : body, now);
        lifecycle.attach(complaint, message, actor.getId(), images, now);
        if (viewer.staff() && complaint.getFirstResponseAt() == null) complaint.setFirstResponseAt(now);
        if (!viewer.staff() && complaint.getStatus() == ComplaintStatus.AWAITING_INFO) complaint.setStatus(ComplaintStatus.INVESTIGATING);
        complaint.setUpdatedAt(now);
        complaints.saveAndFlush(complaint);

        String step = "message:" + message.getPublicId();
        String title = "Tin mới trong khiếu nại " + complaint.getCode();
        String preview = body.isEmpty() ? "Có bằng chứng mới được gửi kèm." : limit(body, 300);
        notifier.parties(complaint, Stream.of(order.getSenderId(), order.getCourierId()).filter(Objects::nonNull)
                .filter(id -> !id.equals(actor.getId())).toList(), step, title, preview);
        if (!viewer.staff() && complaint.getAssignedTo() != null) notifier.person(complaint.getAssignedTo(), complaint, step, title, preview);
        return view(complaint, order, viewer);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ComplaintDetailResponse claim(UUID actorId, UUID complaintId) {
        User actor = userAccess.read(actorId);
        Complaint complaint = locked(complaintId);
        Order order = order(complaint);
        Viewer viewer = staff(actor, order);
        if (!canClaim(complaint, viewer)) throw new AppException(ComplaintErrorCode.STATE_INVALID);
        Instant now = clock.instant();
        Long previous = complaint.getAssignedTo();
        complaint.setAssignedTo(actor.getId());
        if (complaint.getStatus() == ComplaintStatus.OPEN) complaint.setStatus(ComplaintStatus.INVESTIGATING);
        if (complaint.getFirstResponseAt() == null) complaint.setFirstResponseAt(now);
        complaint.setUpdatedAt(now);
        complaints.saveAndFlush(complaint);
        lifecycle.message(complaint, null, ComplaintRole.SYSTEM, previous == null
                ? "Kiểm duyệt viên đã tiếp nhận khiếu nại." : "Quản trị viên đã tiếp quản khiếu nại.", now);
        if (previous == null) {
            notifier.parties(complaint, List.of(order.getSenderId(), order.getCourierId()), "claimed",
                    "Khiếu nại " + complaint.getCode() + " đã được tiếp nhận",
                    "Kiểm duyệt viên đang xem xét. Bạn có thể bổ sung bằng chứng trong hồ sơ khiếu nại.");
        }
        return view(complaint, order, viewer);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ComplaintDetailResponse requestInfo(UUID actorId, UUID complaintId, ComplaintInfoRequest request) {
        User actor = userAccess.read(actorId);
        Complaint complaint = locked(complaintId);
        Order order = order(complaint);
        Viewer viewer = staff(actor, order);
        requireAssignee(complaint, actor);
        if (complaint.getStatus() != ComplaintStatus.INVESTIGATING) throw new AppException(ComplaintErrorCode.STATE_INVALID);
        Instant now = clock.instant();
        ComplaintMessage message = lifecycle.message(complaint, actor.getId(), viewer.role(), request.getMessage().strip(), now);
        complaint.setStatus(ComplaintStatus.AWAITING_INFO);
        complaint.setUpdatedAt(now);
        complaints.saveAndFlush(complaint);
        notifier.parties(complaint, List.of(order.getSenderId(), order.getCourierId()), "info:" + message.getPublicId(),
                "Cần bổ sung bằng chứng cho khiếu nại " + complaint.getCode(), limit(request.getMessage().strip(), 300));
        return view(complaint, order, viewer);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ComplaintDetailResponse rule(UUID actorId, UUID complaintId, ComplaintRulingRequest request) {
        User actor = userAccess.read(actorId);
        Complaint complaint = locked(complaintId);
        Order order = order(complaint);
        Viewer viewer = staff(actor, order);
        requireAssignee(complaint, actor);
        if (!RULING_STATES.contains(complaint.getStatus())) throw new AppException(ComplaintErrorCode.STATE_INVALID);
        Instant now = clock.instant();
        Map<String, Object> before = Map.of("status", complaint.getStatus().name());
        applyRuling(complaint, order, request.getOutcome(), request.getSenderPercent(), request.getCompensationAmount());
        complaint.setRulingReason(request.getReason().strip());
        complaint.setRuledBy(actor.getId());
        complaint.setRuledAt(now);
        complaint.setAppealDeadline(deadlines.appealDeadline(now));
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setUpdatedAt(now);
        complaints.saveAndFlush(complaint);
        lifecycle.message(complaint, null, ComplaintRole.SYSTEM, rulingText(complaint), now);
        notifier.parties(complaint, List.of(order.getSenderId(), order.getCourierId()), "ruled",
                "Khiếu nại " + complaint.getCode() + " đã có phán quyết",
                rulingText(complaint) + " Nếu chưa đồng ý, bạn có thể kháng nghị một lần trước hạn.");
        lifecycle.auditIfActor("COMPLAINT_RULED", complaint, before, rulingAudit(complaint));
        return view(complaint, order, viewer);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ComplaintDetailResponse appeal(UUID actorId, UUID complaintId, ComplaintAppealRequest request) {
        User actor = userAccess.read(actorId);
        Complaint complaint = locked(complaintId);
        Order order = order(complaint);
        Viewer viewer = viewer(actor, order);
        if (viewer.staff()) throw new AppException(ComplaintErrorCode.FORBIDDEN);
        Instant now = clock.instant();
        if (!canAppeal(complaint, now)) throw new AppException(ComplaintErrorCode.APPEAL_CLOSED);
        complaint.setStatus(ComplaintStatus.APPEALED);
        complaint.setAppealCount(complaint.getAppealCount() + 1);
        complaint.setAppealedBy(actor.getId());
        complaint.setAppealReason(request.getReason().strip());
        complaint.setAppealedAt(now);
        complaint.setUpdatedAt(now);
        complaints.saveAndFlush(complaint);
        lifecycle.message(complaint, actor.getId(), viewer.role(), "Kháng nghị phán quyết: " + request.getReason().strip(), now);
        notifier.staff(complaint, "ADMIN", null, "appealed", "Kháng nghị khiếu nại " + complaint.getCode(),
                "Một bên kháng nghị phán quyết, cần Admin xem xét lại.");
        notifier.parties(complaint, Stream.of(order.getSenderId(), order.getCourierId()).filter(Objects::nonNull)
                        .filter(id -> !id.equals(actor.getId())).toList(), "appealed",
                "Phán quyết khiếu nại " + complaint.getCode() + " bị kháng nghị", "Admin sẽ xem xét lại trước khi xử lý tiền.");
        lifecycle.auditIfActor("COMPLAINT_APPEALED", complaint, Map.of("status", ComplaintStatus.RESOLVED.name()),
                Map.of("status", complaint.getStatus().name()));
        return view(complaint, order, viewer);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ComplaintDetailResponse reviewAppeal(UUID actorId, UUID complaintId, AppealReviewRequest request) {
        User actor = userAccess.read(actorId);
        Complaint complaint = locked(complaintId);
        Order order = order(complaint);
        Viewer viewer = staff(actor, order);
        if (complaint.getStatus() != ComplaintStatus.APPEALED) throw new AppException(ComplaintErrorCode.STATE_INVALID);
        if (actor.getId().equals(complaint.getRuledBy())) throw new AppException(ComplaintErrorCode.REVIEWER_CONFLICT);
        Instant now = clock.instant();
        Map<String, Object> before = rulingAudit(complaint);
        if (request.getDecision() == AppealDecision.AMEND) {
            if (request.getOutcome() == null) throw new AppException(ComplaintErrorCode.RULING_INVALID);
            applyRuling(complaint, order, request.getOutcome(), request.getSenderPercent(), request.getCompensationAmount());
        }
        complaint.setAppealDecision(request.getDecision());
        complaint.setAppealNote(request.getNote().strip());
        complaint.setAppealReviewedBy(actor.getId());
        complaint.setAppealReviewedAt(now);
        lifecycle.message(complaint, null, ComplaintRole.SYSTEM, (request.getDecision() == AppealDecision.UPHOLD
                ? "Admin giữ nguyên phán quyết. " : "Admin sửa phán quyết: " + rulingText(complaint) + " ")
                + "Ghi chú: " + request.getNote().strip(), now);
        lifecycle.close(complaint, now);
        lifecycle.auditIfActor("COMPLAINT_APPEAL_REVIEWED", complaint, before, rulingAudit(complaint));
        return view(complaint, order, viewer);
    }

    private void applyRuling(Complaint complaint, Order order, ComplaintOutcome outcome, Integer senderPercent, Long compensation) {
        if (outcome == ComplaintOutcome.SPLIT && senderPercent == null) throw new AppException(ComplaintErrorCode.RULING_INVALID);
        Escrow escrow = settlement.escrow(order.getId()).orElse(null);
        long amount = escrow != null && (escrow.getStatus() == EscrowStatus.HELD || escrow.getStatus() == EscrowStatus.RELEASED)
                ? settlement.escrowAmount(escrow) : 0;
        long refund = switch (outcome) {
            case SENDER_FAVOR -> amount;
            case COURIER_FAVOR -> 0;
            case SPLIT -> amount * senderPercent / 100;
        };
        long requested = compensation == null ? 0 : compensation;
        if (requested > 0) {
            BigDecimal declared = order.getDeclaredValue() == null ? BigDecimal.ZERO : order.getDeclaredValue();
            if (outcome == ComplaintOutcome.COURIER_FAVOR || declared.signum() <= 0
                    || BigDecimal.valueOf(requested).compareTo(declared) > 0) {
                throw new AppException(ComplaintErrorCode.COMPENSATION_NOT_ALLOWED);
            }
        }
        complaint.setOutcome(outcome);
        complaint.setSenderPercent(outcome == ComplaintOutcome.SPLIT ? senderPercent : null);
        complaint.setRefundAmount(refund);
        complaint.setCourierAmount(escrow != null && escrow.getStatus() == EscrowStatus.HELD ? amount - refund : 0);
        complaint.setCompensationAmount(requested);
    }

    private PageResponse<ComplaintSummaryResponse> summaries(Page<Complaint> result, boolean staff) {
        Map<Long, Order> byOrder = orders.findAllById(result.getContent().stream().map(Complaint::getOrderId).distinct().toList())
                .stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, ComplaintPerson> people = people(result.getContent().stream()
                .flatMap(item -> Stream.of(item.getOpenedBy(), item.getAssignedTo())).toList(), staff);
        Instant now = clock.instant();
        return PageResponse.from(result.map(item -> {
            Order order = byOrder.get(item.getOrderId());
            return new ComplaintSummaryResponse(item.getPublicId(), item.getCode(), item.getStatus(), item.getReason(),
                    order == null ? null : order.getOrderCode(), order == null ? null : order.getProductName(),
                    item.getOpenerRole(), people.get(item.getOpenedBy()),
                    item.getAssignedTo() == null ? null : people.get(item.getAssignedTo()), item.getOutcome(),
                    item.getCreatedAt(), item.getFirstResponseDueAt(), item.getResolveDueAt(),
                    responseOverdue(item, now), resolutionOverdue(item, now), item.getAppealDeadline());
        }));
    }

    private ComplaintDetailResponse view(Complaint complaint, Order order, Viewer viewer) {
        Instant now = clock.instant();
        List<ComplaintMessage> thread = messages.findByComplaintIdOrderByCreatedAtAscIdAsc(complaint.getId());
        Map<Long, List<UUID>> files = evidence.findByComplaintIdOrderByIdAsc(complaint.getId()).stream()
                .filter(item -> item.getMessageId() != null)
                .collect(Collectors.groupingBy(ComplaintEvidence::getMessageId, Collectors.mapping(ComplaintEvidence::getPublicId, Collectors.toList())));
        Map<Long, ComplaintPerson> people = people(Stream.concat(thread.stream().map(ComplaintMessage::getAuthorId),
                Stream.of(complaint.getOpenedBy(), order.getSenderId(), order.getCourierId(), complaint.getAssignedTo(),
                        complaint.getRuledBy(), complaint.getAppealedBy(), complaint.getAppealReviewedBy())).toList(), viewer.staff());
        Escrow escrow = settlement.escrow(order.getId()).orElse(null);
        BigDecimal declared = order.getDeclaredValue();

        return ComplaintDetailResponse.builder()
                .id(complaint.getPublicId())
                .code(complaint.getCode())
                .status(complaint.getStatus())
                .reason(complaint.getReason())
                .description(complaint.getDescription())
                .openerRole(complaint.getOpenerRole())
                .openedBy(people.get(complaint.getOpenedBy()))
                .sender(people.get(order.getSenderId()))
                .courier(order.getCourierId() == null ? null : people.get(order.getCourierId()))
                .assignee(complaint.getAssignedTo() == null ? null : people.get(complaint.getAssignedTo()))
                .order(new ComplaintDetailResponse.OrderView(order.getId(), order.getOrderCode(), order.getProductName(),
                        order.getStatus() == null ? null : order.getStatus().name(), declared, order.getPriceAgreed(),
                        declared != null && declared.signum() > 0))
                .escrow(escrow == null ? null : new ComplaintDetailResponse.EscrowView(settlement.escrowAmount(escrow),
                        escrow.getStatus(), escrow.isFrozen(), escrow.getPaymentMethod()))
                .createdAt(complaint.getCreatedAt())
                .updatedAt(complaint.getUpdatedAt())
                .firstResponseAt(complaint.getFirstResponseAt())
                .firstResponseDueAt(complaint.getFirstResponseDueAt())
                .resolveDueAt(complaint.getResolveDueAt())
                .responseOverdue(responseOverdue(complaint, now))
                .resolutionOverdue(resolutionOverdue(complaint, now))
                .ruling(complaint.getOutcome() == null ? null : new ComplaintDetailResponse.RulingView(complaint.getOutcome(),
                        complaint.getSenderPercent(), value(complaint.getRefundAmount()), value(complaint.getCourierAmount()),
                        value(complaint.getCompensationAmount()), complaint.getRulingReason(),
                        complaint.getRuledBy() == null ? null : people.get(complaint.getRuledBy()), complaint.getRuledAt(),
                        complaint.getAppealDeadline()))
                .appeal(complaint.getAppealedAt() == null ? null : new ComplaintDetailResponse.AppealView(
                        people.get(complaint.getAppealedBy()), complaint.getAppealReason(), complaint.getAppealedAt(),
                        complaint.getAppealDecision(), complaint.getAppealNote(),
                        complaint.getAppealReviewedBy() == null ? null : people.get(complaint.getAppealReviewedBy()),
                        complaint.getAppealReviewedAt()))
                .settlement(complaint.getClosedAt() == null ? null : new ComplaintDetailResponse.SettlementView(
                        value(complaint.getClawbackAmount()), value(complaint.getUnrecoveredAmount()), complaint.getClosedAt()))
                .messages(thread.stream().map(message -> new ComplaintDetailResponse.MessageView(message.getPublicId(),
                        message.getAuthorRole(), message.getAuthorId() == null ? null : people.get(message.getAuthorId()),
                        message.getBody(), message.getCreatedAt(), files.getOrDefault(message.getId(), List.of()))).toList())
                .viewerRole(viewer.role())
                .permissions(new ComplaintDetailResponse.Permissions(
                        viewer.staff() && canClaim(complaint, viewer),
                        complaint.getStatus() != ComplaintStatus.CLOSED && (!viewer.staff() || canStaffMessage(complaint, viewer)),
                        viewer.staff() && viewer.userId().equals(complaint.getAssignedTo()) && complaint.getStatus() == ComplaintStatus.INVESTIGATING,
                        viewer.staff() && viewer.userId().equals(complaint.getAssignedTo()) && RULING_STATES.contains(complaint.getStatus()),
                        !viewer.staff() && canAppeal(complaint, now),
                        viewer.role() == ComplaintRole.ADMIN && complaint.getStatus() == ComplaintStatus.APPEALED
                                && !viewer.userId().equals(complaint.getRuledBy())))
                .build();
    }

    private boolean canClaim(Complaint complaint, Viewer viewer) {
        if (complaint.getStatus() == ComplaintStatus.OPEN) return true;
        return viewer.role() == ComplaintRole.ADMIN && RULING_STATES.contains(complaint.getStatus())
                && !viewer.userId().equals(complaint.getAssignedTo());
    }

    private boolean canStaffMessage(Complaint complaint, Viewer viewer) {
        return viewer.userId().equals(complaint.getAssignedTo()) || viewer.role() == ComplaintRole.ADMIN;
    }

    private static boolean canAppeal(Complaint complaint, Instant now) {
        return complaint.getStatus() == ComplaintStatus.RESOLVED && complaint.getAppealCount() == 0
                && complaint.getAppealDeadline() != null && now.isBefore(complaint.getAppealDeadline());
    }

    private static boolean responseOverdue(Complaint complaint, Instant now) {
        return ComplaintStatus.ACTIVE.contains(complaint.getStatus()) && complaint.getFirstResponseAt() == null
                && !now.isBefore(complaint.getFirstResponseDueAt());
    }

    private static boolean resolutionOverdue(Complaint complaint, Instant now) {
        return ComplaintStatus.ACTIVE.contains(complaint.getStatus()) && !now.isBefore(complaint.getResolveDueAt());
    }

    private void requireAssignee(Complaint complaint, User actor) {
        if (!actor.getId().equals(complaint.getAssignedTo())) throw new AppException(ComplaintErrorCode.NOT_ASSIGNED);
    }

    /** Bên của đơn luôn xem với vai trò bên đó; nhân sự là một bên của đơn không được xử lý hồ sơ. */
    private Viewer viewer(User actor, Order order) {
        if (actor.getId().equals(order.getSenderId())) return new Viewer(actor.getId(), ComplaintRole.SENDER);
        if (actor.getId().equals(order.getCourierId())) return new Viewer(actor.getId(), ComplaintRole.COURIER);
        Set<String> roles = actor.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        if (roles.contains("ADMIN")) return new Viewer(actor.getId(), ComplaintRole.ADMIN);
        if (roles.contains("MODERATOR")) return new Viewer(actor.getId(), ComplaintRole.MODERATOR);
        throw new AppException(ComplaintErrorCode.NOT_FOUND);
    }

    private Viewer staff(User actor, Order order) {
        Viewer viewer = viewer(actor, order);
        if (!viewer.staff()) throw new AppException(ComplaintErrorCode.PARTY_CONFLICT);
        return viewer;
    }

    private Map<Long, ComplaintPerson> people(Collection<Long> ids, boolean withEmail) {
        Set<Long> distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) return Map.of();
        return directory.people(distinct).stream().map(person -> withEmail ? person : person.withoutEmail())
                .collect(Collectors.toMap(ComplaintPerson::id, Function.identity()));
    }

    private Complaint locked(UUID complaintId) {
        return complaints.findByPublicIdForUpdate(complaintId).orElseThrow(this::notFound);
    }

    private Order order(Complaint complaint) {
        return orders.findById(complaint.getOrderId()).orElseThrow(this::notFound);
    }

    private AppException notFound() {
        return new AppException(ComplaintErrorCode.NOT_FOUND);
    }

    private static String rulingText(Complaint complaint) {
        StringBuilder text = new StringBuilder("Phán quyết: ").append(ComplaintLifecycle.outcomeText(complaint)).append('.');
        if (value(complaint.getRefundAmount()) > 0) {
            text.append(" Hoàn người gửi ").append(ComplaintLifecycle.money(value(complaint.getRefundAmount()))).append('.');
        }
        if (value(complaint.getCourierAmount()) > 0) {
            text.append(" Trả người giao ").append(ComplaintLifecycle.money(value(complaint.getCourierAmount()))).append('.');
        }
        if (value(complaint.getCompensationAmount()) > 0) {
            text.append(" Bồi thường từ Quỹ bảo đảm ").append(ComplaintLifecycle.money(value(complaint.getCompensationAmount()))).append('.');
        }
        if (complaint.getRulingReason() != null) text.append(" Lý do: ").append(complaint.getRulingReason());
        return limit(text.toString(), 1900);
    }

    private static Map<String, Object> rulingAudit(Complaint complaint) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", complaint.getStatus().name());
        if (complaint.getOutcome() != null) values.put("outcome", complaint.getOutcome().name());
        values.put("refundAmount", value(complaint.getRefundAmount()));
        values.put("courierAmount", value(complaint.getCourierAmount()));
        values.put("compensationAmount", value(complaint.getCompensationAmount()));
        return values;
    }

    private static PageRequest pageRequest(int page, int size, int max) {
        return PageRequest.of(Math.max(0, page), Math.min(max, Math.max(1, size)));
    }

    private static long value(Long amount) {
        return amount == null ? 0 : amount;
    }

    private static String limit(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }

    private record Viewer(Long userId, ComplaintRole role) {
        boolean staff() {
            return role == ComplaintRole.ADMIN || role == ComplaintRole.MODERATOR;
        }
    }
}

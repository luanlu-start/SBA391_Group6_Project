package com.fptpost.modules.moderation.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.ImageValidator;
import com.fptpost.common.file.service.PrivateFileStorage;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import com.fptpost.modules.chat.repository.ChatMessageRepository;
import com.fptpost.modules.chat.repository.ChatRoomRepository;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.moderation.dto.request.ViolationReportRequest;
import com.fptpost.modules.moderation.dto.request.ViolationResolveRequest;
import com.fptpost.modules.moderation.dto.response.*;
import com.fptpost.modules.moderation.enums.ViolationAction;
import com.fptpost.modules.moderation.enums.ViolationCaseStatus;
import com.fptpost.modules.moderation.enums.ViolationTargetType;
import com.fptpost.modules.moderation.exception.ViolationReportErrorCode;
import com.fptpost.modules.moderation.model.ViolationCase;
import com.fptpost.modules.moderation.model.ViolationEvidence;
import com.fptpost.modules.moderation.model.ViolationReport;
import com.fptpost.modules.moderation.repository.*;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** UC-55: người dùng báo cáo đơn, người dùng hoặc tin nhắn; kiểm duyệt viên cảnh cáo, đề nghị khóa hoặc bác báo cáo. */
@Service
@RequiredArgsConstructor
public class ViolationReportService {

    private static final int MAX_EVIDENCE = 3;
    private static final int LABEL_LIMIT = 300;
    private static final Set<ViolationAction> SANCTIONS = EnumSet.of(ViolationAction.WARN, ViolationAction.ESCALATE);

    private final ViolationCaseRepository cases;
    private final ViolationReportRepository reports;
    private final ViolationEvidenceRepository evidence;
    private final ViolationPartyRepository parties;
    private final OrderRepository orders;
    private final UserRepository users;
    private final ChatMessageRepository chatMessages;
    private final ChatRoomRepository chatRooms;
    private final IdentityUserAccess userAccess;
    private final ImageValidator imageValidator;
    private final PrivateFileStorage storage;
    private final NotificationPublisher notifications;
    private final AuditOutboxService audit;
    private final Clock clock;

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public MyViolationReportResponse submit(UUID actorPublicId, ViolationReportRequest request, List<MultipartFile> files) {
        User reporter = userAccess.read(actorPublicId);
        List<MultipartFile> attachments = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (attachments.size() > MAX_EVIDENCE) throw new AppException(ViolationReportErrorCode.EVIDENCE_LIMIT);
        List<ValidatedImage> images = attachments.stream().map(imageValidator::validate).toList();
        Target target = resolve(reporter, request.getTargetType(), request.getTargetId().trim());
        Instant now = clock.instant();

        ViolationCase violationCase = cases.findOpenForUpdate(request.getTargetType(), target.id()).stream().findFirst().orElse(null);
        boolean merged = violationCase != null;
        if (violationCase == null) {
            violationCase = ViolationCase.builder().publicId(UUID.randomUUID()).targetType(request.getTargetType())
                    .targetId(target.id()).targetLabel(target.label()).relatedOrderId(target.orderId())
                    .respondentId(target.respondentId()).status(ViolationCaseStatus.OPEN).createdAt(now).build();
        } else {
            Optional<ViolationReport> existing = reports.findByCaseIdAndReporterId(violationCase.getId(), reporter.getId());
            if (existing.isPresent()) return mine(existing.get(), violationCase, violationCase.getReportCount() > 1);
        }
        violationCase.setLatestReason(request.getReason());
        violationCase.setReportCount(violationCase.getReportCount() + 1);
        violationCase.setUpdatedAt(now);
        violationCase = cases.saveAndFlush(violationCase);

        ViolationReport report = reports.saveAndFlush(ViolationReport.builder().caseId(violationCase.getId()).reporterId(reporter.getId())
                .reason(request.getReason()).description(blankToNull(request.getDescription())).createdAt(now).build());
        for (ValidatedImage image : images) {
            evidence.save(ViolationEvidence.builder().publicId(UUID.randomUUID()).reportId(report.getId())
                    .storageKey(storage.put(image.bytes())).mediaType(image.mediaType()).createdAt(now).build());
        }
        return mine(report, violationCase, merged);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponse<MyViolationReportResponse> mine(UUID actorPublicId, int page, int size) {
        User reporter = userAccess.read(actorPublicId);
        Page<ViolationReport> result = reports.findByReporterIdOrderByCreatedAtDescIdDesc(reporter.getId(),
                PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size))));
        Map<Long, ViolationCase> byId = cases.findByIdIn(result.getContent().stream().map(ViolationReport::getCaseId).toList())
                .stream().collect(Collectors.toMap(ViolationCase::getId, Function.identity()));
        return PageResponse.from(result.map(report -> mine(report, byId.get(report.getCaseId()), byId.get(report.getCaseId()).getReportCount() > 1)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public PageResponse<ViolationCaseResponse> queue(UUID actorPublicId, String status, String targetType, int page, int size) {
        User moderator = userAccess.read(actorPublicId);
        ViolationCaseStatus caseStatus = parse(ViolationCaseStatus.class, status, ViolationCaseStatus.OPEN);
        ViolationTargetType type = parse(ViolationTargetType.class, targetType, null);
        if (caseStatus == null) throw new AppException(ViolationReportErrorCode.FILTER_INVALID);
        Page<ViolationCase> result = cases.queue(caseStatus, type, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        Map<Long, ViolationParty> people = people(result.getContent().stream()
                .flatMap(item -> Stream.of(item.getRespondentId(), item.getHandledBy())).filter(Objects::nonNull).toList());
        return PageResponse.from(result.map(item -> response(item, moderator, people, null)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ViolationCaseResponse detail(UUID actorPublicId, UUID caseId) {
        User moderator = userAccess.read(actorPublicId);
        ViolationCase violationCase = cases.findByPublicId(caseId)
                .orElseThrow(() -> new AppException(ViolationReportErrorCode.CASE_NOT_FOUND));
        return detail(violationCase, moderator);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ValidatedImage evidence(UUID caseId, UUID evidenceId) {
        ViolationCase violationCase = cases.findByPublicId(caseId)
                .orElseThrow(() -> new AppException(ViolationReportErrorCode.CASE_NOT_FOUND));
        ViolationEvidence item = evidence.findByPublicId(evidenceId)
                .orElseThrow(() -> new AppException(ViolationReportErrorCode.CASE_NOT_FOUND));
        ViolationReport report = reports.findById(item.getReportId()).orElse(null);
        if (report == null || !report.getCaseId().equals(violationCase.getId())) {
            throw new AppException(ViolationReportErrorCode.CASE_NOT_FOUND);
        }
        return new ValidatedImage(storage.read(item.getStorageKey()), item.getMediaType());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public ViolationCaseResponse resolve(UUID actorPublicId, UUID caseId, ViolationResolveRequest input) {
        User moderator = userAccess.read(actorPublicId);
        ViolationCase violationCase = cases.findByPublicIdForUpdate(caseId)
                .orElseThrow(() -> new AppException(ViolationReportErrorCode.CASE_NOT_FOUND));
        if (violationCase.getStatus() != ViolationCaseStatus.OPEN) throw new AppException(ViolationReportErrorCode.CASE_CLOSED);
        if (moderator.getId().equals(violationCase.getRespondentId())) {
            throw new AppException(ViolationReportErrorCode.RESPONDENT_FORBIDDEN);
        }
        Instant now = clock.instant();
        String note = input.getNote().strip();
        Map<String, Object> before = Map.of("status", violationCase.getStatus().name());
        violationCase.setStatus(input.getAction() == ViolationAction.DISMISS ? ViolationCaseStatus.DISMISSED : ViolationCaseStatus.RESOLVED);
        violationCase.setResolution(input.getAction());
        violationCase.setResolutionNote(note);
        violationCase.setHandledBy(moderator.getId());
        violationCase.setHandledAt(now);
        violationCase.setUpdatedAt(now);
        cases.saveAndFlush(violationCase);

        String key = "violation:" + violationCase.getPublicId() + ":";
        UUID respondent = violationCase.getRespondentId() == null ? null
                : users.findById(violationCase.getRespondentId()).filter(user -> !user.isDeleted()).map(User::getPublicId).orElse(null);
        if (SANCTIONS.contains(input.getAction()) && respondent != null) {
            notifications.publish(respondent, NotificationCategory.SECURITY, key + "warning",
                    "Cảnh cáo vi phạm quy tắc cộng đồng",
                    "Nội dung hoặc hành vi của bạn bị báo cáo và xác nhận vi phạm: " + note
                            + " Vi phạm tiếp có thể dẫn đến khóa tài khoản.", null);
        }
        if (input.getAction() == ViolationAction.ESCALATE && respondent != null) {
            for (UUID administrator : parties.findActiveAdministrators()) {
                if (administrator.equals(actorPublicId)) continue;
                notifications.publish(administrator, NotificationCategory.SECURITY, key + "escalated:" + administrator,
                        "Đề nghị khóa tài khoản", "Kiểm duyệt viên đề nghị khóa một tài khoản vi phạm: " + note,
                        "/admin/users/" + respondent);
            }
        }
        for (ViolationReport report : reports.findByCaseIdOrderByCreatedAtAscIdAsc(violationCase.getId())) {
            users.findById(report.getReporterId()).filter(user -> !user.isDeleted() && user.getStatus() != UserStatus.DELETED)
                    .ifPresent(reporter -> notifications.publish(reporter.getPublicId(), NotificationCategory.SECURITY,
                            key + "outcome:" + reporter.getPublicId(), "Báo cáo vi phạm đã được xử lý",
                            input.getAction() == ViolationAction.DISMISS
                                    ? "Kiểm duyệt viên chưa ghi nhận vi phạm trong báo cáo của bạn."
                                    : "Kiểm duyệt viên đã xử lý vi phạm bạn báo cáo. Cảm ơn bạn đã giúp giữ cộng đồng an toàn.",
                            null));
        }
        audit.enqueue("VIOLATION_CASE_RESOLVED", "VIOLATION_CASE", violationCase.getPublicId().toString(), before,
                Map.of("status", violationCase.getStatus().name(), "action", input.getAction().name(), "note", note));
        return detail(violationCase, moderator);
    }

    private Target resolve(User reporter, ViolationTargetType type, String id) {
        return switch (type) {
            case ORDER -> {
                if (!id.matches("\\d{1,18}")) throw new AppException(ViolationReportErrorCode.TARGET_NOT_FOUND);
                Order order = orders.findById(Long.valueOf(id)).orElseThrow(() -> new AppException(ViolationReportErrorCode.TARGET_NOT_FOUND));
                boolean participant = reporter.getId().equals(order.getSenderId()) || reporter.getId().equals(order.getCourierId());
                if (!participant && order.getStatus() != OrderStatus.FINDING_SHIPPER) {
                    throw new AppException(ViolationReportErrorCode.TARGET_FORBIDDEN);
                }
                Long respondent = reporter.getId().equals(order.getSenderId()) ? order.getCourierId() : order.getSenderId();
                yield new Target(id, limit(order.getOrderCode() + " · " + order.getProductName()), order.getId(), respondent);
            }
            case USER -> {
                UUID publicId;
                try {
                    publicId = UUID.fromString(id);
                } catch (IllegalArgumentException exception) {
                    throw new AppException(ViolationReportErrorCode.TARGET_NOT_FOUND);
                }
                User user = users.findByPublicId(publicId).filter(found -> !found.isDeleted())
                        .orElseThrow(() -> new AppException(ViolationReportErrorCode.TARGET_NOT_FOUND));
                if (user.getId().equals(reporter.getId())) throw new AppException(ViolationReportErrorCode.SELF_REPORT);
                yield new Target(publicId.toString(), limit(user.getEmail()), null, user.getId());
            }
            case MESSAGE -> {
                ChatMessage message = chatMessages.findById(id).orElseThrow(() -> new AppException(ViolationReportErrorCode.TARGET_NOT_FOUND));
                ChatRoom room = chatRooms.findById(message.getRoomId())
                        .orElseThrow(() -> new AppException(ViolationReportErrorCode.TARGET_NOT_FOUND));
                boolean participant = reporter.getId().equals(room.getSenderId()) || reporter.getId().equals(room.getCourierId());
                if (!participant) throw new AppException(ViolationReportErrorCode.TARGET_FORBIDDEN);
                if (reporter.getId().equals(message.getAuthorId())) throw new AppException(ViolationReportErrorCode.SELF_REPORT);
                yield new Target(id, limit("Tin nhắn: " + message.getContent()), room.getOrderId(), message.getAuthorId());
            }
        };
    }

    private ViolationCaseResponse detail(ViolationCase violationCase, User moderator) {
        List<ViolationReport> items = reports.findByCaseIdOrderByCreatedAtAscIdAsc(violationCase.getId());
        Map<Long, List<UUID>> files = evidence.findByReportIdIn(items.stream().map(ViolationReport::getId).toList()).stream()
                .collect(Collectors.groupingBy(ViolationEvidence::getReportId, Collectors.mapping(ViolationEvidence::getPublicId, Collectors.toList())));
        Map<Long, ViolationParty> people = people(Stream.concat(items.stream().map(ViolationReport::getReporterId),
                Stream.of(violationCase.getRespondentId(), violationCase.getHandledBy())).filter(Objects::nonNull).toList());
        List<ViolationReportItemResponse> reportItems = items.stream().map(report -> ViolationReportItemResponse.builder()
                .reporter(people.get(report.getReporterId())).reason(report.getReason()).description(report.getDescription())
                .createdAt(report.getCreatedAt()).evidenceIds(files.getOrDefault(report.getId(), List.of())).build()).toList();
        return response(violationCase, moderator, people, reportItems);
    }

    private ViolationCaseResponse response(ViolationCase item, User moderator, Map<Long, ViolationParty> people,
                                           List<ViolationReportItemResponse> reportItems) {
        long prior = item.getRespondentId() == null ? 0 : cases.countByRespondentIdAndResolutionIn(item.getRespondentId(), SANCTIONS)
                - (SANCTIONS.contains(item.getResolution()) ? 1 : 0);
        return ViolationCaseResponse.builder()
                .id(item.getPublicId())
                .targetType(item.getTargetType())
                .targetId(item.getTargetId())
                .targetLabel(item.getTargetLabel())
                .relatedOrderId(item.getRelatedOrderId())
                .respondent(item.getRespondentId() == null ? null : people.get(item.getRespondentId()))
                .respondentPriorViolations(Math.max(0, prior))
                .latestReason(item.getLatestReason())
                .reportCount(item.getReportCount())
                .status(item.getStatus())
                .resolution(item.getResolution())
                .resolutionNote(item.getResolutionNote())
                .handledBy(item.getHandledBy() == null ? null : people.get(item.getHandledBy()))
                .handledAt(item.getHandledAt())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .canResolve(item.getStatus() == ViolationCaseStatus.OPEN && !moderator.getId().equals(item.getRespondentId()))
                .reports(reportItems)
                .build();
    }

    private MyViolationReportResponse mine(ViolationReport report, ViolationCase violationCase, boolean merged) {
        return MyViolationReportResponse.builder().caseId(violationCase.getPublicId()).targetType(violationCase.getTargetType())
                .targetLabel(violationCase.getTargetLabel()).reason(report.getReason()).status(violationCase.getStatus())
                .merged(merged).createdAt(report.getCreatedAt()).build();
    }

    private Map<Long, ViolationParty> people(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return parties.findParties(new HashSet<>(ids)).stream().collect(Collectors.toMap(ViolationParty::id, Function.identity()));
    }

    private static String limit(String value) {
        if (value == null) return null;
        return value.length() <= LABEL_LIMIT ? value : value.substring(0, LABEL_LIMIT - 1) + "…";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        if ("ALL".equalsIgnoreCase(value.trim())) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AppException(ViolationReportErrorCode.FILTER_INVALID);
        }
    }

    private record Target(String id, String label, Long orderId, Long respondentId) {
    }
}

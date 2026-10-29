package com.fptpost.modules.identity.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.PrivateFileStorage;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.identity.dto.request.*;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.exception.KycReviewErrorCode;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.security.KycCrypto;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
public class KycReviewService {
    private final KycRequestRepository requests;
    private final KycReviewSearchRepository search;
    private final KycFileRepository files;
    private final KycIdentityClaimRepository claims;
    private final UserProfileRepository profiles;
    private final AuthSessionRepository sessions;
    private final BlacklistRepository blacklist;
    private final IdentityUserAccess userAccess;
    private final KycReviewAdminDirectory administrators;
    private final KycCrypto crypto;
    private final PrivateFileStorage storage;
    private final AuditOutboxService audit;
    private final NotificationPublisher notifications;
    private final KycReviewSlaPolicy sla;
    private final Clock identityClock;

    public PageResponse<KycReviewResponse> list(KycReviewSearchRequest request) {
        KycStatus status = validateSearch(request);
        long hours = sla.hours(); Instant now = identityClock.instant();
        var page = search.find(request, status, sla.cutoff(now, hours));
        Map<Long, String> names = reviewerNames(page.getContent());
        return PageResponse.from(page.map(item -> response(item, names, hours, now, false)));
    }

    public KycReviewResponse detail(UUID id) { return detailResponse(find(id)); }

    public List<KycRejectionReasonResponse> rejectionReasons() {
        return Arrays.stream(KycRejectionReason.values()).map(reason ->
                new KycRejectionReasonResponse(reason.name(), reason.getLabel(), reason.isNoteRequired())).toList();
    }

    @Transactional
    public KycReviewResponse approve(UUID actor, UUID session, UUID id, KycReviewVersionRequest input) {
        requireVersion(input == null ? null : input.getExpectedVersion());
        var locked = lockApproval(id, actor);
        var request = locked.request();
        User reviewer = reviewer(actor, session);
        requireDecision(request, reviewer, input.getExpectedVersion());
        validateApproval(request, locked.claim());
        var before = state(request);
        request.setStatus(KycStatus.APPROVED); request.setRejectReason(null);
        decide(request, reviewer);
        audit.enqueue("KYC_APPROVED", "KYC_REQUEST", id.toString(), before, state(request));
        notifyApplicant(request, "Hồ sơ xác minh đã được duyệt", "Hồ sơ của bạn đã được duyệt. Bạn có thể tiếp tục đăng ký làm người giao.");
        return detailResponse(request);
    }

    @Transactional
    public KycReviewResponse reject(UUID actor, UUID session, UUID id, KycReviewRejectRequest input) {
        requireVersion(input == null ? null : input.getExpectedVersion());
        String reason = rejection(input);
        var request = lockParticipantsThenRequest(id, actor);
        User reviewer = reviewer(actor, session);
        requireDecision(request, reviewer, input.getExpectedVersion());
        var before = state(request);
        request.setStatus(KycStatus.REJECTED); request.setRejectReason(reason);
        decide(request, reviewer);
        var after = new LinkedHashMap<>(state(request)); after.put("reasonCode", input.getReasonCode().name());
        audit.enqueue("KYC_REJECTED", "KYC_REQUEST", id.toString(), before, after);
        notifyApplicant(request, "Hồ sơ xác minh chưa được duyệt", "Vui lòng xem lý do và hướng dẫn bổ sung trong hồ sơ xác minh.");
        return detailResponse(request);
    }

    @Transactional
    public KycReviewResponse flag(UUID actor, UUID session, UUID id, KycReviewFlagRequest input) {
        requireVersion(input == null ? null : input.getExpectedVersion());
        if (input.getReason() == null || input.getReason().isBlank() || input.getReason().length() > 500) fail(KycReviewErrorCode.INVALID_REASON);
        String reason = input.getReason().strip();
        var applicantId = applicantId(id);
        var recipients = administrators.lockApplicantAndAdministrators(applicantId, actor);
        var request = requests.findByPublicIdForUpdate(id).orElseThrow(() -> new AppException(KycReviewErrorCode.NOT_FOUND));
        User reviewer = reviewer(actor, session);
        requirePending(request, reviewer, input.getExpectedVersion());
        if (request.isFlagged() && reason.equals(request.getFlagReason())) return detailResponse(request);
        var before = state(request);
        request.setFlagged(true); request.setFlagReason(reason);
        requests.saveAndFlush(request);
        audit.enqueue("KYC_FLAGGED", "KYC_REQUEST", id.toString(), before, state(request));
        for (UUID administrator : recipients) {
            notifications.publish(administrator, NotificationCategory.KYC, "kyc:flag:" + id + ":" + request.getVersion(),
                    "Hồ sơ xác minh cần quản trị viên xem xét", "Một hồ sơ đã được đánh dấu cần kiểm tra thêm.", "/mod/kyc?request=" + id);
        }
        return detailResponse(request);
    }

    public SignedFileLinkResponse fileLink(UUID actor, UUID session, UUID id, UUID fileId) {
        reviewer(actor, session);
        find(id); file(id, fileId);
        Instant expiresAt = identityClock.instant().truncatedTo(ChronoUnit.SECONDS).plusSeconds(300);
        String signature = crypto.sign(linkPayload(actor, session, id, fileId, expiresAt.getEpochSecond()));
        return SignedFileLinkResponse.builder().url("/mod/kyc/" + id + "/files/" + fileId + "?expires="
                + expiresAt.getEpochSecond() + "&signature=" + signature).expiresAt(expiresAt).build();
    }

    @Transactional
    public ValidatedImage readFile(UUID actor, UUID session, UUID id, UUID fileId, long expires, String signature) {
        reviewer(actor, session);
        find(id);
        var file = file(id, fileId);
        long now = identityClock.instant().getEpochSecond();
        if (expires <= now || expires > now + 300 || !crypto.verify(linkPayload(actor, session, id, fileId, expires), signature))
            fail(KycReviewErrorCode.FILE_LINK_INVALID);
        byte[] plaintext = crypto.decrypt(storage.read(file.getStorageKey()), "kyc-file:" + fileId + ":" + id);
        if (!checksum(plaintext).equals(file.getChecksum())) throw new AppException(IdentityErrorCode.KYC_ENCRYPTION_UNAVAILABLE);
        audit.enqueue("KYC_FILE_VIEWED", "KYC_REQUEST", id.toString(), Map.of(), Map.of("documentType", file.getType().name()));
        return new ValidatedImage(plaintext, file.getMediaType());
    }

    private KycRequest lockParticipantsThenRequest(UUID id, UUID reviewer) {
        UUID applicantId = applicantId(id);
        administrators.lockApplicantAndReviewer(applicantId, reviewer);
        return requests.findByPublicIdForUpdate(id).orElseThrow(() -> new AppException(KycReviewErrorCode.NOT_FOUND));
    }

    private LockedApproval lockApproval(UUID id, UUID reviewer) {
        UUID applicant = applicantId(id);
        String hash = requests.findIdentityHash(id).orElseThrow(() -> new AppException(KycReviewErrorCode.NOT_FOUND));
        administrators.lockApplicantAndReviewer(applicant, reviewer);
        // Submission checks the request through its reserved claim; take that claim before the request too.
        var claim = claims.lockByHash(hash).orElse(null);
        var request = requests.findByPublicIdForUpdate(id).orElseThrow(() -> new AppException(KycReviewErrorCode.NOT_FOUND));
        if (!hash.equals(request.getIdNumberHash()) || !applicant.equals(request.getUser().getPublicId()))
            fail(KycReviewErrorCode.IDENTITY_CONFLICT);
        return new LockedApproval(request, claim);
    }

    private record LockedApproval(KycRequest request, KycIdentityClaim claim) { }

    private User reviewer(UUID actor, UUID sessionId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)
                || !Objects.equals(actor == null ? null : actor.toString(), jwt.getSubject())
                || !Objects.equals(sessionId == null ? null : sessionId.toString(), jwt.getClaimAsString("sid")))
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        User reviewer = userAccess.read(actor);
        if (reviewer.getRoles().stream().noneMatch(role -> Set.of("ADMIN", "MODERATOR").contains(role.getCode())))
            throw new AppException(GlobalErrorCode.FORBIDDEN);
        var session = sessions.findByPublicIdAndUserId(sessionId, reviewer.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        if (session.getRevokedAt() != null || session.getReplacedBy() != null
                || !identityClock.instant().isBefore(session.getExpiresAt())) throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        return reviewer;
    }

    private void requirePending(KycRequest request, User reviewer, long version) {
        if (reviewer.getPublicId().equals(request.getUser().getPublicId())) fail(KycReviewErrorCode.SELF_FORBIDDEN);
        if (request.getVersion() != version) fail(KycReviewErrorCode.VERSION_CONFLICT);
        if (request.getStatus() != KycStatus.PENDING) fail(KycReviewErrorCode.ALREADY_DECIDED);
    }

    private void requireDecision(KycRequest request, User reviewer, long version) {
        requirePending(request, reviewer, version);
        if (request.isFlagged() && reviewer.getRoles().stream().noneMatch(role -> "ADMIN".equals(role.getCode())))
            fail(KycReviewErrorCode.ADMIN_REQUIRED);
    }

    private void validateApproval(KycRequest request, KycIdentityClaim claim) {
        if (request.getUser().isDeleted() || request.getUser().getStatus() != UserStatus.ACTIVE
                || request.getUser().getEmailVerifiedAt() == null) fail(KycReviewErrorCode.APPLICANT_UNAVAILABLE);
        LocalDate today = LocalDate.ofInstant(identityClock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        if (request.getDob().plusYears(18).isAfter(today) || request.getExpiryDate().isBefore(today)
                || request.getIssuedDate().isAfter(today) || request.getIssuedDate().isBefore(request.getDob())
                || request.getExpiryDate().isBefore(request.getIssuedDate())) fail(KycReviewErrorCode.INVALID_DOCUMENT);
        var profile = profiles.findById(request.getUser().getId()).orElseThrow(() -> new AppException(KycReviewErrorCode.INVALID_DOCUMENT));
        if (!Objects.equals(profile.getDob(), request.getDob()) || !normalized(profile.getFullName()).equals(normalized(request.getFullName())))
            fail(KycReviewErrorCode.INVALID_DOCUMENT);
        var latest = requests.findFirstByUserIdOrderBySubmittedAtDescIdDesc(request.getUser().getId());
        if (latest.isEmpty() || !latest.get().getPublicId().equals(request.getPublicId())) fail(KycReviewErrorCode.IDENTITY_CONFLICT);
        if (claim == null || !request.getIdNumberHash().equals(claim.getIdNumberHash())
                || !claim.getUser().getId().equals(request.getUser().getId()) || !claim.getKyc().getPublicId().equals(request.getPublicId())
                || blacklist.existsByKindAndValueHash("ID_NUMBER", request.getIdNumberHash())) fail(KycReviewErrorCode.IDENTITY_CONFLICT);
        var images = files.findByKycIdOrderByIdAsc(request.getId());
        if (images.size() != 3 || !images.stream().map(KycFile::getType).collect(java.util.stream.Collectors.toSet())
                .equals(EnumSet.allOf(KycFileType.class))) fail(KycReviewErrorCode.INVALID_DOCUMENT);
    }

    private void decide(KycRequest request, User reviewer) {
        request.setReviewer(reviewer); request.setReviewedAt(identityClock.instant().truncatedTo(ChronoUnit.MICROS));
        requests.saveAndFlush(request);
    }

    private void notifyApplicant(KycRequest request, String title, String body) {
        notifications.publish(request.getUser().getPublicId(), NotificationCategory.KYC,
                "kyc:decision:" + request.getPublicId() + ":" + request.getVersion(), title, body, "/me/kyc");
    }

    private String rejection(KycReviewRejectRequest input) {
        if (input.getReasonCode() == null || input.getNote() != null && input.getNote().length() > 400) fail(KycReviewErrorCode.INVALID_REASON);
        String note = input.getNote() == null ? "" : input.getNote().strip();
        if (input.getReasonCode().isNoteRequired() && note.isEmpty()) fail(KycReviewErrorCode.INVALID_REASON);
        String reason = input.getReasonCode().getLabel() + (note.isEmpty() ? "" : ": " + note);
        if (reason.length() > 500) fail(KycReviewErrorCode.INVALID_REASON);
        return reason;
    }

    private KycReviewResponse detailResponse(KycRequest request) {
        return response(request, reviewerNames(List.of(request)), sla.hours(), identityClock.instant(), true);
    }

    private KycReviewResponse response(KycRequest request, Map<Long, String> names, long hours, Instant now, boolean detail) {
        Instant dueAt = sla.dueAt(request.getSubmittedAt(), hours);
        var result = KycReviewResponse.builder().id(request.getPublicId()).applicantId(request.getUser().getPublicId())
                .fullName(request.getFullName()).email(request.getUser().getEmail()).idNumberMasked("********" + request.getIdLastFour())
                .dob(request.getDob()).issuedDate(request.getIssuedDate()).expiryDate(request.getExpiryDate()).status(request.getStatus())
                .submittedAt(request.getSubmittedAt()).reviewedAt(request.getReviewedAt())
                .reviewerName(request.getReviewer() == null ? null : names.get(request.getReviewer().getId()))
                .rejectReason(request.getRejectReason()).flagged(request.isFlagged()).flagReason(request.getFlagReason())
                .version(request.getVersion()).dueAt(dueAt).overdue(request.getStatus() == KycStatus.PENDING && !now.isBefore(dueAt)).build();
        if (detail) result.setFiles(files.findByKycIdOrderByIdAsc(request.getId()).stream().map(file ->
                KycFileResponse.builder().id(file.getPublicId()).type(file.getType()).mediaType(file.getMediaType()).sizeBytes(file.getSizeBytes()).build()).toList());
        return result;
    }

    private Map<Long, String> reviewerNames(List<KycRequest> rows) {
        Map<Long, String> result = new HashMap<>();
        rows.stream().map(KycRequest::getReviewer).filter(Objects::nonNull).forEach(user -> result.put(user.getId(), user.getEmail()));
        profiles.findAllById(result.keySet()).forEach(profile -> result.put(profile.getUserId(), profile.getFullName()));
        return result;
    }

    private KycStatus validateSearch(KycReviewSearchRequest request) {
        if (request == null || request.getPage() < 0 || request.getPage() > 100000 || request.getSize() < 1 || request.getSize() > 100
                || request.getQ() != null && request.getQ().length() > 100) throw new AppException(KycReviewErrorCode.INVALID_FILTER);
        if (request.getStatus() == null || "ALL".equals(request.getStatus())) return null;
        try {
            KycStatus status = KycStatus.valueOf(request.getStatus());
            if (status == KycStatus.NOT_SUBMITTED) throw new IllegalArgumentException();
            return status;
        } catch (IllegalArgumentException exception) { throw new AppException(KycReviewErrorCode.INVALID_FILTER); }
    }

    private Map<String, Object> state(KycRequest request) {
        return Map.of("status", request.getStatus().name(), "flagged", request.isFlagged(), "version", request.getVersion(),
                "reviewerId", request.getReviewer() == null ? "" : request.getReviewer().getPublicId().toString());
    }
    private KycRequest find(UUID id) { return requests.findByPublicId(id).orElseThrow(() -> new AppException(KycReviewErrorCode.NOT_FOUND)); }
    private UUID applicantId(UUID id) { return requests.findApplicantPublicId(id).orElseThrow(() -> new AppException(KycReviewErrorCode.NOT_FOUND)); }
    private KycFile file(UUID id, UUID fileId) { return files.findByPublicIdAndKycPublicId(fileId, id).orElseThrow(() -> new AppException(KycReviewErrorCode.NOT_FOUND)); }
    private void requireVersion(Long version) { if (version == null || version < 0) throw new AppException(GlobalErrorCode.INVALID_REQUEST); }
    private void fail(KycReviewErrorCode code) { throw new AppException(code); }
    private String normalized(String value) { return Normalizer.normalize(value.strip().replaceAll("\\s+", " "), Normalizer.Form.NFC).toLowerCase(Locale.ROOT); }
    private String linkPayload(UUID actor, UUID session, UUID request, UUID file, long expires) { return "review:" + actor + ":" + session + ":" + request + ":" + file + ":" + expires; }
    private String checksum(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}

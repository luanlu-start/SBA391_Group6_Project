package com.fptpost.modules.identity;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.file.service.PrivateFileStorage;
import com.fptpost.modules.identity.dto.request.*;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.security.KycCrypto;
import com.fptpost.modules.identity.service.*;
import com.fptpost.modules.notification.service.NotificationPublisher;
import org.junit.jupiter.api.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KycReviewServiceTest {
    static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
    static final UUID APPLICANT = UUID.fromString("e023f6ab-97e2-4c3f-8e47-3400a799af9a");
    static final UUID ACTOR = UUID.fromString("a10b998e-4dc5-4908-926c-b526960f3a8a");
    static final UUID SESSION = UUID.fromString("34213f62-0d3c-4148-88db-ae0dc13848a8");
    static final UUID ID = UUID.fromString("8c6631dc-a06c-4c18-868e-c4c398005af1");
    final KycRequestRepository requests = mock(KycRequestRepository.class);
    final KycReviewSearchRepository search = mock(KycReviewSearchRepository.class);
    final KycFileRepository files = mock(KycFileRepository.class);
    final KycIdentityClaimRepository claims = mock(KycIdentityClaimRepository.class);
    final UserProfileRepository profiles = mock(UserProfileRepository.class);
    final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    final BlacklistRepository blacklist = mock(BlacklistRepository.class);
    final IdentityUserAccess userAccess = mock(IdentityUserAccess.class);
    final KycReviewAdminDirectory administrators = mock(KycReviewAdminDirectory.class);
    final KycCrypto crypto = mock(KycCrypto.class);
    final PrivateFileStorage storage = mock(PrivateFileStorage.class);
    final AuditOutboxService audit = mock(AuditOutboxService.class);
    final NotificationPublisher notifications = mock(NotificationPublisher.class);
    final KycReviewSlaPolicy sla = mock(KycReviewSlaPolicy.class);
    final KycReviewService reviews = new KycReviewService(requests, search, files, claims, profiles, sessions,
            blacklist, userAccess, administrators, crypto, storage, audit, notifications, sla, Clock.fixed(NOW, ZoneOffset.UTC));
    User applicant;
    User reviewer;
    KycRequest request;
    UserProfile profile;
    AuthSession session;

    @BeforeEach void setup() {
        applicant = person(1, APPLICANT, "SENDER"); reviewer = person(2, ACTOR, "MODERATOR");
        request = KycRequest.builder().id(10L).publicId(ID).user(applicant).idNumberHash("a".repeat(64)).idNumberEnc("encrypted")
                .idLastFour("9012").fullName("Người Kiểm Thử").dob(LocalDate.of(1990, 1, 1)).issuedDate(LocalDate.of(2022, 1, 1))
                .expiryDate(LocalDate.of(2036, 1, 1)).status(KycStatus.PENDING).submittedAt(NOW.minusSeconds(10)).version(4).build();
        profile = UserProfile.builder().userId(1L).user(applicant).fullName(request.getFullName()).dob(request.getDob()).build();
        session = AuthSession.builder().user(reviewer).publicId(SESSION).expiresAt(NOW.plusSeconds(3600)).build();
        var jwt = Jwt.withTokenValue("reviewer").header("alg", "HS256").subject(ACTOR.toString()).claim("sid", SESSION.toString())
                .issuedAt(NOW.minusSeconds(1)).expiresAt(NOW.plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MODERATOR"))));
        when(requests.findByPublicId(ID)).thenReturn(Optional.of(request));
        when(requests.findApplicantPublicId(ID)).thenReturn(Optional.of(APPLICANT));
        when(requests.findIdentityHash(ID)).thenReturn(Optional.of(request.getIdNumberHash()));
        when(requests.findByPublicIdForUpdate(ID)).thenReturn(Optional.of(request));
        when(userAccess.read(ACTOR)).thenReturn(reviewer);
        when(sessions.findByPublicIdAndUserId(SESSION, 2L)).thenReturn(Optional.of(session));
        when(profiles.findById(1L)).thenReturn(Optional.of(profile));
        when(requests.findFirstByUserIdOrderBySubmittedAtDescIdDesc(1L)).thenReturn(Optional.of(request));
        when(claims.lockByHash(request.getIdNumberHash())).thenReturn(Optional.of(KycIdentityClaim.builder().idNumberHash(request.getIdNumberHash())
                .user(applicant).kyc(request).build()));
        when(files.findByKycIdOrderByIdAsc(10L)).thenReturn(Arrays.stream(KycFileType.values()).map(type ->
                KycFile.builder().publicId(UUID.randomUUID()).kyc(request).type(type).mediaType("image/png").sizeBytes(100).build()).toList());
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> {
            KycRequest saved = invocation.getArgument(0); saved.setVersion(saved.getVersion() + 1); return saved;
        });
        when(sla.hours()).thenReturn(24L);
        when(sla.dueAt(any(), anyLong())).thenAnswer(invocation -> ((Instant) invocation.getArgument(0)).plus(Duration.ofHours(invocation.getArgument(1))));
        when(administrators.lockApplicantAndAdministrators(APPLICANT, ACTOR)).thenReturn(List.of(UUID.randomUUID()));
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void approvalLocksApplicantBeforeRequestAndClaimAndNeverTouchesUserRoles() {
        var result = reviews.approve(ACTOR, SESSION, ID, version());
        assertThat(result.getStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(result.getVersion()).isEqualTo(5);
        assertThat(applicant.getRoles()).extracting(Role::getCode).containsExactly("SENDER");
        var order = inOrder(administrators, requests, claims);
        order.verify(requests).findApplicantPublicId(ID);
        order.verify(requests).findIdentityHash(ID);
        order.verify(administrators).lockApplicantAndReviewer(APPLICANT, ACTOR);
        order.verify(claims).lockByHash(request.getIdNumberHash());
        order.verify(requests).findByPublicIdForUpdate(ID);
        order.verify(requests).saveAndFlush(request);
        verify(audit).enqueue(eq("KYC_APPROVED"), eq("KYC_REQUEST"), eq(ID.toString()), anyMap(), anyMap());
        verify(notifications).publish(eq(APPLICANT), any(), eq("kyc:decision:" + ID + ":5"), anyString(), anyString(), eq("/me/kyc"));
    }

    @Test void staleVersionAndPreviouslyDecidedStateHaveNoSideEffects() {
        var stale = version(); stale.setExpectedVersion(3L);
        failure(() -> reviews.approve(ACTOR, SESSION, ID, stale), "KYC_REVIEW_VERSION_CONFLICT");
        request.setStatus(KycStatus.REJECTED);
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_ALREADY_DECIDED");
        verify(requests, never()).saveAndFlush(any()); verifyNoInteractions(audit, notifications);
    }

    @Test void selfReviewAndUnavailableApplicantAreRejected() {
        request.setUser(reviewer);
        when(requests.findApplicantPublicId(ID)).thenReturn(Optional.of(ACTOR));
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_SELF_FORBIDDEN");
        request.setUser(applicant); applicant.setStatus(UserStatus.LOCKED);
        when(requests.findApplicantPublicId(ID)).thenReturn(Optional.of(APPLICANT));
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_APPLICANT_UNAVAILABLE");
        verify(requests, never()).saveAndFlush(any()); verifyNoInteractions(audit, notifications);
    }

    @Test void expiredUnderageOrChangedProfileCannotBeApproved() {
        request.setExpiryDate(LocalDate.of(2026, 10, 28));
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_INVALID_DOCUMENT");
        request.setExpiryDate(LocalDate.of(2036, 1, 1)); request.setDob(LocalDate.of(2015, 1, 1));
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_INVALID_DOCUMENT");
        request.setDob(profile.getDob()); profile.setFullName("Thông tin khác");
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_INVALID_DOCUMENT");
        verify(requests, never()).saveAndFlush(any()); verifyNoInteractions(audit, notifications);
    }

    @Test void foreignIdentityClaimBlacklistOrMissingImagesCannotBeApproved() {
        when(claims.lockByHash(anyString())).thenReturn(Optional.of(KycIdentityClaim.builder().user(reviewer).kyc(request).build()));
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_IDENTITY_CONFLICT");
        when(claims.lockByHash(anyString())).thenReturn(Optional.of(KycIdentityClaim.builder().idNumberHash(request.getIdNumberHash()).user(applicant).kyc(request).build()));
        when(blacklist.existsByKindAndValueHash("ID_NUMBER", request.getIdNumberHash())).thenReturn(true);
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_IDENTITY_CONFLICT");
        when(blacklist.existsByKindAndValueHash(anyString(), anyString())).thenReturn(false);
        when(files.findByKycIdOrderByIdAsc(10L)).thenReturn(List.of());
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_INVALID_DOCUMENT");
        verify(requests, never()).saveAndFlush(any()); verifyNoInteractions(audit, notifications);
    }

    @Test void flaggedRequestsRequireAdminButKeepTheirIdentityReservation() {
        var flag = new KycReviewFlagRequest(); flag.setExpectedVersion(4L); flag.setReason("Cần đối chiếu");
        var flagged = reviews.flag(ACTOR, SESSION, ID, flag);
        assertThat(flagged.getStatus()).isEqualTo(KycStatus.PENDING); assertThat(flagged.isFlagged()).isTrue();
        var current = version(); current.setExpectedVersion(5L);
        failure(() -> reviews.approve(ACTOR, SESSION, ID, current), "KYC_REVIEW_ADMIN_REQUIRED");
        verify(audit).enqueue(eq("KYC_FLAGGED"), anyString(), anyString(), anyMap(), anyMap());
    }

    @Test void rejectionStoresApplicantNoteButExcludesFreeTextFromAuditAndNotification() {
        var input = new KycReviewRejectRequest(); input.setExpectedVersion(4L); input.setReasonCode(KycRejectionReason.OTHER);
        input.setNote("Nội dung riêng của hồ sơ");
        var rejected = reviews.reject(ACTOR, SESSION, ID, input);
        assertThat(rejected.getRejectReason()).contains(input.getNote());
        verify(audit).enqueue(eq("KYC_REJECTED"), anyString(), anyString(), anyMap(),
                argThat(after -> !after.toString().contains(input.getNote()) && after.get("reasonCode").equals("OTHER")));
        verify(notifications).publish(any(), any(), anyString(), anyString(), argThat(body -> !body.contains(input.getNote())), anyString());
    }

    @Test void invalidNotesAndFlagsAreRejectedBeforeAcquiringAnyDataLocks() {
        var input = new KycReviewRejectRequest(); input.setExpectedVersion(4L); input.setReasonCode(KycRejectionReason.OTHER); input.setNote(" ");
        failure(() -> reviews.reject(ACTOR, SESSION, ID, input), "KYC_REVIEW_INVALID_REASON");
        input.setNote("x".repeat(401)); failure(() -> reviews.reject(ACTOR, SESSION, ID, input), "KYC_REVIEW_INVALID_REASON");
        var flag = new KycReviewFlagRequest(); flag.setExpectedVersion(4L); flag.setReason(" ");
        failure(() -> reviews.flag(ACTOR, SESSION, ID, flag), "KYC_REVIEW_INVALID_REASON");
        verifyNoInteractions(requests, administrators, audit, notifications);
    }

    @Test void revokedSessionAndActorImpersonationAreRejected() {
        session.setRevokedAt(NOW);
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "SYS_UNAUTHORIZED");
        failure(() -> reviews.approve(UUID.randomUUID(), SESSION, ID, version()), "SYS_UNAUTHORIZED");
        verify(requests, never()).saveAndFlush(any()); verifyNoInteractions(audit, notifications);
    }

    @Test void lockedApplicantCanStillReceiveARejectionDecision() {
        applicant.setStatus(UserStatus.LOCKED);
        var input = new KycReviewRejectRequest(); input.setExpectedVersion(4L); input.setReasonCode(KycRejectionReason.INVALID_SELFIE);
        assertThat(reviews.reject(ACTOR, SESSION, ID, input).getStatus()).isEqualTo(KycStatus.REJECTED);
        verify(notifications).publish(eq(APPLICANT), any(), anyString(), anyString(), anyString(), eq("/me/kyc"));
    }

    @Test void rejectionAndFlagLockTheReviewerBeforeTheirRequest() {
        var rejection = new KycReviewRejectRequest(); rejection.setExpectedVersion(4L);
        rejection.setReasonCode(KycRejectionReason.INVALID_SELFIE);
        reviews.reject(ACTOR, SESSION, ID, rejection);
        var order = inOrder(administrators, requests, userAccess);
        order.verify(administrators).lockApplicantAndReviewer(APPLICANT, ACTOR);
        order.verify(requests).findByPublicIdForUpdate(ID);
        order.verify(userAccess).read(ACTOR);

        clearInvocations(administrators, requests, userAccess);
        request.setStatus(KycStatus.PENDING); request.setVersion(4L);
        var flag = new KycReviewFlagRequest(); flag.setExpectedVersion(4L); flag.setReason("Cần kiểm tra");
        reviews.flag(ACTOR, SESSION, ID, flag);
        order = inOrder(administrators, requests, userAccess);
        order.verify(administrators).lockApplicantAndAdministrators(APPLICANT, ACTOR);
        order.verify(requests).findByPublicIdForUpdate(ID);
        order.verify(userAccess).read(ACTOR);
    }

    @Test void approvalRechecksTheIdentityHashAfterLocking() {
        when(requests.findIdentityHash(ID)).thenReturn(Optional.of("b".repeat(64)));
        failure(() -> reviews.approve(ACTOR, SESSION, ID, version()), "KYC_REVIEW_IDENTITY_CONFLICT");
        verify(requests, never()).saveAndFlush(any()); verifyNoInteractions(audit, notifications);
    }

    private KycReviewVersionRequest version() { var result = new KycReviewVersionRequest(); result.setExpectedVersion(4L); return result; }
    private User person(long id, UUID publicId, String role) {
        return User.builder().id(id).publicId(publicId).email("test" + id + "@example.invalid").status(UserStatus.ACTIVE)
                .emailVerifiedAt(NOW).roles(Set.of(Role.builder().code(role).build())).build();
    }
    private void failure(Runnable operation, String code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }
}

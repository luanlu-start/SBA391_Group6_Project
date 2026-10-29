package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.identity.dto.request.KycReviewVersionRequest;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.KycReviewSearchRepository;
import com.fptpost.modules.identity.service.*;
import com.fptpost.modules.notification.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class KycReviewFlowHttpTest extends IdentityFeatureHttpTest {
    static final String URL = "/api/v1/mod/kyc";
    @Autowired KycReviewService reviews;
    @Autowired KycReviewSlaAlertService alerts;
    @Autowired KycReviewSearchRepository reviewSearch;
    @Autowired AuditOutboxRepository auditOutbox;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    User moderator;
    User administrator;
    String moderatorToken;
    String administratorToken;

    @BeforeEach void setupReview() throws Exception {
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll();
        moderator = staff("MODERATOR", 1); administrator = staff("ADMIN", 2);
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        administratorToken = access(login(administrator.getEmail(), "OldPassword123!", 200));
    }

    @Test void queueUsesRealPagingFiltersMaskedFieldsAndDynamicDeadline() throws Exception {
        var first = submitted(user, token, "123456789012");
        now.set(now.get().plusSeconds(1));
        User second = createUser("second@example.invalid", "+84877777771");
        var secondToken = access(login(second.getEmail(), "OldPassword123!", 200));
        submitted(second, secondToken, "123456789013");
        var page = data(send(get(URL).header("Authorization", "Bearer " + moderatorToken).param("size", "1"), 200));
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("content").get(0).path("id").asText()).isEqualTo(first);
        assertThat(page.path("content").get(0).has("files")).isFalse();
        var next = data(send(get(URL).header("Authorization", "Bearer " + moderatorToken).param("size", "1").param("page", "1"), 200));
        assertThat(next.path("content").get(0).path("id").asText()).isNotEqualTo(first);
        for (String literal : List.of("%", "_", "[abc]")) {
            assertThat(data(send(get(URL).header("Authorization", "Bearer " + moderatorToken).param("q", literal), 200))
                    .path("totalElements").asInt()).isZero();
        }
        var result = send(get(URL + "/" + first).header("Authorization", "Bearer " + moderatorToken), 200);
        var detail = data(result);
        assertThat(detail.path("files")).hasSize(3);
        assertThat(detail.path("idNumberMasked").asText()).isEqualTo("********9012");
        assertThat(detail.path("dueAt").asText()).isEqualTo("2026-10-21T12:00:00Z");
        assertThat(detail.path("overdue").asBoolean()).isFalse();
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(detail.toString()).doesNotContain("123456789012", "storageKey", "idNumberEnc", "idNumberHash", "signature");
        assertThat(data(send(get(URL).header("Authorization", "Bearer " + moderatorToken).param("q", first), 200))
                .path("totalElements").asInt()).isEqualTo(1);
    }

    @Test void approvalAuditsAndNotifiesAtomicallyWithoutGrantingCourier() throws Exception {
        String id = submitted(user, token, "123456789012");
        var approved = data(write("POST", URL + "/" + id + "/approve", Map.of("expectedVersion", 0), moderatorToken, 200));
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.path("version").asLong()).isEqualTo(1);
        assertThat(approved.path("reviewerName").asText()).isEqualTo("Người Dùng");
        assertThat(users.findByPublicId(user.getPublicId()).orElseThrow().getRoles()).extracting("code").doesNotContain("COURIER");
        assertThat(data(send(get("/api/v1/me/courier-registration").header("Authorization", "Bearer " + token), 200))
                .path("eligible").asBoolean()).isTrue();
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(user.getPublicId())
                && event.getEventKey().startsWith("kyc:decision:" + id));
        assertThat(auditOutbox.findAll()).singleElement().satisfies(event ->
                assertThat(event.getPayload()).contains("KYC_APPROVED").doesNotContain("123456789012", "idNumberHash", "idNumberEnc", "files"));
        write("POST", URL + "/" + id + "/approve", Map.of("expectedVersion", 0), moderatorToken, 409);
    }

    @Test void rejectionCatalogRequiresOtherNoteAndLetsApplicantResubmit() throws Exception {
        String id = submitted(user, token, "123456789012");
        var catalog = data(send(get(URL + "/rejection-reasons").header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(catalog).hasSize(5);
        write("POST", URL + "/" + id + "/reject", Map.of("expectedVersion", 0, "reasonCode", "OTHER", "note", "  "), moderatorToken, 400);
        write("POST", URL + "/" + id + "/reject", Map.of("expectedVersion", 0, "reasonCode", "OTHER", "note", "x".repeat(401)), moderatorToken, 400);
        var rejected = data(write("POST", URL + "/" + id + "/reject", Map.of("expectedVersion", 0,
                "reasonCode", "UNREADABLE_DOCUMENT", "note", "  Vui lòng chụp lại mặt trước  "), moderatorToken, 200));
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.path("rejectReason").asText()).contains("Ảnh giấy tờ không rõ", "Vui lòng chụp lại mặt trước");
        assertThat(data(send(get("/api/v1/me/kyc").header("Authorization", "Bearer " + token), 200))
                .path("request").path("rejectReason").asText()).contains("Vui lòng chụp lại mặt trước");
        now.set(now.get().plusSeconds(1));
        assertThat(submitted(user, token, "123456789012")).isNotEqualTo(id);
    }

    @Test void fraudEscalationRetainsPendingReservationAndRequiresAdminFinalDecision() throws Exception {
        String id = submitted(user, token, "123456789012");
        var flagged = data(write("POST", URL + "/" + id + "/flag", Map.of("expectedVersion", 0, "reason", "Cần đối chiếu thêm"), moderatorToken, 200));
        assertThat(flagged.path("status").asText()).isEqualTo("PENDING");
        assertThat(flagged.path("flagged").asBoolean()).isTrue();
        assertThat(kycClaims.count()).isEqualTo(1);
        write("POST", URL + "/" + id + "/approve", Map.of("expectedVersion", 1), moderatorToken, 403);
        write("POST", URL + "/" + id + "/reject", Map.of("expectedVersion", 1, "reasonCode", "INVALID_SELFIE"), moderatorToken, 403);
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(administrator.getPublicId())
                && event.getEventKey().startsWith("kyc:flag:"));
        write("POST", URL + "/" + id + "/approve", Map.of("expectedVersion", 1), administratorToken, 200);
    }

    @Test void protectedImagesAreBoundToReviewerSessionAndEveryViewIsAudited() throws Exception {
        String id = submitted(user, token, "123456789012");
        var file = kycFiles.findAll().getFirst();
        String path = URL + "/" + id + "/files/" + file.getPublicId();
        String link = data(write("POST", path + "/link", Map.of(), moderatorToken, 200)).path("url").asText();
        var response = send(get("/api/v1" + link).header("Authorization", "Bearer " + moderatorToken), 200);
        assertThat(response.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("KYC_FILE_VIEWED"));
        String otherSession = access(login(moderator.getEmail(), "OldPassword123!", 200));
        send(get("/api/v1" + link).header("Authorization", "Bearer " + otherSession), 403);
        send(get("/api/v1" + link).header("Authorization", "Bearer " + administratorToken), 403);
        send(get("/api/v1" + link).header("Authorization", "Bearer " + token), 403);
        send(get("/api/v1" + link.replace("signature=", "signature=0")).header("Authorization", "Bearer " + moderatorToken), 403);
        now.set(now.get().plusSeconds(300));
        send(get("/api/v1" + link).header("Authorization", "Bearer " + moderatorToken), 403);
    }

    @Test void senderAndAnonymousCannotAccessAnyReviewOperation() throws Exception {
        String id = submitted(user, token, "123456789012");
        for (String bearer : List.of("", token)) {
            int denied = bearer.isEmpty() ? 401 : 403;
            for (String suffix : List.of("", "/" + id, "/rejection-reasons")) {
                var request = get(URL + suffix); if (!bearer.isEmpty()) request.header("Authorization", "Bearer " + bearer);
                send(request, denied);
            }
            for (String action : List.of("approve", "reject", "flag")) {
                write("POST", URL + "/" + id + "/" + action, Map.of("expectedVersion", 0), bearer.isEmpty() ? null : bearer, denied);
            }
        }
        send(get(URL).header("Authorization", "Bearer " + moderatorToken).param("size", "101"), 400);
        send(get(URL).header("Authorization", "Bearer " + moderatorToken).param("status", "NOT_SUBMITTED"), 400);
    }

    @Test void simultaneousDecisionsAcceptExactlyOneExpectedVersion() throws Exception {
        String id = submitted(user, token, "123456789012");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return decide(id, "approve", Map.of("expectedVersion", 0)); });
            var second = executor.submit(() -> { start.await(); return decide(id, "reject", Map.of("expectedVersion", 0, "reasonCode", "INVALID_SELFIE")); });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(auditOutbox.count()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test void rollbackRestoresDecisionAuditAndNotificationTogether() throws Exception {
        String id = submitted(user, token, "123456789012");
        var jwt = decoder.decode(moderatorToken);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_MODERATOR"))));
        try {
            new TransactionTemplate(transactions).execute(transaction -> {
                var request = new KycReviewVersionRequest(); request.setExpectedVersion(0L);
                reviews.approve(moderator.getPublicId(), UUID.fromString(jwt.getClaimAsString("sid")), UUID.fromString(id), request);
                transaction.setRollbackOnly(); return null;
            });
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(kycRequests.findByPublicId(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(auditOutbox.count()).isZero(); assertThat(notifications.count()).isZero();
    }

    @Test void administrativeDecisionAndOverdueScanAcquireCompatibleUserLocks() throws Exception {
        String id = submitted(user, token, "123456789012");
        now.set(now.get().plus(Duration.ofHours(24)));
        administratorToken = access(login(administrator.getEmail(), "OldPassword123!", 200));
        var candidate = new KycReviewSearchRepository.Candidate(UUID.fromString(id), user.getPublicId());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var review = executor.submit(() -> {
                start.await();
                return mvc.perform(post(URL + "/" + id + "/approve").header("Authorization", "Bearer " + administratorToken)
                        .contentType("application/json").content(json.writeValueAsBytes(Map.of("expectedVersion", 0))))
                        .andReturn().getResponse().getStatus();
            });
            var overdue = executor.submit(() -> { start.await(); return alerts.alert(candidate, 24, now.get()); });
            start.countDown();
            int status = review.get(20, TimeUnit.SECONDS);
            assertThat(status).isIn(200, 409);
            overdue.get(20, TimeUnit.SECONDS);
            if (status == 409) {
                long version = kycRequests.findByPublicId(UUID.fromString(id)).orElseThrow().getVersion();
                write("POST", URL + "/" + id + "/approve", Map.of("expectedVersion", version), administratorToken, 200);
            }
        }
        assertThat(kycRequests.findByPublicId(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(auditOutbox.count()).isEqualTo(1);
        assertThat(notifications.findAll().stream().filter(event -> event.getEventKey().startsWith("kyc:decision:"))).hasSize(1);
    }

    @Test void approvalAndForeignDuplicateSubmissionAcquireClaimBeforeRequest() throws Exception {
        String id = submitted(user, token, "123456789012");
        var duplicate = createUser("duplicate-claim@example.invalid", "+84812345677");
        String duplicateToken = access(login(duplicate.getEmail(), "OldPassword123!", 200));
        Map<String, Object> input = Map.of("idNumber", "123456789012", "fullName", "Người Dùng", "dob", "1990-01-01",
                "issuedDate", "2022-01-01", "expiryDate", "2036-01-01", "consentAccepted", true, "policyVersion", "kyc-v1");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var approval = executor.submit(() -> { start.await(); return decide(id, "approve", Map.of("expectedVersion", 0)); });
            var submission = executor.submit(() -> {
                start.await();
                return mvc.perform(multipart("/api/v1/me/kyc")
                        .file(new org.springframework.mock.web.MockMultipartFile("request", "", "application/json", json.writeValueAsBytes(input)))
                        .file(image("front", "png")).file(image("back", "jpg")).file(image("portrait", "png"))
                        .header("Authorization", "Bearer " + duplicateToken)).andReturn();
            });
            start.countDown();
            assertThat(approval.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            var rejected = submission.get(20, TimeUnit.SECONDS);
            assertThat(rejected.getResponse().getStatus()).isEqualTo(422);
            assertThat(body(rejected).path("code").asText()).isEqualTo("KYC_ID_DUPLICATED");
        }
        assertThat(kycRequests.findByPublicId(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(kycClaims.count()).isEqualTo(1);
    }

    @Test void overdueAlertsArePersistedOnceAndExcludeAlreadyReviewedRequests() throws Exception {
        String id = submitted(user, token, "123456789012");
        now.set(now.get().plus(Duration.ofHours(24)));
        var candidate = new KycReviewSearchRepository.Candidate(UUID.fromString(id), user.getPublicId());
        assertThat(alerts.alert(candidate, 24, now.get())).isTrue();
        assertThat(alerts.alert(candidate, 24, now.get())).isFalse();
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(kycRequests.findByPublicId(UUID.fromString(id)).orElseThrow().getSlaAlertedHours()).isEqualTo(24L);
        List<KycReviewSearchRepository.Candidate> remaining = new TransactionTemplate(transactions)
                .execute(transaction -> reviewSearch.overdueCandidates(now.get().minus(Duration.ofHours(24)), 24, 50));
        assertThat(remaining).isEmpty();
        administratorToken = access(login(administrator.getEmail(), "OldPassword123!", 200));
        var queue = data(send(get(URL).header("Authorization", "Bearer " + administratorToken).param("overdue", "true"), 200));
        assertThat(queue.path("totalElements").asInt()).isEqualTo(1);
        assertThat(queue.path("content").get(0).path("overdue").asBoolean()).isTrue();
        long version = kycRequests.findByPublicId(UUID.fromString(id)).orElseThrow().getVersion();
        write("POST", URL + "/" + id + "/approve", Map.of("expectedVersion", version), administratorToken, 200);
        assertThat(alerts.alert(candidate, 12, now.get())).isFalse();
    }

    private int decide(String id, String action, Map<String, Object> input) throws Exception {
        return mvc.perform(post(URL + "/" + id + "/" + action).header("Authorization", "Bearer " + moderatorToken)
                .contentType("application/json").content(json.writeValueAsBytes(input))).andReturn().getResponse().getStatus();
    }

    private User staff(String role, int offset) {
        int number = sequence.incrementAndGet();
        User result = createUser(role.toLowerCase(Locale.ROOT) + number + "@example.invalid", "+848" + String.format(Locale.ROOT, "%08d", number + offset));
        result.setRoles(new HashSet<>(Set.of(roles.findByCode(role).orElseThrow())));
        return users.saveAndFlush(result);
    }

    String submitted(User applicant, String bearer, String identity) throws Exception {
        Map<String, Object> input = Map.of("idNumber", identity, "fullName", "Người Dùng", "dob", "1990-01-01",
                "issuedDate", "2022-01-01", "expiryDate", "2036-01-01", "consentAccepted", true, "policyVersion", "kyc-v1");
        var response = send(multipart("/api/v1/me/kyc").file(new org.springframework.mock.web.MockMultipartFile("request", "", "application/json", json.writeValueAsBytes(input)))
                .file(image("front", "png")).file(image("back", "jpg")).file(image("portrait", "png"))
                .header("Authorization", "Bearer " + bearer), 201);
        return data(response).path("request").path("id").asText();
    }
}

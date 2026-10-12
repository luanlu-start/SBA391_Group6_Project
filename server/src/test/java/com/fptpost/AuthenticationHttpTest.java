package com.fptpost;

import com.fasterxml.jackson.databind.*;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.enums.OtpType;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authentication;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.mongodb.auto-index-creation=false",
        "app.registration.requests-per-minute=1000", "management.health.mail.enabled=false"
})
@AutoConfigureMockMvc
class AuthenticationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RoleRepository roles;
    @Autowired AuthSessionRepository sessions;
    @Autowired OtpCodeRepository otps;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender mailSender;
    @MockitoBean Clock identityClock;
    final AtomicReference<Instant> now = new AtomicReference<>();
    static final AtomicInteger sequence = new AtomicInteger();
    String ip;
    User user;

    @BeforeEach void setup() {
        ip = "192.0.2." + sequence.incrementAndGet();
        now.set(Instant.parse("2026-10-11T12:00:00Z"));
        when(identityClock.instant()).thenAnswer(invocation -> now.get());
        sessions.deleteAll(); otps.deleteAll(); profiles.deleteAll(); users.deleteAll();
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            user = users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email("login@example.com")
                    .phone("+84901234567").passwordHash(encoder.encode("Secret123!"))
                    .status(UserStatus.ACTIVE).emailVerifiedAt(now.get()).createdAt(now.get()).updatedAt(now.get())
                    .roles(new HashSet<>(Set.of(roles.findByCode("SENDER").orElseThrow()))).build());
            profiles.saveAndFlush(UserProfile.builder().user(user).fullName("Người đăng nhập").adultConfirmedAt(now.get()).build());
        });
    }
    record Csrf(Cookie cookie, String header, String token) {}
    Csrf csrf() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var data = body(result).path("data");
        return new Csrf(cookie(result, "XSRF-TOKEN"), data.path("headerName").asText(), data.path("token").asText());
    }
    MvcResult send(MockHttpServletRequestBuilder request, int status) throws Exception {
        return mvc.perform(request.with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().is(status)).andReturn();
    }
    MvcResult login(String identifier, String password, int status) throws Exception {
        Csrf csrf = csrf();
        return send(post("/api/v1/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                .contentType("application/json").content(json.writeValueAsBytes(Map.of("identifier", identifier, "password", password))), status);
    }
    MvcResult refresh(Cookie token, int status) throws Exception {
        Csrf csrf = csrf();
        return send(post("/api/v1/auth/refresh").cookie(csrf.cookie(), token).header(csrf.header(), csrf.token()), status);
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    String access(MvcResult result) throws Exception { return body(result).path("data").path("accessToken").asText(); }
    static Cookie cookie(MvcResult result, String name) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream().filter(value -> value.startsWith(name + "=")).findFirst().orElseThrow();
        return new Cookie(name, header.substring(name.length() + 1).split(";", 2)[0]);
    }
    MvcResult me(String access, int status) throws Exception {
        return send(get("/api/v1/me").header("Authorization", "Bearer " + access), status);
    }
    UUID sessionId(MvcResult result) throws Exception {
        return UUID.fromString(decoder.decode(access(result)).getClaimAsString("sid"));
    }
    MvcResult listSessions(MvcResult login) throws Exception {
        return send(get("/api/v1/auth/sessions").header("Authorization", "Bearer " + access(login)), 200);
    }
    MvcResult revoke(MvcResult login, UUID target, int status) throws Exception {
        Csrf csrf = csrf();
        return send(delete("/api/v1/auth/sessions/" + target).header("Authorization", "Bearer " + access(login))
                .cookie(csrf.cookie()).header(csrf.header(), csrf.token()), status);
    }
    User anotherUser() {
        return new TransactionTemplate(transactions).execute(transaction -> {
            User other = users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email("another@example.com")
                    .phone("+84912345678").passwordHash(encoder.encode("Secret123!"))
                    .status(UserStatus.ACTIVE).emailVerifiedAt(now.get()).createdAt(now.get()).updatedAt(now.get())
                    .roles(new HashSet<>(Set.of(roles.findByCode("SENDER").orElseThrow()))).build());
            profiles.saveAndFlush(UserProfile.builder().user(other).fullName("Người dùng khác").adultConfirmedAt(now.get()).build());
            return other;
        });
    }

    @Test void sessionListContainsOnlyOwnActiveSessionsAndMarksCurrentWithoutLeakingTokens() throws Exception {
        var first = login(user.getEmail(), "Secret123!", 200);
        var rotated = refresh(cookie(first, "fptpost_refresh"), 200);
        var expired = login(user.getEmail(), "Secret123!", 200);
        var expiredSession = sessions.findByPublicId(sessionId(expired)).orElseThrow();
        expiredSession.setExpiresAt(now.get().minusSeconds(1)); sessions.saveAndFlush(expiredSession);
        var revoked = login(user.getEmail(), "Secret123!", 200);
        var revokedSession = sessions.findByPublicId(sessionId(revoked)).orElseThrow();
        revokedSession.setRevokedAt(now.get()); sessions.saveAndFlush(revokedSession);
        var current = login(user.getEmail(), "Secret123!", 200);
        var foreign = login(anotherUser().getEmail(), "Secret123!", 200);
        var response = listSessions(current);
        var data = body(response).path("data");
        assertThat(data.size()).isEqualTo(2);
        assertThat(data.get(0).path("id").asText()).isEqualTo(sessionId(current).toString());
        assertThat(data.get(0).path("current").asBoolean()).isTrue();
        assertThat(data.get(1).path("id").asText()).isEqualTo(sessionId(rotated).toString());
        assertThat(data.get(1).path("current").asBoolean()).isFalse();
        assertThat(data.get(0).path("ip").asText()).isEqualTo(ip);
        assertThat(data.get(0).has("createdAt")).isTrue();
        assertThat(data.get(0).has("expiresAt")).isTrue();
        assertThat(response.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(data.toString()).doesNotContain("tokenHash", "refreshToken", "passwordHash", "userId",
                cookie(current, "fptpost_refresh").getValue(), sessionId(foreign).toString());
    }
    @Test void remoteRevocationInvalidatesOnlySelectedSessionImmediately() throws Exception {
        var current = login(user.getEmail(), "Secret123!", 200);
        var target = login(user.getEmail(), "Secret123!", 200);
        var response = revoke(current, sessionId(target), 200);
        assertThat(body(response).path("data").path("currentSessionRevoked").asBoolean()).isFalse();
        assertThat(response.getResponse().getHeaders("Set-Cookie").toString()).doesNotContain("fptpost_refresh");
        me(access(target), 401);
        assertThat(body(refresh(cookie(target, "fptpost_refresh"), 401)).path("code").asText()).isEqualTo("AUTH_REFRESH_INVALID");
        me(access(current), 200);
        assertThat(body(listSessions(current)).path("data").size()).isEqualTo(1);
    }
    @Test void revokingCurrentSessionClearsRefreshCookieAndInvalidatesItsJwt() throws Exception {
        var current = login(user.getEmail(), "Secret123!", 200);
        var response = revoke(current, sessionId(current), 200);
        assertThat(body(response).path("data").path("currentSessionRevoked").asBoolean()).isTrue();
        assertThat(response.getResponse().getHeaders("Set-Cookie").toString()).contains("fptpost_refresh=", "Max-Age=0");
        me(access(current), 401);
        refresh(cookie(current, "fptpost_refresh"), 401);
    }
    @Test void revocationRejectsForeignMissingExpiredAndAlreadyRevokedSessions() throws Exception {
        var current = login(user.getEmail(), "Secret123!", 200);
        var foreign = login(anotherUser().getEmail(), "Secret123!", 200);
        var expired = login(user.getEmail(), "Secret123!", 200);
        UUID expiredId = sessionId(expired);
        var expiredSession = sessions.findByPublicId(expiredId).orElseThrow();
        expiredSession.setExpiresAt(now.get()); sessions.saveAndFlush(expiredSession);
        for (UUID id : List.of(sessionId(foreign), UUID.randomUUID(), expiredId)) {
            assertThat(body(revoke(current, id, 404)).path("code").asText()).isEqualTo("AUTH_SESSION_NOT_FOUND");
        }
        var target = login(user.getEmail(), "Secret123!", 200);
        UUID targetId = sessionId(target);
        revoke(current, targetId, 200);
        assertThat(body(revoke(current, targetId, 404)).path("code").asText()).isEqualTo("AUTH_SESSION_NOT_FOUND");
        me(access(current), 200); me(access(foreign), 200);
    }
    @Test void sessionEndpointsRequireBearerAuthenticationAndRejectMalformedIds() throws Exception {
        send(get("/api/v1/auth/sessions"), 401);
        Csrf csrf = csrf();
        send(delete("/api/v1/auth/sessions/" + UUID.randomUUID()).cookie(csrf.cookie()).header(csrf.header(), csrf.token()), 401);
        var current = login(user.getEmail(), "Secret123!", 200);
        var target = login(user.getEmail(), "Secret123!", 200);
        send(delete("/api/v1/auth/sessions/" + sessionId(target)).cookie(cookie(current, "fptpost_refresh")), 401);
        me(access(target), 200);
        send(delete("/api/v1/auth/sessions/" + sessionId(target)).header("Authorization", "Bearer " + access(current)), 200);
        send(delete("/api/v1/auth/sessions/not-a-uuid").header("Authorization", "Bearer " + access(current))
                .cookie(csrf.cookie()).header(csrf.header(), csrf.token()), 400);
    }
    @Test void revocationFollowsRotationAndRefreshPreservesLoginTime() throws Exception {
        var current = login(user.getEmail(), "Secret123!", 200);
        UUID originalId = sessionId(current);
        Instant loggedInAt = now.get();
        now.set(now.get().plusSeconds(60));
        var refreshed = refresh(cookie(current, "fptpost_refresh"), 200);
        assertThat(sessions.findByPublicId(sessionId(refreshed)).orElseThrow().getCreatedAt()).isEqualTo(loggedInAt);
        var response = revoke(refreshed, originalId, 200);
        assertThat(body(response).path("data").path("currentSessionRevoked").asBoolean()).isTrue();
        me(access(refreshed), 401);
        refresh(cookie(refreshed, "fptpost_refresh"), 401);
    }
    @Test void concurrentRefreshAndRevocationCannotLeaveSelectedDeviceActive() throws Exception {
        var current = login(user.getEmail(), "Secret123!", 200);
        var target = login(user.getEmail(), "Secret123!", 200);
        UUID targetId = sessionId(target);
        Csrf csrf = csrf();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var refreshResult = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/refresh").cookie(csrf.cookie(), cookie(target, "fptpost_refresh"))
                        .header(csrf.header(), csrf.token())).andReturn().getResponse().getStatus();
            });
            var revokeResult = pool.submit(() -> {
                start.await();
                return mvc.perform(delete("/api/v1/auth/sessions/" + targetId).header("Authorization", "Bearer " + access(current))
                        .cookie(csrf.cookie()).header(csrf.header(), csrf.token())).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(refreshResult.get(20, TimeUnit.SECONDS)).isIn(200, 401);
            assertThat(revokeResult.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        var active = sessions.findByUserIdAndRevokedAtIsNullAndReplacedByIsNullAndExpiresAtAfterOrderByCreatedAtAscIdAsc(user.getId(), now.get());
        assertThat(active).extracting(AuthSession::getPublicId).containsExactly(sessionId(current));
        me(access(current), 200);
    }

    @Test void loginByEmailOrPhoneIssuesJwtAndHashedHttpOnlyRefreshCookie() throws Exception {
        for (String identifier : List.of(" LOGIN@EXAMPLE.COM ", "0901234567", "+84901234567")) {
            var result = login(identifier, "Secret123!", 200);
            var response = body(result).path("data");
            var jwt = decoder.decode(access(result));
            assertThat(jwt.getSubject()).isEqualTo(user.getPublicId().toString());
            assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SENDER");
            assertThat(jwt.getClaimAsString("sid")).hasSize(36);
            assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds()).isEqualTo(900);
            assertThat(response.path("user").path("id").asText()).isEqualTo(user.getPublicId().toString());
            assertThat(response.path("user").path("fullName").asText()).isEqualTo("Người đăng nhập");
            var raw = cookie(result, "fptpost_refresh").getValue();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(raw, "Secret123!", "passwordHash", "tokenHash", "refreshToken");
            assertThat(result.getResponse().getHeaders("Set-Cookie").toString()).contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
            assertThat(sessions.findAll()).allSatisfy(session -> assertThat(session.getTokenHash()).hasSize(64).isNotEqualTo(raw));
            me(access(result), 200);
        }
    }
    @Test void invalidCredentialsHaveSameMessageAndNeverCreateSessions() throws Exception {
        assertThat(body(login("login@example.com", "wrong", 401)).path("code").asText()).isEqualTo("AUTH_LOGIN_FAILED");
        assertThat(body(login("unknown@example.com", "wrong", 401)).path("code").asText()).isEqualTo("AUTH_LOGIN_FAILED");
        assertThat(users.findById(user.getId()).orElseThrow().getFailedLoginCount()).isEqualTo(1);
        assertThat(sessions.count()).isZero();
    }
    @Test void rejectsPendingLockedAndDeletedAccountsEvenWithCorrectPassword() throws Exception {
        pendingUser();
        assertThat(body(login(user.getEmail(), "Secret123!", 403)).path("code").asText()).isEqualTo("AUTH_ACCOUNT_UNVERIFIED");
        for (UserStatus status : List.of(UserStatus.LOCKED, UserStatus.DELETED)) {
            user.setStatus(status); user.setEmailVerifiedAt(now.get()); user = users.saveAndFlush(user);
            assertThat(body(login(user.getEmail(), "Secret123!", 403)).path("code").asText()).isEqualTo("AUTH_ACCOUNT_DISABLED");
        }
        user.setStatus(UserStatus.ACTIVE); user.setDeleted(true); users.saveAndFlush(user);
        login(user.getEmail(), "Secret123!", 403);
        assertThat(sessions.count()).isZero();
    }
    OtpCode pendingUser() {
        user.setStatus(UserStatus.PENDING_VERIFICATION); user.setEmailVerifiedAt(null); user = users.saveAndFlush(user);
        return otps.saveAndFlush(OtpCode.builder().publicId(UUID.randomUUID()).user(user).target(user.getEmail())
                .type(OtpType.REGISTRATION).codeHash(encoder.encode("123456"))
                .createdAt(now.get()).expiresAt(now.get().plusSeconds(300)).build());
    }
    @Test void correctPendingLoginReturnsChallengeWithoutSessionOrMailAndResetsFailedAttempts() throws Exception {
        var challenge = pendingUser();
        var wrong = body(login(user.getEmail(), "wrong", 401));
        assertThat(wrong.has("verification")).isFalse();
        assertThat(wrong.toString()).doesNotContain(challenge.getPublicId().toString());
        var result = login("0901234567", "Secret123!", 403);
        var problem = body(result);
        assertThat(problem.path("code").asText()).isEqualTo("AUTH_ACCOUNT_UNVERIFIED");
        var verification = problem.path("verification");
        assertThat(verification.path("challengeId").asText()).isEqualTo(challenge.getPublicId().toString());
        assertThat(verification.path("verificationChannel").asText()).isEqualTo("EMAIL");
        assertThat(verification.path("maskedTarget").asText()).contains("*").isNotEqualTo(user.getEmail());
        assertThat(Instant.parse(verification.path("expiresAt").asText())).isEqualTo(now.get().plusSeconds(300));
        assertThat(Instant.parse(verification.path("resendAvailableAt").asText())).isEqualTo(now.get().plusSeconds(60));
        assertThat(problem.toString()).doesNotContain("Secret123!", "123456", "codeHash", "accessToken", user.getEmail());
        assertThat(result.getResponse().getHeaders("Set-Cookie").toString()).doesNotContain("fptpost_refresh");
        assertThat(users.findById(user.getId()).orElseThrow().getFailedLoginCount()).isZero();
        assertThat(sessions.count()).isZero();
        assertThat(otps.count()).isEqualTo(1);
        verifyNoInteractions(mailSender);
    }
    @Test void pendingLoginReturnsLatestChallengeEvenWhenExpiredAndNeverReturnsItForDeletedUser() throws Exception {
        pendingUser();
        now.set(now.get().plusSeconds(60));
        var latest = pendingUser();
        now.set(now.get().plusSeconds(301));
        var problem = body(login(user.getEmail(), "Secret123!", 403));
        assertThat(problem.path("verification").path("challengeId").asText()).isEqualTo(latest.getPublicId().toString());
        assertThat(Instant.parse(problem.path("verification").path("expiresAt").asText())).isBefore(now.get());
        assertThat(otps.count()).isEqualTo(2);
        user.setDeleted(true); users.saveAndFlush(user);
        assertThat(body(login(user.getEmail(), "Secret123!", 403)).has("verification")).isFalse();
        assertThat(sessions.count()).isZero();
        verifyNoInteractions(mailSender);
    }
    @Test void fiveFailuresPersistAndLockAccountAcrossIpsThenUnlockAfterFifteenMinutes() throws Exception {
        for (int i = 0; i < 5; i++) { ip = "198.51.100." + sequence.incrementAndGet(); login(user.getEmail(), "wrong", 401); }
        ip = "203.0.113." + sequence.incrementAndGet();
        var locked = login(user.getEmail(), "Secret123!", 429);
        assertThat(locked.getResponse().getHeader("Retry-After")).isEqualTo("900");
        assertThat(users.findById(user.getId()).orElseThrow().getFailedLoginCount()).isEqualTo(5);
        now.set(now.get().plusSeconds(900));
        login(user.getEmail(), "Secret123!", 200);
        assertThat(users.findById(user.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }
    @Test void unknownAccountFailuresAlsoLockIpAndSuccessResetsCounters() throws Exception {
        for (int i = 0; i < 5; i++) login("unknown@example.com", "wrong", 401);
        login(user.getEmail(), "Secret123!", 429);
        now.set(now.get().plusSeconds(900));
        login(user.getEmail(), "wrong", 401);
        login(user.getEmail(), "Secret123!", 200);
        assertThat(users.findById(user.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }
    @Test void refreshRotatesTokenAndOldTokenReplayRevokesEveryUserSession() throws Exception {
        var first = login(user.getEmail(), "Secret123!", 200);
        var another = login(user.getEmail(), "Secret123!", 200);
        Cookie old = cookie(first, "fptpost_refresh");
        var refreshed = refresh(old, 200);
        assertThat(cookie(refreshed, "fptpost_refresh").getValue()).isNotEqualTo(old.getValue());
        me(access(first), 401); me(access(refreshed), 200);
        assertThat(body(refresh(old, 401)).path("code").asText()).isEqualTo("AUTH_REFRESH_REUSED");
        assertThat(sessions.findAll()).allSatisfy(session -> assertThat(session.getRevokedAt()).isNotNull());
        me(access(refreshed), 401); me(access(another), 401);
    }
    @Test void jwtExpiresAtFifteenMinutesAndRefreshExpiresAtSevenDays() throws Exception {
        var result = login(user.getEmail(), "Secret123!", 200);
        now.set(now.get().plusSeconds(900)); me(access(result), 401);
        var renewed = refresh(cookie(result, "fptpost_refresh"), 200);
        me(access(renewed), 200);
        now.set(now.get().plusSeconds(7 * 86400));
        assertThat(body(refresh(cookie(renewed, "fptpost_refresh"), 401)).path("code").asText()).isEqualTo("AUTH_REFRESH_INVALID");
    }
    @Test void csrfIsRequiredForLoginAndCookieMutationAndFailureDoesNotRotate() throws Exception {
        send(post("/api/v1/auth/login").contentType("application/json").content("{}"), 403);
        var result = login(user.getEmail(), "Secret123!", 200);
        send(post("/api/v1/auth/refresh").cookie(cookie(result, "fptpost_refresh")), 403);
        assertThat(sessions.count()).isEqualTo(1);
        me(access(result), 200);
    }
    @Test void logoutRevokesSessionAndJwtCannotBeUsedAfterAccountLock() throws Exception {
        var first = login(user.getEmail(), "Secret123!", 200);
        Csrf csrf = csrf();
        var result = send(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access(first))
                .cookie(csrf.cookie()).header(csrf.header(), csrf.token()), 200);
        assertThat(result.getResponse().getHeaders("Set-Cookie").toString()).contains("Max-Age=0");
        me(access(first), 401); refresh(cookie(first, "fptpost_refresh"), 401);
        var second = login(user.getEmail(), "Secret123!", 200);
        var current = users.findById(user.getId()).orElseThrow(); current.setStatus(UserStatus.LOCKED); users.saveAndFlush(current);
        me(access(second), 401);
    }
    @Test void sixthSessionEvictsOldestAndProtectedApisReturnProblemDetails() throws Exception {
        String first = null, latest = null;
        for (int i = 0; i < 6; i++) { latest = access(login(user.getEmail(), "Secret123!", 200)); if (first == null) first = latest; }
        assertThat(sessions.findAll().stream().filter(session -> session.getRevokedAt() == null)).hasSize(5);
        me(first, 401); me(latest, 200);
        send(get("/api/v1/products"), 401);
        send(get("/actuator/metrics").header("Authorization", "Bearer " + latest), 403);
        send(get("/api/v1/products/not-a-uuid").header("Authorization", "Bearer " + latest), 400);
        send(get("/api/v1/missing-endpoint").header("Authorization", "Bearer " + latest), 404);
        var invalid = me("invalid.jwt.signature", 401);
        assertThat(invalid.getResponse().getContentType()).contains("application/problem+json");
        assertThat(body(invalid).path("correlationId").asText()).hasSize(36);
    }
    @Test void concurrentRefreshOfSameTokenCommitsReplayRevocation() throws Exception {
        var login = login(user.getEmail(), "Secret123!", 200);
        var token = cookie(login, "fptpost_refresh");
        Csrf csrf = csrf();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/refresh").cookie(csrf.cookie(), token).header(csrf.header(), csrf.token()))
                        .andReturn().getResponse().getStatus();
            }));
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : results) statuses.add(future.get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
            assertThat(sessions.findAll()).allSatisfy(session -> assertThat(session.getRevokedAt()).isNotNull());
        }
    }
}

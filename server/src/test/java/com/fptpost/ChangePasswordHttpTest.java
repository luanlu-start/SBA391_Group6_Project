package com.fptpost;

import com.fasterxml.jackson.databind.*;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.dto.request.ChangePasswordRequest;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.service.PasswordService;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authentication;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.mongodb.auto-index-creation=false",
        "app.registration.requests-per-minute=1000", "management.health.mail.enabled=false",
        "app.authentication.session-cleanup-cron=-"
})
@AutoConfigureMockMvc
class ChangePasswordHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RoleRepository roles;
    @Autowired AuthSessionRepository sessions;
    @Autowired OtpCodeRepository otps;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordService passwordService;
    @Autowired RegistrationProperties properties;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender mailSender;
    @MockitoBean Clock identityClock;
    final AtomicReference<Instant> now = new AtomicReference<>();
    final List<MimeMessage> delivered = new CopyOnWriteArrayList<>();
    static final AtomicInteger sequence = new AtomicInteger();
    User user;
    String ip;

    @BeforeEach void setup() {
        int number = sequence.incrementAndGet();
        ip = "203.0.113." + number;
        now.set(Instant.parse("2026-10-18T12:00:00Z"));
        when(identityClock.instant()).thenAnswer(invocation -> now.get());
        properties.setRequestsPerMinute(1000);
        reset(mailSender); delivered.clear();
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            message.saveChanges(); delivered.add(message); return null;
        }).when(mailSender).send(any(MimeMessage.class));
        sessions.deleteAll(); otps.deleteAll(); profiles.deleteAll(); users.deleteAll();
        user = createUser("change" + number + "@example.com", "+849" + String.format(Locale.ROOT, "%08d", number));
    }

    User createUser(String email, String phone) {
        return new TransactionTemplate(transactions).execute(transaction -> {
            User created = users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email(email).phone(phone)
                    .passwordHash(encoder.encode("OldPassword123!")).status(UserStatus.ACTIVE).emailVerifiedAt(now.get())
                    .createdAt(now.get()).updatedAt(now.get())
                    .roles(new HashSet<>(Set.of(roles.findByCode("SENDER").orElseThrow()))).build());
            profiles.saveAndFlush(UserProfile.builder().user(created).fullName("Người dùng đổi mật khẩu").adultConfirmedAt(now.get()).build());
            return created;
        });
    }
    MvcResult send(MockHttpServletRequestBuilder request, int expected) throws Exception {
        return mvc.perform(request.with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().is(expected)).andReturn();
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    String access(MvcResult result) throws Exception { return body(result).path("data").path("accessToken").asText(); }
    MvcResult login(User target, String password, int expected) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn(); var data = body(csrf).path("data");
        return send(post("/api/v1/auth/login").cookie(AuthenticationHttpTest.cookie(csrf, "XSRF-TOKEN"))
                .header(data.path("headerName").asText(), data.path("token").asText())
                .contentType("application/json").content(json.writeValueAsBytes(Map.of("identifier", target.getEmail(), "password", password))), expected);
    }
    MvcResult change(String token, String current, String next, int expected) throws Exception {
        var request = post("/api/v1/auth/password/change").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("currentPassword", current, "newPassword", next)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request, expected);
    }
    void refresh(Cookie token, int expected) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn(); var data = body(csrf).path("data");
        send(post("/api/v1/auth/refresh").cookie(token, AuthenticationHttpTest.cookie(csrf, "XSRF-TOKEN"))
                .header(data.path("headerName").asText(), data.path("token").asText()), expected);
    }
    String text(Part part) throws Exception {
        if (part.isMimeType("text/plain")) return (String) part.getContent();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) { String value = text(multipart.getBodyPart(i)); if (value != null) return value; }
        }
        return null;
    }

    @Test void changingPasswordRevokesAllOwnSessionsAndPreservesOtherAccounts() throws Exception {
        var first = login(user, "OldPassword123!", 200); var second = login(user, "OldPassword123!", 200);
        var other = createUser("other" + sequence.get() + "@example.com", "+848" + String.format(Locale.ROOT, "%08d", sequence.get()));
        var otherLogin = login(other, "OldPassword123!", 200);
        now.set(now.get().plusSeconds(30));
        var response = change(access(first), "OldPassword123!", "NewPassword456!", 200);
        assertThat(response.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(org.springframework.mock.web.MockCookie.parse(response.getResponse().getHeader("Set-Cookie")).getMaxAge()).isZero();
        assertThat(body(response).has("data")).isFalse();
        User saved = users.findById(user.getId()).orElseThrow();
        assertThat(encoder.matches("NewPassword456!", saved.getPasswordHash())).isTrue();
        assertThat(Integer.parseInt(saved.getPasswordHash().substring(4, 6))).isGreaterThanOrEqualTo(10);
        assertThat(saved.getUpdatedAt()).isEqualTo(now.get());
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        for (var old : List.of(first, second)) {
            send(get("/api/v1/me").header("Authorization", "Bearer " + access(old)), 401);
            refresh(AuthenticationHttpTest.cookie(old, "fptpost_refresh"), 401);
        }
        send(get("/api/v1/me").header("Authorization", "Bearer " + access(otherLogin)), 200);
        login(user, "OldPassword123!", 401); login(user, "NewPassword456!", 200);
        assertThat(delivered).hasSize(1);
        assertThat(delivered.getFirst().getAllRecipients()[0].toString()).isEqualTo(user.getEmail());
        assertThat(delivered.getFirst().getSubject()).contains("Mật khẩu đã được thay đổi");
        assertThat(text(delivered.getFirst())).contains("Tất cả phiên đăng nhập cũ").doesNotContain("OldPassword123!", "NewPassword456!");
    }

    @Test void wrongCurrentPasswordDoesNotChangeCredentialsOrInvalidateTheCurrentSession() throws Exception {
        String token = access(login(user, "OldPassword123!", 200));
        var response = change(token, "WrongPassword123!", "NewPassword456!", 400);
        assertThat(body(response).path("code").asText()).isEqualTo("AUTH_CURRENT_PASSWORD_INCORRECT");
        assertThat(response.getResponse().getContentAsString()).doesNotContain("WrongPassword123!", "NewPassword456!");
        assertThat(encoder.matches("OldPassword123!", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        send(get("/api/v1/me").header("Authorization", "Bearer " + token), 200);
        assertThat(delivered).isEmpty();
    }

    @Test void rejectsAnonymousCookieOnlyExpiredAndRevokedSessions() throws Exception {
        var loggedIn = login(user, "OldPassword123!", 200); String token = access(loggedIn);
        change(null, "OldPassword123!", "NewPassword456!", 401);
        send(post("/api/v1/auth/password/change").cookie(AuthenticationHttpTest.cookie(loggedIn, "fptpost_refresh"))
                .contentType("application/json").content(json.writeValueAsBytes(Map.of("currentPassword", "OldPassword123!", "newPassword", "NewPassword456!"))), 401);
        sessions.findAll().forEach(session -> { session.setRevokedAt(now.get()); sessions.saveAndFlush(session); });
        change(token, "OldPassword123!", "NewPassword456!", 401);
        token = access(login(user, "OldPassword123!", 200)); now.set(now.get().plusSeconds(3600));
        change(token, "OldPassword123!", "NewPassword456!", 401);
        assertThat(delivered).isEmpty();
    }

    @Test void rejectsWeakUnchangedContactAndOversizedPasswordsWithoutRevokingSessions() throws Exception {
        String token = access(login(user, "OldPassword123!", 200));
        for (String password : List.of("short", "alllowercase123", "ALLUPPERCASE123", "NoDigitsHere",
                "Change" + sequence.get() + "@example.com", user.getPhone(), "Aa1" + "界".repeat(24))) {
            change(token, "OldPassword123!", password, 400);
        }
        assertThat(body(change(token, "OldPassword123!", "OldPassword123!", 400)).path("code").asText()).isEqualTo("AUTH_PASSWORD_UNCHANGED");
        assertThat(body(change(token, "Aa1" + "界".repeat(24), "NewPassword456!", 400)).path("code").asText()).isEqualTo("AUTH_CURRENT_PASSWORD_INCORRECT");
        send(get("/api/v1/me").header("Authorization", "Bearer " + token), 200);
        assertThat(delivered).isEmpty();
    }

    @Test void authenticatedUserCannotChooseAnotherAccountInTheRequest() throws Exception {
        var other = createUser("target" + sequence.get() + "@example.com", "+848" + String.format(Locale.ROOT, "%08d", sequence.get()));
        String token = access(login(user, "OldPassword123!", 200));
        send(post("/api/v1/auth/password/change").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("currentPassword", "OldPassword123!", "newPassword", "NewPassword456!",
                        "userId", other.getPublicId(), "identifier", other.getEmail()))), 200);
        assertThat(encoder.matches("OldPassword123!", users.findById(other.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(encoder.matches("NewPassword456!", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void invalidatesOutstandingPasswordResetOtpButPreservesRegistrationOtp() throws Exception {
        String token = access(login(user, "OldPassword123!", 200));
        for (OtpType type : List.of(OtpType.PASSWORD_RESET, OtpType.REGISTRATION)) {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> otps.saveAndFlush(OtpCode.builder()
                    .publicId(UUID.randomUUID()).user(users.findById(user.getId()).orElseThrow()).target(user.getEmail()).type(type)
                    .codeHash(encoder.encode("012345")).createdAt(now.get()).expiresAt(now.get().plusSeconds(300)).build()));
        }
        change(token, "OldPassword123!", "NewPassword456!", 200);
        assertThat(otps.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), OtpType.PASSWORD_RESET).orElseThrow().getExpiresAt()).isEqualTo(now.get());
        assertThat(otps.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), OtpType.REGISTRATION).orElseThrow().getExpiresAt()).isAfter(now.get());
        send(post("/api/v1/auth/password/reset").contentType("application/json").content(json.writeValueAsBytes(Map.of(
                "identifier", user.getEmail(), "otp", "012345", "newPassword", "AnotherPassword789!"))), 400);
    }

    @Test void mailFailureAfterCommitDoesNotUndoThePasswordChange() throws Exception {
        String token = access(login(user, "OldPassword123!", 200));
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(MimeMessage.class));
        change(token, "OldPassword123!", "NewPassword456!", 200);
        login(user, "NewPassword456!", 200);
        send(get("/api/v1/me").header("Authorization", "Bearer " + token), 401);
    }

    @Test void invalidSessionIsRecheckedInsideTheTransaction() throws Exception {
        login(user, "OldPassword123!", 200);
        UUID sessionId = sessions.findAll().getFirst().getPublicId();
        sessions.findAll().forEach(session -> { session.setRevokedAt(now.get()); sessions.saveAndFlush(session); });
        assertThatThrownBy(() -> passwordService.changePassword(user.getPublicId(), sessionId,
                ChangePasswordRequest.builder().currentPassword("OldPassword123!").newPassword("NewPassword456!").build()))
                .isInstanceOf(com.fptpost.common.exception.AppException.class);
        assertThat(encoder.matches("OldPassword123!", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(delivered).isEmpty();
    }

    @Test void simultaneousChangesCanOnlySucceedOnce() throws Exception {
        String token = access(login(user, "OldPassword123!", 200)); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/password/change").header("Authorization", "Bearer " + token)
                        .with(r -> { r.setRemoteAddr(ip); return r; }).contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("currentPassword", "OldPassword123!", "newPassword", "NewPassword456!"))))
                        .andReturn().getResponse().getStatus();
            }));
            start.countDown();
            assertThat(List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(delivered).hasSize(1);
    }

    @Test void validationFailuresCountTowardsTheSharedIpLimit() throws Exception {
        String token = access(login(user, "OldPassword123!", 200)); ip += "-malformed"; properties.setRequestsPerMinute(10);
        try {
            for (int i = 0; i < 10; i++) send(post("/api/v1/auth/password/change").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content(i == 0 ? "{}" : "{"), 400);
            var limited = send(post("/api/v1/auth/password/change").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content("{"), 429);
            assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo("60");
        } finally { properties.setRequestsPerMinute(1000); }
        var dto = ChangePasswordRequest.builder().currentPassword("OldPassword123!").newPassword("NewPassword456!").build();
        assertThat(dto.toString()).doesNotContain("OldPassword123!", "NewPassword456!");
    }
}

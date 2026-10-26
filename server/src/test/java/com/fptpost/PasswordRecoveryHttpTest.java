package com.fptpost;

import com.fasterxml.jackson.databind.*;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.service.OtpService;
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
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
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
class PasswordRecoveryHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RoleRepository roles;
    @Autowired AuthSessionRepository sessions;
    @Autowired OtpCodeRepository otps;
    @Autowired PasswordEncoder encoder;
    @Autowired RegistrationProperties properties;
    @Autowired OtpService otpService;
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
        ip = "198.51.100." + number;
        now.set(Instant.parse("2026-10-15T12:00:00Z"));
        when(identityClock.instant()).thenAnswer(invocation -> now.get());
        properties.setRequestsPerMinute(1000);
        reset(mailSender);
        delivered.clear();
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            message.saveChanges(); delivered.add(message); return null;
        }).when(mailSender).send(any(MimeMessage.class));
        sessions.deleteAll(); otps.deleteAll(); profiles.deleteAll(); users.deleteAll();
        user = createUser("recovery" + number + "@example.com", "+849" + String.format(Locale.ROOT, "%08d", number));
    }

    User createUser(String email, String phone) {
        return new TransactionTemplate(transactions).execute(transaction -> {
            User created = users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email(email).phone(phone)
                    .passwordHash(encoder.encode("OldPassword123!")).status(UserStatus.ACTIVE).emailVerifiedAt(now.get())
                    .createdAt(now.get()).updatedAt(now.get())
                    .roles(new HashSet<>(Set.of(roles.findByCode("SENDER").orElseThrow()))).build());
            profiles.saveAndFlush(UserProfile.builder().user(created).fullName("Người dùng khôi phục").adultConfirmedAt(now.get()).build());
            return created;
        });
    }
    MvcResult send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return mvc.perform(request.with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().is(expectedStatus)).andReturn();
    }
    MvcResult postJson(String path, Object payload, int status) throws Exception {
        return send(post("/api/v1/auth" + path).contentType("application/json").content(json.writeValueAsBytes(payload)), status);
    }
    MvcResult forgot(String identifier, int status) throws Exception { return postJson("/password/forgot", Map.of("identifier", identifier), status); }
    MvcResult resetPassword(String identifier, String code, String password, int status) throws Exception {
        return postJson("/password/reset", Map.of("identifier", identifier, "otp", code, "newPassword", password), status);
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    String text(Part part) throws Exception {
        if (part.isMimeType("text/plain")) return (String) part.getContent();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) { String value = text(multipart.getBodyPart(i)); if (value != null) return value; }
        }
        return null;
    }
    String code() throws Exception {
        var matcher = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)").matcher(text(delivered.getLast()));
        assertThat(matcher.find()).isTrue(); return matcher.group();
    }
    OtpCode latest() { return otps.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), OtpType.PASSWORD_RESET).orElseThrow(); }
    MvcResult login(User owner, String password, int status) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn();
        var data = body(csrf).path("data");
        return send(post("/api/v1/auth/login").cookie(AuthenticationHttpTest.cookie(csrf, "XSRF-TOKEN"))
                .header(data.path("headerName").asText(), data.path("token").asText())
                .contentType("application/json").content(json.writeValueAsBytes(Map.of("identifier", owner.getEmail(), "password", password))), status);
    }
    String access(MvcResult result) throws Exception { return body(result).path("data").path("accessToken").asText(); }
    MvcResult refresh(Cookie token, int status) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn(); var data = body(csrf).path("data");
        return send(post("/api/v1/auth/refresh").cookie(token, AuthenticationHttpTest.cookie(csrf, "XSRF-TOKEN"))
                .header(data.path("headerName").asText(), data.path("token").asText()), status);
    }

    @Test void knownUnknownAndDisabledAccountsReceiveIdenticalAcknowledgements() throws Exception {
        var known = body(forgot(user.getEmail(), 200));
        var unknown = body(forgot("unknown" + sequence.get() + "@example.com", 200));
        assertThat(known.path("data")).isEqualTo(unknown.path("data"));
        assertThat(known.path("message")).isEqualTo(unknown.path("message"));
        assertThat(known.path("data").fieldNames()).toIterable().containsExactly("resendAvailableAt");
        assertThat(known.toString()).doesNotContain(user.getEmail(), user.getPhone(), code(), "challengeId", "token");
        now.set(now.get().plusSeconds(60)); user.setStatus(UserStatus.LOCKED); users.saveAndFlush(user);
        var disabled = body(forgot(user.getEmail(), 200));
        assertThat(disabled.path("data")).isEqualTo(body(forgot("absent" + sequence.get() + "@example.com", 200)).path("data"));
        assertThat(delivered).hasSize(1);
    }

    @Test void phoneAliasesSendOnlyToTheRegisteredEmailAndHaveEqualCooldown() throws Exception {
        String national = "0" + user.getPhone().substring(3);
        var response = forgot(" " + national + " ", 200);
        assertThat(response.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(delivered.getFirst().getAllRecipients()[0].toString()).isEqualTo(user.getEmail());
        assertThat(delivered.getFirst().getSubject()).contains("Mã đặt lại mật khẩu");
        assertThat(latest().getCodeHash()).isNotEqualTo(code());
        assertThat(encoder.matches(code(), latest().getCodeHash())).isTrue();
        now.set(now.get().plusSeconds(59));
        assertThat(forgot(user.getPhone(), 429).getResponse().getHeader("Retry-After")).isEqualTo("1");
        now.set(now.get().plusSeconds(1)); forgot(user.getPhone(), 200);
        assertThat(delivered).hasSize(2);
    }

    @Test void unknownIdentifiersHaveTheSameResendCooldown() throws Exception {
        String unknown = "absent-cooldown" + sequence.get() + "@example.com";
        forgot(unknown, 200);
        assertThat(forgot(unknown.toUpperCase(Locale.ROOT), 429).getResponse().getHeader("Retry-After")).isEqualTo("60");
        assertThat(delivered).isEmpty();
    }

    @Test void resetRevokesEveryOldJwtAndRefreshTokenWithoutRevokingOtherAccounts() throws Exception {
        var first = login(user, "OldPassword123!", 200); var second = login(user, "OldPassword123!", 200);
        var other = createUser("other" + sequence.get() + "@example.com", "+848" + String.format(Locale.ROOT, "%08d", sequence.get()));
        var otherLogin = login(other, "OldPassword123!", 200);
        forgot(user.getEmail().toUpperCase(Locale.ROOT), 200);
        String otp = code(); var response = resetPassword(user.getEmail(), otp, "NewPassword456!", 200);
        assertThat(response.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(body(response).has("data")).isFalse();
        assertThat(latest().getUsedAt()).isEqualTo(now.get());
        assertThat(delivered).hasSize(2);
        assertThat(delivered.getLast().getSubject()).contains("Mật khẩu đã được thay đổi");
        assertThat(text(delivered.getLast())).doesNotContain(otp, "NewPassword456!");
        for (var old : List.of(first, second)) {
            send(get("/api/v1/me").header("Authorization", "Bearer " + access(old)), 401);
            refresh(AuthenticationHttpTest.cookie(old, "fptpost_refresh"), 401);
        }
        send(get("/api/v1/me").header("Authorization", "Bearer " + access(otherLogin)), 200);
        login(user, "OldPassword123!", 401); login(user, "NewPassword456!", 200);
        resetPassword(user.getEmail(), otp, "AnotherPassword789!", 400);
    }

    @Test void failedAttemptsPersistAndTheFifthAttemptDisablesEvenTheCorrectCode() throws Exception {
        forgot(user.getEmail(), 200); String actual = code(); String wrong = actual.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 5; i++) {
            assertThat(body(resetPassword(user.getEmail(), wrong, "NewPassword456!", 400)).path("code").asText()).isEqualTo("AUTH_PASSWORD_RESET_INVALID");
            assertThat(latest().getTries()).isEqualTo(i);
        }
        resetPassword(user.getEmail(), actual, "NewPassword456!", 400);
        assertThat(latest().getUsedAt()).isNull();
        now.set(now.get().plusSeconds(60)); forgot(user.getEmail(), 200);
        resetPassword(user.getEmail(), code(), "NewPassword456!", 200);
    }

    @Test void expiryAndUnknownAccountHaveTheSameErrorWithoutChangingCredentials() throws Exception {
        forgot(user.getEmail(), 200); String actual = code(); now.set(now.get().plusSeconds(300));
        var expired = body(resetPassword(user.getEmail(), actual, "NewPassword456!", 400));
        var missing = body(resetPassword("not-registered@example.com", actual, "NewPassword456!", 400));
        assertThat(expired.path("code")).isEqualTo(missing.path("code"));
        assertThat(expired.path("detail")).isEqualTo(missing.path("detail"));
        assertThat(encoder.matches("OldPassword123!", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void badPasswordPolicyRollsBackConsumptionSoTheSameOtpCanBeRetried() throws Exception {
        forgot(user.getEmail(), 200); String actual = code();
        String emailAsPassword = Character.toUpperCase(user.getEmail().charAt(0)) + user.getEmail().substring(1);
        for (String invalid : List.of("lowercase123", "UPPERCASE123", "OnlyLetters", emailAsPassword, "Aa1" + "界".repeat(24))) {
            assertThat(body(resetPassword(user.getEmail(), actual, invalid, 400)).path("code").asText()).isEqualTo("AUTH_PASSWORD_INVALID");
            assertThat(latest().getUsedAt()).isNull();
        }
        resetPassword(user.getEmail(), actual, "NewPassword456!", 200);
    }

    @Test void resendingInvalidatesTheOldRecordAndDoesNotAffectRegistrationOtps() throws Exception {
        OtpCode registration = otpService.generateAndSendOtp(user, "Test");
        forgot(user.getEmail(), 200); long oldId = latest().getId(); now.set(now.get().plusSeconds(60));
        forgot(user.getEmail(), 200);
        assertThat(latest().getId()).isNotEqualTo(oldId);
        assertThat(otps.findById(oldId).orElseThrow().getExpiresAt()).isEqualTo(now.get());
        assertThat(otps.findById(registration.getId()).orElseThrow().getExpiresAt()).isAfter(now.get());
        postJson("/otp/verify", Map.of("challengeId", latest().getPublicId(), "otp", code()), 404);
        resetPassword(user.getEmail(), code(), "NewPassword456!", 200);
    }

    @Test void registrationCodeAloneCannotResetAPassword() throws Exception {
        otpService.generateAndSendOtp(user, "Test");
        resetPassword(user.getEmail(), code(), "NewPassword456!", 400);
        assertThat(encoder.matches("OldPassword123!", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void deliveryFailureKeepsThePreviousOtpAndReturnsTheGenericAcknowledgement() throws Exception {
        forgot(user.getEmail(), 200); String original = code(); long oldId = latest().getId();
        now.set(now.get().plusSeconds(60));
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(MimeMessage.class));
        assertThat(body(forgot(user.getEmail(), 200)).path("data").fieldNames()).toIterable().containsExactly("resendAvailableAt");
        assertThat(latest().getId()).isEqualTo(oldId);
        assertThat(latest().getExpiresAt()).isAfter(now.get());
        resetPassword(user.getEmail(), original, "NewPassword456!", 200);
        assertThat(encoder.matches("NewPassword456!", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test void pendingAccountRecoveryDoesNotActivateTheAccountOrChangeItsRoles() throws Exception {
        user.setStatus(UserStatus.PENDING_VERIFICATION); user.setEmailVerifiedAt(null); users.saveAndFlush(user);
        forgot(user.getEmail(), 200); resetPassword(user.getEmail(), code(), "NewPassword456!", 200);
        User saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getEmailVerifiedAt()).isNull();
    }

    @Test void lockingOrDeletingAnAccountAfterIssuingAnOtpPreventsRecovery() throws Exception {
        forgot(user.getEmail(), 200); String actual = code(); user.setStatus(UserStatus.LOCKED); user = users.saveAndFlush(user);
        resetPassword(user.getEmail(), actual, "NewPassword456!", 400);
        user.setStatus(UserStatus.ACTIVE); user.setDeleted(true); user = users.saveAndFlush(user);
        resetPassword(user.getEmail(), actual, "NewPassword456!", 400);
        assertThat(latest().getUsedAt()).isNull();
    }

    @Test void simultaneousSubmissionsUseTheCodeOnlyOnce() throws Exception {
        forgot(user.getEmail(), 200); String actual = code(); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/password/reset").with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType("application/json").content(json.writeValueAsBytes(Map.of("identifier", user.getEmail(), "otp", actual,
                                "newPassword", "NewPassword456!")))).andReturn().getResponse().getStatus();
            }));
            start.countDown();
            assertThat(List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(delivered).hasSize(2);
    }

    @Test void malformedRecoveryRequestsAreAlsoSubjectToTheSharedIpLimit() throws Exception {
        properties.setRequestsPerMinute(10);
        try {
            for (String path : List.of("forgot", "reset")) {
                ip += "-" + path;
                for (int i = 0; i < 10; i++) send(post("/api/v1/auth/password/" + path).contentType("application/json").content("{"), 400);
                var limited = send(post("/api/v1/auth/password/" + path).contentType("application/json").content("{"), 429);
                assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo("60");
            }
        } finally { properties.setRequestsPerMinute(1000); }
    }
}

package com.fptpost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.enums.OtpType;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.repository.*;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:otp;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.mongodb.auto-index-creation=false", "app.registration.requests-per-minute=1000",
        "management.health.mail.enabled=false"
})
@AutoConfigureMockMvc
class OtpHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserProfileRepository profiles;
    @Autowired ConsentRepository consents;
    @Autowired OtpCodeRepository otps;
    @Autowired BlacklistRepository blacklist;
    @Autowired RegistrationProperties properties;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender mailSender;
    @MockitoBean Clock identityClock;
    final AtomicReference<Instant> now = new AtomicReference<>();
    final AtomicReference<String> deliveredCode = new AtomicReference<>();
    UUID challengeId;

    @BeforeEach
    void clean() throws Exception {
        now.set(Instant.parse("2026-10-11T09:00:00Z"));
        when(identityClock.instant()).thenAnswer(invocation -> now.get());
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(
                invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            message.saveChanges();
            var matcher = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)").matcher(plainText(message));
            assertThat(matcher.find()).isTrue();
            deliveredCode.set(matcher.group());
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
        blacklist.deleteAll();
        otps.deleteAll();
        consents.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        if (roles.findByCode("SENDER").isEmpty()) roles.save(Role.builder().code("SENDER").build());
        var payload = Map.of("fullName", "Test OTP", "email", "otp@example.com", "phone", "0901234567",
                "password", "Secret123!", "adultConfirmed", true, "termsAccepted", true, "privacyAccepted", true,
                "termsVersion", properties.getTermsVersion(), "privacyVersion", properties.getPrivacyVersion());
        var response = body(send("/register", payload, 201));
        challengeId = UUID.fromString(response.path("data").path("challengeId").asText());
        assertThat(response.path("data").path("resendAvailableAt").asText())
                .isEqualTo(now.get().plusSeconds(60).toString());
    }

    MvcResult send(String path, Object payload, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/v1/auth" + path).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    MvcResult verify(UUID challenge, String code, int status) throws Exception {
        return send("/otp/verify", Map.of("challengeId", challenge, "otp", code), status);
    }

    String wrongCode() { return "000000".equals(deliveredCode.get()) ? "111111" : "000000"; }

    @Test
    void verifiesDeliveredEmailOnceAndActivatesUserWithoutReturningSecretsOrTokens() throws Exception {
        String correct = deliveredCode.get();
        var result = verify(challengeId, correct, 200);
        assertThat(body(result).path("data").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(body(result).path("data").path("verificationChannel").asText()).isEqualTo("EMAIL");
        assertThat(body(result).path("data").path("verifiedAt").asText()).isEqualTo(now.get().toString());
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(correct, "password", "codeHash", "accessToken");
        var user = users.findAll().getFirst();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(now.get());
        assertThat(user.getPhoneVerifiedAt()).isNull();
        assertThat(otps.findAll().getFirst().getUsedAt()).isEqualTo(now.get());
        assertThat(otps.findAll().getFirst().getType()).isEqualTo(OtpType.REGISTRATION);
        assertThat(body(verify(challengeId, correct, 409)).path("code").asText()).isEqualTo("AUTH_OTP_ALREADY_USED");
        assertThat(body(send("/otp/resend", Map.of("challengeId", challengeId), 403)).path("code").asText())
                .isEqualTo("AUTH_VERIFICATION_BLOCKED");
    }

    @Test
    void commitsEachWrongAttemptAndRejectsCorrectCodeAfterTheFifthFailure() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            var response = body(verify(challengeId, wrongCode(), 400));
            assertThat(response.path("code").asText()).isEqualTo(attempt == 5
                    ? "AUTH_OTP_ATTEMPTS_EXCEEDED" : "AUTH_OTP_INVALID");
            assertThat(otps.findAll().getFirst().getTries()).isEqualTo(attempt);
        }
        var result = verify(challengeId, deliveredCode.get(), 400);
        assertThat(body(result).path("code").asText()).isEqualTo("AUTH_OTP_ATTEMPTS_EXCEEDED");
        assertThat(otps.findAll().getFirst().getTries()).isEqualTo(5);
        assertThat(users.findAll().getFirst().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    }

    @Test
    void expirationIsEnforcedAtTheExactBoundary() throws Exception {
        now.set(now.get().plusSeconds(300));
        assertThat(body(verify(challengeId, deliveredCode.get(), 410)).path("code").asText()).isEqualTo("AUTH_OTP_EXPIRED");
        assertThat(otps.findAll().getFirst().getTries()).isZero();
    }

    @Test
    void rejectsMalformedInputUnknownIdsAndOtherPurposesWithoutCountingAttempts() throws Exception {
        verify(challengeId, "12", 400);
        verify(challengeId, "abcdef", 400);
        send("/otp/verify", Map.of("otp", "123456"), 400);
        assertThat(body(verify(UUID.randomUUID(), "123456", 404)).path("code").asText()).isEqualTo("AUTH_OTP_NOT_FOUND");
        assertThat(otps.findAll().getFirst().getTries()).isZero();
        var otp = otps.findAll().getFirst();
        for (OtpType type : new OtpType[]{OtpType.PASSWORD_RESET, OtpType.EMAIL_CHANGE}) {
            otp.setType(type);
            otp = otps.saveAndFlush(otp);
            assertThat(body(verify(challengeId, deliveredCode.get(), 404)).path("code").asText()).isEqualTo("AUTH_OTP_NOT_FOUND");
            send("/otp/resend", Map.of("challengeId", challengeId), 404);
            assertThat(otps.findByPublicIdAndType(challengeId, type).orElseThrow().getTries()).isZero();
        }
    }

    @Test
    void resendUsesServerCooldownReplacesChallengeAndPreventsBypassWithOldId() throws Exception {
        now.set(now.get().plusMillis(59999));
        var tooSoon = send("/otp/resend", Map.of("challengeId", challengeId), 429);
        assertThat(tooSoon.getResponse().getHeader("Retry-After")).isEqualTo("1");
        assertThat(tooSoon.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(body(tooSoon).path("code").asText()).isEqualTo("AUTH_OTP_RESEND_TOO_SOON");
        String oldCode = deliveredCode.get();
        now.set(now.get().plusMillis(1));
        var resent = body(send("/otp/resend", Map.of("challengeId", challengeId), 200)).path("data");
        UUID replacementId = UUID.fromString(resent.path("challengeId").asText());
        assertThat(replacementId).isNotEqualTo(challengeId);
        assertThat(resent.path("resendAvailableAt").asText()).isEqualTo(now.get().plusSeconds(60).toString());
        assertThat(resent.path("maskedTarget").asText()).isEqualTo("o***@example.com");
        assertThat(body(verify(challengeId, oldCode, 409)).path("code").asText()).isEqualTo("AUTH_OTP_SUPERSEDED");
        assertThat(send("/otp/resend", Map.of("challengeId", challengeId), 429).getResponse().getHeader("Retry-After"))
                .isEqualTo("60");
        verify(replacementId, deliveredCode.get(), 200);
        org.mockito.Mockito.verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void failedResendRollsBackInvalidationAndKeepsOldCodeUsable() throws Exception {
        String oldCode = deliveredCode.get();
        Instant oldExpiry = otps.findAll().getFirst().getExpiresAt();
        now.set(now.get().plusSeconds(60));
        doThrow(new MailSendException("private smtp details")).when(mailSender).send(any(MimeMessage.class));
        var failure = send("/otp/resend", Map.of("challengeId", challengeId), 503);
        assertThat(body(failure).path("code").asText()).isEqualTo("AUTH_OTP_DELIVERY_FAILED");
        assertThat(failure.getResponse().getContentAsString()).doesNotContain("private smtp details");
        assertThat(otps.count()).isEqualTo(1);
        assertThat(otps.findAll().getFirst().getExpiresAt()).isEqualTo(oldExpiry);
        verify(challengeId, oldCode, 200);
    }

    @Test
    void newChallengeRestoresAttemptsAfterExpiredOrExhaustedCode() throws Exception {
        for (int i = 0; i < 5; i++) verify(challengeId, wrongCode(), 400);
        now.set(now.get().plusSeconds(301));
        var replacement = body(send("/otp/resend", Map.of("challengeId", challengeId), 200)).path("data");
        UUID replacementId = UUID.fromString(replacement.path("challengeId").asText());
        assertThat(otps.findByPublicIdAndType(replacementId, OtpType.REGISTRATION).orElseThrow().getTries()).isZero();
        verify(replacementId, deliveredCode.get(), 200);
    }

    @Test
    void lockedOrDeletedAccountsCannotBeActivatedOrReceiveAnotherCode() throws Exception {
        var user = users.findAll().getFirst();
        user.setStatus(UserStatus.LOCKED);
        users.saveAndFlush(user);
        verify(challengeId, deliveredCode.get(), 403);
        send("/otp/resend", Map.of("challengeId", challengeId), 403);
        user = users.findAll().getFirst();
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setDeleted(true);
        users.saveAndFlush(user);
        verify(challengeId, deliveredCode.get(), 403);
        assertThat(otps.findAll().getFirst().getUsedAt()).isNull();
        org.mockito.Mockito.verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void simultaneousCorrectVerificationsConsumeCodeOnlyOnce() throws Exception {
        String correct = deliveredCode.get();
        var results = parallel(2, () -> mvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("challengeId", challengeId, "otp", correct))))
                .andReturn().getResponse().getStatus());
        assertThat(results).containsExactlyInAnyOrder(200, 409);
        assertThat(users.findAll().getFirst().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void simultaneousWrongAttemptsCannotLoseTheCounter() throws Exception {
        String wrong = wrongCode();
        assertThat(parallel(5, () -> verify(challengeId, wrong, 400).getResponse().getStatus()))
                .containsOnly(400);
        assertThat(otps.findAll().getFirst().getTries()).isEqualTo(5);
    }

    @Test
    void simultaneousResendsSendOnlyOneReplacement() throws Exception {
        now.set(now.get().plusSeconds(60));
        var results = parallel(2, () -> mvc.perform(post("/api/v1/auth/otp/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("challengeId", challengeId))))
                .andReturn().getResponse().getStatus());
        assertThat(results).containsExactlyInAnyOrder(200, 429);
        assertThat(otps.count()).isEqualTo(2);
        org.mockito.Mockito.verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void otpEndpointsCountMalformedRequestsInTheIpLimit() throws Exception {
        properties.setRequestsPerMinute(2);
        try {
            for (String path : java.util.List.of("/otp/verify", "/otp/resend")) {
                String ip = path.endsWith("verify") ? "198.51.100.51" : "198.51.100.52";
                for (int i = 0; i < 2; i++) {
                    mvc.perform(post("/api/v1/auth" + path).contentType(MediaType.APPLICATION_JSON)
                                    .content("{bad").with(request -> { request.setRemoteAddr(ip); return request; }))
                            .andExpect(status().isBadRequest());
                }
                mvc.perform(post("/api/v1/auth" + path).contentType(MediaType.APPLICATION_JSON)
                                .content("{bad").with(request -> { request.setRemoteAddr(ip); return request; }))
                        .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
            }
        } finally { properties.setRequestsPerMinute(1000); }
    }

    private java.util.List<Integer> parallel(int count, java.util.concurrent.Callable<Integer> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < count; i++) tasks.add(executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test");
                return call.call();
            }));
            start.countDown();
            var results = new java.util.ArrayList<Integer>();
            for (var task : tasks) results.add(task.get(30, TimeUnit.SECONDS));
            return results;
        }
    }

    private String plainText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) return (String) part.getContent();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = plainText(multipart.getBodyPart(i));
                if (text != null) return text;
            }
        }
        return null;
    }
}

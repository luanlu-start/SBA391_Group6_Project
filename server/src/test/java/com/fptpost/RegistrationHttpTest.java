package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.identity.config.IdentityDataInitializer;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.util.ContactUtils;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.mongodb.auto-index-creation=false", "app.registration.requests-per-minute=1000",
        "management.health.mail.enabled=false"
})
@AutoConfigureMockMvc
class RegistrationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CourierProfileRepository courierProfiles;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserProfileRepository profiles;
    @Autowired ConsentRepository consents;
    @Autowired OtpCodeRepository otps;
    @Autowired BlacklistRepository blacklist;
    @Autowired PasswordEncoder encoder;
    @Autowired RegistrationProperties properties;
    @Autowired IdentityDataInitializer initializer;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void clean() {
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(
                invocation -> new MimeMessage(Session.getInstance(new Properties())));
        blacklist.deleteAll();
        otps.deleteAll();
        consents.deleteAll();
        courierProfiles.deleteAll(); profiles.deleteAll();
        users.deleteAll();
        initializer.run();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-email", " "})
    void rejectsInvalidEmailAtRequestAndEntityBoundaries(String email) throws Exception {
        var payload = request();
        payload.put("email", email);
        register(payload, 400);
        verifyNoInteractions(mailSender);

        Instant now = Instant.now();
        User user = User.builder().publicId(UUID.randomUUID()).email(email).phone("+84901234567")
                .passwordHash("unused-for-validation-test").status(UserStatus.PENDING_VERIFICATION)
                .createdAt(now).updatedAt(now).build();
        assertThatThrownBy(() -> users.saveAndFlush(user))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThat(users.count()).isZero();
    }

    Map<String, Object> request() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("fullName", " Nguyễn Văn An ");
        request.put("email", " Person@Example.com ");
        request.put("phone", "0901234567");
        request.put("password", "Secret123!");
        request.put("adultConfirmed", true);
        request.put("termsAccepted", true);
        request.put("privacyAccepted", true);
        request.put("termsVersion", properties.getTermsVersion());
        request.put("privacyVersion", properties.getPrivacyVersion());
        return request;
    }

    String register(Map<String, Object> request, int status) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void initializesRequiredRolesWithoutDemoAndRestoresOnlyMissingRole() {
        var existing = roles.findAll().stream().collect(java.util.stream.Collectors.toMap(Role::getCode, Role::getId));
        assertThat(existing.keySet()).containsExactlyInAnyOrder("SENDER", "COURIER", "MODERATOR", "ADMIN");
        initializer.run();
        assertThat(roles.findAll()).allSatisfy(role -> assertThat(role.getId()).isEqualTo(existing.get(role.getCode())));
        roles.deleteById(existing.get("COURIER"));
        initializer.run();
        assertThat(roles.count()).isEqualTo(4);
        assertThat(roles.findByCode("COURIER")).isPresent();
        assertThat(roles.findByCode("SENDER").orElseThrow().getId()).isEqualTo(existing.get("SENDER"));
    }

    @Test
    void createsPendingUserProfileConsentsAndHashedOtpWithoutLeakingSecrets() throws Exception {
        Instant before = Instant.now();
        String body = register(request(), 201);
        var response = json.readTree(body);
        assertThat(response.path("status").asInt()).isEqualTo(201);
        assertThat(response.path("code").asInt()).isEqualTo(1000);
        assertThat(response.path("data").path("status").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(response.path("data").path("userId").asText()).hasSize(36);
        assertThat(body).doesNotContain("Secret123!", "passwordHash", "codeHash", "person@example.com", "0901234567");
        var user = users.findAll().getFirst();
        assertThat(user.getEmail()).isEqualTo("person@example.com");
        assertThat(user.getPhone()).isEqualTo("+84901234567");
        assertThat(user.getPasswordHash()).startsWith("$2a$12$");
        assertThat(encoder.matches("Secret123!", user.getPasswordHash())).isTrue();
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getPhoneVerifiedAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?",
                String.class, user.getId())).isEqualTo("SENDER");
        assertThat(profiles.findAll().getFirst().getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(consents.findAll()).hasSize(2).allSatisfy(c -> {
            assertThat(c.getAcceptedAt()).isAfterOrEqualTo(before);
            assertThat(c.getIp()).isEqualTo("127.0.0.1");
        });
        var captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        captor.getValue().saveChanges();
        String mail = plainText(captor.getValue());
        assertThat(mail).contains("Nguyễn Văn An", "Mã có hiệu lực 5 phút");
        var matcher = java.util.regex.Pattern.compile("(?<!\\d)\\d{6}(?!\\d)").matcher(mail);
        assertThat(matcher.find()).isTrue();
        String code = matcher.group();
        var otp = otps.findAll().getFirst();
        assertThat(encoder.matches(code, otp.getCodeHash())).isTrue();
        assertThat(java.time.Duration.between(otp.getCreatedAt(), otp.getExpiresAt()).getSeconds()).isEqualTo(300);
        assertThat(body).doesNotContain(code);
        var roleCount = mvc.perform(get("/api/v1/auth/registration-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.terms.version").value(properties.getTermsVersion()))
                .andExpect(jsonPath("$.data.privacy.version").value(properties.getPrivacyVersion()))
                .andExpect(jsonPath("$.data.terms.title").value("Điều khoản sử dụng"))
                .andExpect(jsonPath("$.data.privacy.title").value("Chính sách dữ liệu cá nhân"))
                .andExpect(jsonPath("$.data.terms.paragraphs").isNotEmpty())
                .andExpect(jsonPath("$.data.privacy.paragraphs").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordMinLength").value(properties.getPasswordMinLength()));
        assertThat(roleCount).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"adultConfirmed", "termsAccepted", "privacyAccepted"})
    void rejectsMissingOrFalseConsent(String field) throws Exception {
        String code = switch (field) {
            case "adultConfirmed" -> "ADULT_CONFIRMATION_REQUIRED";
            case "termsAccepted" -> "TERMS_NOT_ACCEPTED";
            default -> "PRIVACY_NOT_ACCEPTED";
        };
        var request = request(); request.put(field, false);
        assertThat(register(request, 400)).contains(field, code);
        request.remove(field);
        assertThat(register(request, 400)).contains(field, code);
        assertThat(users.count()).isZero(); verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1A", "lowercase123", "UPPERCASE123", "NoDigitsHere", "Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1Áb1"})
    void rejectsEachPasswordViolationBeforeSaving(String password) throws Exception {
        var request = request(); request.put("password", password);
        register(request, 400);
        assertThat(users.count()).isZero(); verifyNoInteractions(mailSender);
    }

    @Test
    void rejectsStalePolicyVersionAndMalformedContact() throws Exception {
        var request = request(); request.put("termsVersion", "old");
        assertThat(register(request, 409)).contains("AUTH_POLICY_CHANGED");
        request = request(); request.put("phone", "not-a-phone"); register(request, 400);
        request = request(); request.put("email", "not-email"); register(request, 400);
        assertThat(users.count()).isZero(); verifyNoInteractions(mailSender);
    }

    @Test
    void treatsCaseAndVietnamesePhoneFormatsAsSameAccount() throws Exception {
        register(request(), 201);
        var duplicateEmail = request(); duplicateEmail.put("phone", "0901234568");
        assertThat(register(duplicateEmail, 409)).contains("AUTH_EMAIL_ALREADY_EXISTS");
        var duplicatePhone = request(); duplicatePhone.put("email", "other@example.com");
        duplicatePhone.put("phone", "+84901234567");
        assertThat(register(duplicatePhone, 409)).contains("AUTH_PHONE_ALREADY_EXISTS");
        assertThat(users.count()).isEqualTo(1);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void blocksBlacklistedPhoneBeforeCreatingAccount() throws Exception {
        var entry = new com.fptpost.modules.identity.model.BlacklistEntry();
        entry.setKind("PHONE"); entry.setValueHash(ContactUtils.phoneHash("+84901234567"));
        entry.setReason("Test block"); entry.setCreatedAt(Instant.now()); blacklist.save(entry);
        assertThat(register(request(), 403)).contains("AUTH_REGISTRATION_BLOCKED");
        assertThat(users.count()).isZero(); verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailureRollsBackAllRegistrationDataAndAllowsRetry() throws Exception {
        doThrow(new MailSendException("secret recipient details")).when(mailSender).send(any(MimeMessage.class));
        String body = register(request(), 503);
        assertThat(body).contains("AUTH_OTP_DELIVERY_FAILED").doesNotContain("secret recipient details");
        assertThat(users.count()).isZero(); assertThat(profiles.count()).isZero();
        assertThat(consents.count()).isZero(); assertThat(otps.count()).isZero();
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(
                invocation -> new MimeMessage(Session.getInstance(new Properties())));
        register(request(), 201);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void malformedJsonDoesNotExposeInternalsOrCreateUser() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_INVALID_REQUEST"));
        assertThat(users.count()).isZero(); verifyNoInteractions(mailSender);
    }

    @Test
    void rateLimitsMalformedRequestsAndIgnoresSpoofedForwardedHeaders() throws Exception {
        properties.setRequestsPerMinute(2);
        try {
            for (int i = 0; i < 2; i++) {
                mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{bad")
                                .with(r -> { r.setRemoteAddr("198.51.100.5"); return r; }))
                        .andExpect(status().isBadRequest());
            }
            mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{bad")
                            .header("X-Forwarded-For", "203.0.113.1")
                            .with(r -> { r.setRemoteAddr("198.51.100.5"); return r; }))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                    .andExpect(header().string("Retry-After", "60"));
        } finally { properties.setRequestsPerMinute(1000); }
    }

    private String plainText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = plainText(multipart.getBodyPart(i));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    @Test
    void returnsProblemDetailsAndPreservesCorrelationWithoutLeakingRejectedValues() throws Exception {
        String id = "7f3c9a52-1d4e-4b8a-9c21-0a6e5d3f7b10";
        var payload = request();
        payload.put("email", "private-not-an-email");
        var result = mvc.perform(post("/api/v1/auth/register").header("X-Correlation-Id", id)
                        .contentType("application/json").content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("X-Correlation-Id", id))
                .andExpect(jsonPath("$.correlationId").value(id))
                .andExpect(jsonPath("$.code").value("SYS_INVALID_REQUEST"))
                .andExpect(jsonPath("$.type").value("https://fptpost.vn/errors/sys-invalid-request"))
                .andExpect(jsonPath("$.title").value("Yêu cầu không hợp lệ"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value(org.hamcrest.Matchers.hasItem("EMAIL_INVALID")))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getHeaders("X-Correlation-Id")).containsExactly(id);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-not-an-email", "Secret123!");
        assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    }

    @Test
    void generatesNewCorrelationForMalformedHeaderAndIndependentRequests() throws Exception {
        var first = mvc.perform(get("/api/v1/auth/registration-policies")
                        .header("X-Correlation-Id", "unsafe private supplied value"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String id = first.getHeader("X-Correlation-Id");
        assertThat(java.util.UUID.fromString(id).toString()).isEqualTo(id);
        var second = mvc.perform(get("/api/v1/auth/registration-policies")).andReturn().getResponse();
        assertThat(second.getHeader("X-Correlation-Id")).isNotEqualTo(id);
        assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    }

    @Test
    void mvcFailuresUseSameProblemContractAndPreserveProtocolHeaders() throws Exception {
        mvc.perform(get("/api/v1/auth/register"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(jsonPath("$.code").value("SYS_METHOD_NOT_ALLOWED"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(post("/api/v1/auth/register").contentType("text/plain").content("private-value"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("SYS_UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/api/v1/missing-endpoint"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SYS_UNAUTHORIZED"));
        mvc.perform(get("/api/v1/products/not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SYS_UNAUTHORIZED"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void correlationHeaderIsAllowedAndExposedAcrossCors() throws Exception {
        mvc.perform(options("/api/v1/auth/register").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("x-correlation-id")))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("X-Correlation-Id")));
        mvc.perform(options("/api/v1/auth/register").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SYS_FORBIDDEN"));
    }
}

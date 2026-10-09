package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.service.RegistrationContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.data.mongodb.auto-index-creation=false", "app.registration.requests-per-minute=1000",
        "management.health.mail.enabled=false"
})
@AutoConfigureMockMvc
class RegistrationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserProfileRepository profiles;
    @Autowired ConsentRepository consents;
    @Autowired OtpCodeRepository otps;
    @Autowired BlacklistRepository blacklist;
    @Autowired PasswordEncoder encoder;
    @Autowired RegistrationProperties properties;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void clean() {
        reset(mailSender);
        blacklist.deleteAll();
        otps.deleteAll();
        consents.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        if (roles.findByCode("SENDER").isEmpty()) {
            Role role = new Role(); role.setCode("SENDER"); roles.save(role);
        }
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
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String mail = captor.getValue().getText();
        var matcher = java.util.regex.Pattern.compile("(?<!\\d)\\d{6}(?!\\d)").matcher(mail);
        assertThat(matcher.find()).isTrue();
        String code = matcher.group();
        var otp = otps.findAll().getFirst();
        assertThat(encoder.matches(code, otp.getCodeHash())).isTrue();
        assertThat(java.time.Duration.between(otp.getCreatedAt(), otp.getExpiresAt()).getSeconds()).isEqualTo(300);
        assertThat(body).doesNotContain(code);
        var roleCount = mvc.perform(get("/api/v1/auth/registration-policies"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.terms.version").value(properties.getTermsVersion()));
        assertThat(roleCount).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"adultConfirmed", "termsAccepted", "privacyAccepted"})
    void rejectsMissingOrFalseConsent(String field) throws Exception {
        var request = request(); request.put(field, false);
        assertThat(register(request, 400)).contains(field);
        request.remove(field);
        assertThat(register(request, 400)).contains(field);
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
        assertThat(register(request, 409)).contains("3004");
        request = request(); request.put("phone", "not-a-phone"); register(request, 400);
        request = request(); request.put("email", "not-email"); register(request, 400);
        assertThat(users.count()).isZero(); verifyNoInteractions(mailSender);
    }

    @Test
    void treatsCaseAndVietnamesePhoneFormatsAsSameAccount() throws Exception {
        register(request(), 201);
        var duplicateEmail = request(); duplicateEmail.put("phone", "0901234568");
        assertThat(register(duplicateEmail, 409)).contains("3001");
        var duplicatePhone = request(); duplicatePhone.put("email", "other@example.com");
        duplicatePhone.put("phone", "+84901234567");
        assertThat(register(duplicatePhone, 409)).contains("3002");
        assertThat(users.count()).isEqualTo(1);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void blocksBlacklistedPhoneBeforeCreatingAccount() throws Exception {
        var entry = new com.fptpost.modules.identity.model.BlacklistEntry();
        entry.setKind("PHONE"); entry.setValueHash(RegistrationContact.phoneHash("+84901234567"));
        entry.setReason("Test block"); entry.setCreatedAt(Instant.now()); blacklist.save(entry);
        assertThat(register(request(), 403)).contains("3005");
        assertThat(users.count()).isZero(); verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailureRollsBackAllRegistrationDataAndAllowsRetry() throws Exception {
        doThrow(new MailSendException("secret recipient details")).when(mailSender).send(any(SimpleMailMessage.class));
        String body = register(request(), 503);
        assertThat(body).contains("3006").doesNotContain("secret recipient details");
        assertThat(users.count()).isZero(); assertThat(profiles.count()).isZero();
        assertThat(consents.count()).isZero(); assertThat(otps.count()).isZero();
        reset(mailSender);
        register(request(), 201);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void malformedJsonDoesNotExposeInternalsOrCreateUser() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1002));
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
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(3008))
                    .andExpect(header().string("Retry-After", "60"));
        } finally { properties.setRequestsPerMinute(1000); }
    }
}

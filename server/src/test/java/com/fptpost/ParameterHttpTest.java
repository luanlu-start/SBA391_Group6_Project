package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.catalog.repository.ParameterHistoryRepository;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:parameterhttp;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.mongodb.auto-index-creation=false",
        "audit.outbox.enabled=false", "management.health.mail.enabled=false"})
@AutoConfigureMockMvc
class ParameterHttpTest {
    @Autowired MockMvc mvc;
    @Autowired SystemParameterRepository parameters;
    @Autowired ParameterHistoryRepository history;
    @Autowired AuditOutboxRepository outbox;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JavaMailSender mailSender;
    long version;

    @BeforeEach void setup() {
        when(decoder.decode("admin-token")).thenReturn(jwt("admin-token", "ADMIN"));
        when(decoder.decode("sender-token")).thenReturn(jwt("sender-token", "SENDER"));
        history.deleteAll(); outbox.deleteAll();
        var parameter = parameters.findByKey("BASE_FARE").orElseThrow();
        parameter.setValue("10000");
        version = parameters.saveAndFlush(parameter).getVersion();
    }

    @Test void exposesCatalogUpdateHistoryAndConflictUsingTheStableApiContract() throws Exception {
        mvc.perform(get("/api/v1/admin/params").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.length()").value(109))
                .andExpect(jsonPath("$.data[0].prmId").value("PRM-001"));
        String request = "{\"value\":\"12000\",\"expectedVersion\":" + version + "}";
        mvc.perform(put("/api/v1/admin/params/BASE_FARE").header("Authorization", "Bearer admin-token")
                        .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.key").value("BASE_FARE"))
                .andExpect(jsonPath("$.data.value").value("12000"))
                .andExpect(jsonPath("$.data.version").value(version + 1));
        mvc.perform(get("/api/v1/admin/params/BASE_FARE/history").header("Authorization", "Bearer admin-token")
                        .param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].oldValue").value("10000"))
                .andExpect(jsonPath("$.data.content[0].newValue").value("12000"));
        mvc.perform(put("/api/v1/admin/params/BASE_FARE").header("Authorization", "Bearer admin-token")
                        .contentType("application/json").content(request))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PARAMETER_VERSION_CONFLICT"));
        assertThat(history.count()).isEqualTo(1); assertThat(outbox.count()).isEqualTo(1);
    }

    @Test void rejectsAnonymousAndNonAdminCatalogAndMutationRequests() throws Exception {
        mvc.perform(get("/api/v1/admin/params")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SYS_UNAUTHORIZED"));
        mvc.perform(get("/api/v1/admin/params").header("Authorization", "Bearer sender-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SYS_FORBIDDEN"));
        mvc.perform(get("/api/v1/admin/params/BASE_FARE/history").header("Authorization", "Bearer sender-token"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/params/BASE_FARE").header("Authorization", "Bearer sender-token")
                        .contentType("application/json").content("{\"value\":\"12000\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isForbidden());
        assertThat(history.count()).isZero(); assertThat(outbox.count()).isZero();
    }

    @Test void rejectsInvalidBodyPaginationUnknownKeysAndCrossFieldChanges() throws Exception {
        mvc.perform(put("/api/v1/admin/params/BASE_FARE").header("Authorization", "Bearer admin-token")
                        .contentType("application/json").content("{\"value\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_INVALID_REQUEST"));
        mvc.perform(get("/api/v1/admin/params/BASE_FARE/history").header("Authorization", "Bearer admin-token")
                        .param("page", "-1").param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/params/UNKNOWN_KEY").header("Authorization", "Bearer admin-token")
                        .contentType("application/json").content("{\"value\":\"1\",\"expectedVersion\":0}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PARAMETER_NOT_FOUND"));
        var sizeVersion = parameters.findByKey("SIZE_S").orElseThrow().getVersion();
        mvc.perform(put("/api/v1/admin/params/SIZE_S").header("Authorization", "Bearer admin-token")
                        .contentType("application/json").content("{\"value\":\"60\",\"expectedVersion\":" + sizeVersion + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETER_CONSTRAINT_VIOLATION"));
        mvc.perform(put("/api/v1/admin/params/batch").header("Authorization", "Bearer admin-token")
                        .contentType("application/json").content("{\"changes\":{\"BASE_FARE\":null}}"))
                .andExpect(status().isBadRequest());
        assertThat(history.count()).isZero(); assertThat(outbox.count()).isZero();
    }

    private static Jwt jwt(String token, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token).header("alg", "HS256")
                .subject("a914bf06-62cd-43a3-9672-982d40a32f6a")
                .claim("roles", List.of(role)).issuedAt(now).expiresAt(now.plusSeconds(900)).build();
    }
}

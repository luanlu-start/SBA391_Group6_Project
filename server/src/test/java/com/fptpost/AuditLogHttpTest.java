package com.fptpost;

import com.fptpost.common.audit.MongoAuditDocument;
import com.fptpost.modules.audit.dto.request.AuditLogSearchRequest;
import com.fptpost.modules.audit.repository.AuditLogQueryRepository;
import com.fptpost.modules.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audithttp;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.mongodb.auto-index-creation=false",
        "audit.outbox.enabled=false", "audit.retention.enabled=false", "audit.indexes.enabled=false",
        "management.health.mail.enabled=false"})
@AutoConfigureMockMvc
class AuditLogHttpTest {
    private static final String URL = "/api/v1/admin/audit-logs";
    private static final String ACTOR = "a914bf06-62cd-43a3-9672-982d40a32f6a";
    @Autowired MockMvc mvc;
    @Autowired AuditLogService service;
    @MockitoBean AuditLogQueryRepository queryRepository;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        when(decoder.decode("admin-token")).thenReturn(jwt("admin-token", "ADMIN"));
        when(decoder.decode("sender-token")).thenReturn(jwt("sender-token", "SENDER"));
        when(decoder.decode("courier-token")).thenReturn(jwt("courier-token", "COURIER"));
        var document = new MongoAuditDocument("internal", UUID.randomUUID().toString(), "USER", ACTOR,
                "PARAMETER_UPDATED", "system_parameters", "BASE_FARE", Map.of("password", "old-secret", "value", "10000"),
                Map.of("value", "12000"), "127.0.0.1", "http-audit", Instant.parse("2026-10-14T12:00:00Z"));
        when(queryRepository.find(any())).thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));
    }

    @Test void adminReceivesStablePageContractAndMaskedBeforeAfterWithNoStore() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer admin-token").param("actor", ACTOR)
                        .param("action", "PARAMETER_UPDATED").param("entityType", "system_parameters").param("entityId", "BASE_FARE")
                        .param("from", "2026-10-14T00:00:00Z").param("to", "2026-10-15T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].actorPublicId").value(ACTOR))
                .andExpect(jsonPath("$.data.content[0].before.password").value("[REDACTED]"))
                .andExpect(jsonPath("$.data.content[0].before.value").value("10000"))
                .andExpect(jsonPath("$.data.content[0].after.value").value("12000"))
                .andExpect(jsonPath("$.data.content[0].ip").value("127.0.0.1"))
                .andExpect(jsonPath("$.data.content[0].correlationId").value("http-audit"))
                .andExpect(jsonPath("$.data.content[0].at").value("2026-10-14T12:00:00Z"))
                .andExpect(jsonPath("$.data.content[0].id").doesNotExist());
        var request = ArgumentCaptor.forClass(AuditLogSearchRequest.class);
        verify(queryRepository).find(request.capture());
        assertThat(request.getValue().getActor()).isEqualTo(UUID.fromString(ACTOR));
        assertThat(request.getValue().getFrom()).isEqualTo(Instant.parse("2026-10-14T00:00:00Z"));
        assertThat(request.getValue().getTo()).isEqualTo(Instant.parse("2026-10-15T00:00:00Z"));
        assertThat(request.getValue().getEntityId()).isEqualTo("BASE_FARE");
        verifyNoInteractions(mailSender);
    }

    @Test void anonymousSenderAndCourierCannotReadLogs() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SYS_UNAUTHORIZED"));
        for (String token : List.of("sender-token", "courier-token")) {
            mvc.perform(get(URL).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SYS_FORBIDDEN"));
        }
        verifyNoInteractions(queryRepository);
    }

    @Test void serviceProtectsInternalInvocationOutsideHttpSecurity() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("sender", null,
                List.of(new SimpleGrantedAuthority("ROLE_SENDER"))));
        try {
            assertThatThrownBy(() -> service.search(new AuditLogSearchRequest())).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(queryRepository);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test void rejectsMalformedFiltersDatesAndPagingBeforeReadingStore() throws Exception {
        for (String[] parameter : List.of(new String[]{"actor", "not-a-uuid"}, new String[]{"from", "not-a-date"},
                new String[]{"page", "-1"}, new String[]{"page", "100001"}, new String[]{"page", "2147483648"},
                new String[]{"size", "0"}, new String[]{"size", "101"}, new String[]{"action", "A".repeat(101)},
                new String[]{"entityType", "A".repeat(101)}, new String[]{"entityId", "A".repeat(201)})) {
            mvc.perform(get(URL).header("Authorization", "Bearer admin-token").param(parameter[0], parameter[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_INVALID_REQUEST"));
        }
        mvc.perform(get(URL).header("Authorization", "Bearer admin-token").param("from", "2026-10-15T00:00:00Z")
                        .param("to", "2026-10-14T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUDIT_INVALID_FILTER"));
        verifyNoInteractions(queryRepository);
    }

    @Test void mongoFailureHasGeneric503WithoutLeakingConnectionOrException() throws Exception {
        when(queryRepository.find(any())).thenThrow(new DataAccessResourceFailureException("mongodb://private-host/secret"));
        mvc.perform(get(URL).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AUDIT_STORE_UNAVAILABLE"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-host"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DataAccess"))));
    }

    @Test void noWriteRouteExistsEvenForAdmin() throws Exception {
        for (var request : List.of(post(URL), put(URL), delete(URL))) {
            mvc.perform(request.header("Authorization", "Bearer admin-token").contentType("application/json").content("{}"))
                    .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("SYS_METHOD_NOT_ALLOWED"));
        }
        verifyNoInteractions(queryRepository);
    }

    private static Jwt jwt(String token, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token).header("alg", "HS256").subject(ACTOR).claim("roles", List.of(role))
                .issuedAt(now).expiresAt(now.plusSeconds(900)).build();
    }
}

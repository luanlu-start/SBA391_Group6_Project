package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.modules.identity.dto.response.UserDirectoryEntry;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import com.fptpost.modules.pricing.dto.request.PromotionSearchRequest;
import com.fptpost.modules.pricing.dto.request.PromotionWriteRequest;
import com.fptpost.modules.pricing.enums.*;
import com.fptpost.modules.pricing.model.Promotion;
import com.fptpost.modules.pricing.repository.*;
import com.fptpost.modules.pricing.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:promotionhttp;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.mongodb.auto-index-creation=false",
        "audit.outbox.enabled=false", "audit.retention.enabled=false", "audit.indexes.enabled=false",
        "management.health.mail.enabled=false"})
@AutoConfigureMockMvc
class PromotionHttpTest {
    static final String URL = "/api/v1/admin/promotions";
    static final UUID ACTOR = UUID.fromString("a914bf06-62cd-43a3-9672-982d40a32f6a");
    static final UUID ID = UUID.fromString("3fc2792e-1283-4701-aa9d-b3cd07e39b28");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PromotionService service;
    @MockitoBean PromotionRepository promotions;
    @MockitoBean PromotionUsageRepository usages;
    @MockitoBean PromotionSearchRepository search;
    @MockitoBean UserDirectoryLookup users;
    @MockitoBean AuditOutboxService audit;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        for (String role : List.of("ADMIN", "SENDER", "COURIER")) {
            when(decoder.decode(role.toLowerCase(Locale.ROOT))).thenReturn(jwt(role));
        }
        when(search.find(any(), any())).thenReturn(new PageImpl<>(List.of(new PromotionSearchRepository.Row(promotion(), 3)),
                PageRequest.of(0, 20), 1));
        when(promotions.findById(ID)).thenReturn(Optional.of(promotion()));
        when(promotions.findByIdForUpdate(ID)).thenReturn(Optional.of(promotion()));
        when(promotions.saveAndFlush(any())).thenAnswer(invocation -> {
            Promotion value = invocation.getArgument(0);
            value.setVersion(value.getVersion() == null ? 0L : value.getVersion() + 1); return value;
        });
    }

    @Test void adminReadsPageDetailAndTargetProjectionWithNoStore() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer admin").param("q", "SALE"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.data.content[0].value").value("9999999999999999"))
                .andExpect(jsonPath("$.data.content[0].usedCount").value(3));
        mvc.perform(get(URL + "/" + ID).header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.version").value(2));
        when(users.search("Thuận", 31)).thenReturn(List.of(new UserDirectoryEntry(ACTOR, "Trần Công Thuận", "thuan@example.invalid")));
        mvc.perform(get(URL + "/target-options").param("q", "Thuận").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.options[0].value").value(ACTOR.toString()))
                .andExpect(jsonPath("$.data.options[0].label").value("Trần Công Thuận"))
                .andExpect(jsonPath("$.data.options[0].description").value("thuan@example.invalid"))
                .andExpect(jsonPath("$.data.options[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.options[0].roles").doesNotExist())
                .andExpect(jsonPath("$.data.hasMore").value(false));
        verifyNoInteractions(mailSender, audit);
    }

    @Test void onlyAdminCanUseEveryReadAndWriteRoute() throws Exception {
        for (String token : List.of("", "sender", "courier")) {
            for (var request : List.of(get(URL), get(URL + "/" + ID), get(URL + "/target-options"),
                    post(URL).contentType("application/json").content("{}"),
                    put(URL + "/" + ID).contentType("application/json").content("{}"))) {
                if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
                mvc.perform(request).andExpect(status().is(token.isEmpty() ? 401 : 403));
            }
        }
        verifyNoInteractions(promotions, usages, search, users, audit, mailSender);
    }

    @Test void directServiceInvocationAlsoEnforcesAdminForAllOperations() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("sender", null,
                List.of(new SimpleGrantedAuthority("ROLE_SENDER"))));
        try {
            List<Runnable> denied = List.of(() -> service.list(new PromotionSearchRequest()), () -> service.detail(ID),
                    () -> service.targetOptions(null, 30), () -> service.create(write()), () -> service.update(ID, write()));
            denied.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(AccessDeniedException.class));
            verifyNoInteractions(promotions, usages, search, users, audit);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void createAndPauseReturnStableContractAndServerGeneratedVersion() throws Exception {
        var request = write();
        mvc.perform(post(URL).header("Authorization", "Bearer admin").contentType("application/json")
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.code").value("SALE26")).andExpect(jsonPath("$.data.value").value("2500"))
                .andExpect(jsonPath("$.data.version").value(0)).andExpect(jsonPath("$.data.usedCount").value(0));
        request.setExpectedVersion(2L); request.setStatus(PromotionStatus.PAUSED);
        mvc.perform(put(URL + "/" + ID).header("Authorization", "Bearer admin").contentType("application/json")
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.effectiveStatus").value("PAUSED"));
        verify(audit).enqueue(eq("PROMOTION_CREATED"), eq("PROMOTION"), anyString(), eq(Map.of()), anyMap());
        verify(audit).enqueue(eq("PROMOTION_UPDATED"), eq("PROMOTION"), eq(ID.toString()), anyMap(), anyMap());
    }

    @Test void duplicateAndStaleVersionHaveLocalizedConflictProblemsWithoutEchoingPayload() throws Exception {
        when(promotions.existsByCode("SALE26")).thenReturn(true);
        mvc.perform(post(URL).header("Authorization", "Bearer admin").contentType("application/json")
                        .content(mapper.writeValueAsString(write())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROMOTION_CODE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.title").value("Mã khuyến mãi đã được sử dụng"))
                .andExpect(header().string("Cache-Control", "no-store"));
        var request = write(); request.setExpectedVersion(1L);
        mvc.perform(put(URL + "/" + ID).header("Authorization", "Bearer admin").contentType("application/json")
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROMOTION_VERSION_CONFLICT"));
        verify(promotions, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }

    @Test void rejectsMalformedFiltersBodiesEnumsAndIdsBeforeStoreAccess() throws Exception {
        for (String[] filter : List.of(new String[]{"q", "a".repeat(51)}, new String[]{"page", "-1"},
                new String[]{"page", "100001"}, new String[]{"size", "101"}, new String[]{"status", "BROKEN"})) {
            mvc.perform(get(URL).header("Authorization", "Bearer admin").param(filter[0], filter[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_INVALID_REQUEST"));
        }
        mvc.perform(post(URL).header("Authorization", "Bearer admin").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_INVALID_REQUEST"));
        String valid = mapper.writeValueAsString(write());
        for (String invalid : List.of(valid.replace("FIXED_AMOUNT", "UNKNOWN"), valid.replace("\"targetUserIds\":[]", "\"targetUserIds\":[\"broken\"]"))) {
            mvc.perform(post(URL).header("Authorization", "Bearer admin").contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_INVALID_REQUEST"));
        }
        mvc.perform(get(URL + "/not-a-uuid").header("Authorization", "Bearer admin"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_INVALID_REQUEST"));
        mvc.perform(get(URL + "/target-options").param("limit", "51").header("Authorization", "Bearer admin"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROMOTION_INVALID_VALUE"));
        verifyNoInteractions(promotions, usages, search, users, audit);
    }

    @Test void businessValidationIsBadRequestAndDeleteDoesNotExist() throws Exception {
        var request = write(); request.setValue("1.25");
        mvc.perform(post(URL).header("Authorization", "Bearer admin").contentType("application/json")
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROMOTION_INVALID_VALUE"));
        mvc.perform(delete(URL + "/" + ID).header("Authorization", "Bearer admin"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(promotions, usages, search, users, audit);
    }

    private static PromotionWriteRequest write() {
        var request = new PromotionWriteRequest(); request.setCode("sale26"); request.setType(PromotionType.FIXED_AMOUNT);
        request.setValue("2500"); request.setValidFrom(Instant.now().minusSeconds(3600));
        request.setValidTo(Instant.now().plusSeconds(3600)); request.setUsageLimit(10); request.setTarget(PromotionTarget.ALL);
        request.setTargetUserIds(List.of()); request.setStatus(PromotionStatus.ACTIVE); return request;
    }

    private static Promotion promotion() {
        return Promotion.builder().id(ID).code("SALE26").type(PromotionType.FIXED_AMOUNT)
                .value(new BigDecimal("9999999999999999.00")).validFrom(Instant.now().minusSeconds(3600))
                .validTo(Instant.now().plusSeconds(3600)).usageLimit(10).target(PromotionTarget.ALL).status(PromotionStatus.ACTIVE)
                .version(2L).createdAt(Instant.now().minusSeconds(7200)).updatedAt(Instant.now().minusSeconds(3600)).build();
    }

    private static Jwt jwt(String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(role.toLowerCase(Locale.ROOT)).header("alg", "HS256").subject(ACTOR.toString())
                .claim("roles", List.of(role)).issuedAt(now).expiresAt(now.plusSeconds(900)).build();
    }
}

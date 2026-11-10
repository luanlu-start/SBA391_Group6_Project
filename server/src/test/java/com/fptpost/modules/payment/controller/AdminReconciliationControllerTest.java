package com.fptpost.modules.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.LedgerIntegrityResponse;
import com.fptpost.modules.payment.dto.ReconciliationAdjustRequest;
import com.fptpost.modules.payment.dto.ReconciliationItemResponse;
import com.fptpost.modules.payment.dto.ReconciliationReportResponse;
import com.fptpost.modules.payment.model.ReconciliationItemStatus;
import com.fptpost.modules.payment.model.ReconciliationReportStatus;
import com.fptpost.modules.payment.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminReconciliationController Web Layer Tests (UC-43)")
class AdminReconciliationControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private UserRepository userRepository;

    private Jwt mockJwt;
    private final UUID adminPublicId = UUID.randomUUID();
    private final Long adminId = 1L;

    @BeforeEach
    void setUp() {
        mockJwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "HS256")
                .subject(adminPublicId.toString())
                .claim("userId", adminId)
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        HandlerMethodArgumentResolver jwtArgumentResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        || parameter.getParameterType().isAssignableFrom(Jwt.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return mockJwt;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new AdminReconciliationController(reconciliationService, userRepository))
                .setCustomArgumentResolvers(jwtArgumentResolver)
                .build();
    }

    private void mockAdminLookup() {
        User admin = new User();
        admin.setId(adminId);
        admin.setPublicId(adminPublicId);
        when(userRepository.findByPublicId(adminPublicId)).thenReturn(Optional.of(admin));
    }

    @Test
    @DisplayName("API-106: GET /api/v1/admin/reconciliation - Danh sách báo cáo đối soát")
    void getReports_Success() throws Exception {
        ReconciliationReportResponse rep = ReconciliationReportResponse.builder()
                .id(10L)
                .reportDate(LocalDate.of(2026, 11, 10))
                .totalCount(5)
                .matchedCount(5)
                .mismatchCount(0)
                .status(ReconciliationReportStatus.MATCHED)
                .items(List.of())
                .build();

        PageResponse<ReconciliationReportResponse> page = new PageResponse<>(
                List.of(rep), 0, 10, 1L, 1, true, true
        );
        when(reconciliationService.getReports(0, 10)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(10L))
                .andExpect(jsonPath("$.data.content[0].status").value("MATCHED"));
    }

    @Test
    @DisplayName("API-107: POST /api/v1/admin/reconciliation/run - Chạy đối soát thủ công")
    void runReconciliation_Success() throws Exception {
        mockAdminLookup();

        ReconciliationReportResponse rep = ReconciliationReportResponse.builder()
                .id(11L)
                .reportDate(LocalDate.of(2026, 11, 10))
                .totalCount(3)
                .matchedCount(2)
                .mismatchCount(1)
                .status(ReconciliationReportStatus.MISMATCH_FOUND)
                .items(List.of())
                .build();

        when(reconciliationService.runReconciliation(any(), eq(adminId))).thenReturn(rep);

        mockMvc.perform(post("/api/v1/admin/reconciliation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportDate\":\"2026-11-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11L))
                .andExpect(jsonPath("$.data.mismatchCount").value(1))
                .andExpect(jsonPath("$.data.status").value("MISMATCH_FOUND"));
    }

    @Test
    @DisplayName("API-108: POST /api/v1/admin/reconciliation/items/{id}/adjust - Điều chỉnh dòng lệch")
    void adjustItem_Success() throws Exception {
        mockAdminLookup();

        ReconciliationItemResponse itemResp = ReconciliationItemResponse.builder()
                .id(55L)
                .txnRef("TOPUP_002")
                .status(ReconciliationItemStatus.ADJUSTED)
                .adjustmentTxnCode("LTX-RECON-ADJ-001")
                .build();

        when(reconciliationService.adjustMismatchItem(eq(55L), any(ReconciliationAdjustRequest.class), eq(adminId)))
                .thenReturn(itemResp);

        ReconciliationAdjustRequest req = ReconciliationAdjustRequest.builder()
                .debitAccount("VNPAY_CLEARING")
                .creditAccount("RECONCILIATION_SUSPENSE")
                .amount(100_000L)
                .note("Điều chỉnh lệch")
                .build();

        mockMvc.perform(post("/api/v1/admin/reconciliation/items/55/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(55L))
                .andExpect(jsonPath("$.data.status").value("ADJUSTED"))
                .andExpect(jsonPath("$.data.adjustmentTxnCode").value("LTX-RECON-ADJ-001"));
    }

    @Test
    @DisplayName("API-109: GET /api/v1/admin/ledger/integrity - Kiểm tra bất biến sổ cái kép")
    void checkLedgerIntegrity_Success() throws Exception {
        LedgerIntegrityResponse integrity = LedgerIntegrityResponse.builder()
                .balanced(true)
                .totalTransactions(12)
                .totalEntries(24)
                .totalDebit(1_500_000L)
                .totalCredit(1_500_000L)
                .discrepancyAmount(0L)
                .unbalancedTransactionCount(0)
                .unbalancedTransactionCodes(List.of())
                .message("Sổ cái kép toàn vẹn tuyệt đối")
                .build();

        when(reconciliationService.checkLedgerIntegrity()).thenReturn(integrity);

        mockMvc.perform(get("/api/v1/admin/ledger/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.totalDebit").value(1500000))
                .andExpect(jsonPath("$.data.totalCredit").value(1500000))
                .andExpect(jsonPath("$.data.discrepancyAmount").value(0));
    }
}

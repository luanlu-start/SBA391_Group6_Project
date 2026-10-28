package com.fptpost.modules.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.WithdrawalCreationRequest;
import com.fptpost.modules.payment.dto.WithdrawalResponse;
import com.fptpost.modules.payment.model.WithdrawalStatus;
import com.fptpost.modules.payment.service.WithdrawalService;
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
@DisplayName("WithdrawalController Web Layer Tests (UC-40)")
class WithdrawalControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WithdrawalService withdrawalService;

    @Mock
    private UserRepository userRepository;

    private Jwt mockJwt;
    private final UUID userPublicId = UUID.randomUUID();
    private final Long userId = 100L;

    @BeforeEach
    void setUp() {
        mockJwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "HS256")
                .subject(userPublicId.toString())
                .claim("userId", userId)
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
                String authHeader = webRequest.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer valid")) {
                    return mockJwt;
                }
                return null;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(new WithdrawalController(withdrawalService, userRepository))
                .setCustomArgumentResolvers(jwtArgumentResolver)
                .build();
    }

    private void mockUserLookup() {
        User user = new User();
        user.setId(userId);
        user.setPublicId(userPublicId);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("API-097: POST /api/v1/wallet/withdrawals - Tạo yêu cầu rút tiền thành công trả về 201 Created")
    void createWithdrawal_Success() throws Exception {
        mockUserLookup();

        WithdrawalCreationRequest request = new WithdrawalCreationRequest();
        request.setAmount(100_000L);
        request.setBankName("Vietcombank");
        request.setAccountNumber("0123456789");
        request.setAccountHolderName("NGUYEN VAN A");

        WithdrawalResponse mockResponse = WithdrawalResponse.builder()
                .id(1L)
                .requestCode("WDR-20261024-TEST1234")
                .amount(100_000L)
                .feeAmount(0L)
                .bankName("Vietcombank")
                .accountNumber("0123456789")
                .accountHolderName("NGUYEN VAN A")
                .status(WithdrawalStatus.PENDING_REVIEW)
                .createdAt(Instant.now())
                .build();

        when(withdrawalService.createWithdrawalRequest(eq(userId), any(WithdrawalCreationRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/wallet/withdrawals")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.requestCode").value("WDR-20261024-TEST1234"))
                .andExpect(jsonPath("$.data.amount").value(100000))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
    }

    @Test
    @DisplayName("API-097: POST /api/v1/wallet/withdrawals - Request body không hợp lệ trả về 400 Bad Request")
    void createWithdrawal_InvalidBody_Returns400() throws Exception {
        WithdrawalCreationRequest invalidRequest = new WithdrawalCreationRequest();
        // Thiếu amount, bankName, accountNumber, accountHolderName

        mockMvc.perform(post("/api/v1/wallet/withdrawals")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API-098: GET /api/v1/wallet/withdrawals - Lấy danh sách yêu cầu rút tiền phân trang")
    void getMyWithdrawals_Success() throws Exception {
        mockUserLookup();

        WithdrawalResponse item = WithdrawalResponse.builder()
                .id(1L)
                .requestCode("WDR-20261024-001")
                .amount(50_000L)
                .feeAmount(0L)
                .bankName("MB Bank")
                .accountNumber("123456")
                .accountHolderName("NGUYEN VAN B")
                .status(WithdrawalStatus.PENDING_REVIEW)
                .createdAt(Instant.now())
                .build();

        PageResponse<WithdrawalResponse> pageResponse = new PageResponse<>(
                List.of(item), 0, 10, 1L, 1, true, true
        );

        when(withdrawalService.getMyWithdrawals(eq(userId), eq(0), eq(10)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/wallet/withdrawals")
                        .header("Authorization", "Bearer valid-token")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].requestCode").value("WDR-20261024-001"))
                .andExpect(jsonPath("$.data.content[0].amount").value(50000));
    }

    @Test
    @DisplayName("GET /api/v1/wallet/withdrawals/{id} - Lấy chi tiết yêu cầu rút tiền")
    void getWithdrawalById_Success() throws Exception {
        mockUserLookup();

        WithdrawalResponse mockResponse = WithdrawalResponse.builder()
                .id(5L)
                .requestCode("WDR-20261024-005")
                .amount(200_000L)
                .feeAmount(0L)
                .bankName("Techcombank")
                .accountNumber("99887766")
                .accountHolderName("TRAN THI C")
                .status(WithdrawalStatus.PENDING_REVIEW)
                .createdAt(Instant.now())
                .build();

        when(withdrawalService.getWithdrawalById(userId, 5L)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/wallet/withdrawals/5")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(5L))
                .andExpect(jsonPath("$.data.requestCode").value("WDR-20261024-005"));
    }

    @Test
    @DisplayName("API-099: POST /api/v1/wallet/withdrawals/{id}/cancel - Hủy yêu cầu rút tiền thành công")
    void cancelWithdrawal_Success() throws Exception {
        mockUserLookup();

        WithdrawalResponse mockResponse = WithdrawalResponse.builder()
                .id(1L)
                .requestCode("WDR-20261024-TEST1234")
                .amount(100_000L)
                .feeAmount(0L)
                .bankName("Vietcombank")
                .accountNumber("0123456789")
                .accountHolderName("NGUYEN VAN A")
                .status(WithdrawalStatus.CANCELLED)
                .updatedAt(Instant.now())
                .build();

        when(withdrawalService.cancelWithdrawalRequest(userId, 1L)).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/wallet/withdrawals/1/cancel")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}

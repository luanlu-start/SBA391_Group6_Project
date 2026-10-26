package com.fptpost.modules.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.WalletResponse;
import com.fptpost.modules.payment.dto.WalletTopupRequest;
import com.fptpost.modules.payment.service.WalletService;
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

import java.math.BigDecimal;
import java.time.Instant;
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
@DisplayName("WalletController Web Layer Tests (UC-34 & UC-38)")
class WalletControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WalletService walletService;

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

        mockMvc = MockMvcBuilders.standaloneSetup(new WalletController(walletService, userRepository))
                .setCustomArgumentResolvers(jwtArgumentResolver)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/wallet should return 200 with wallet info for authenticated user")
    void testGetWallet_Success() throws Exception {
        User user = User.builder().id(userId).publicId(userPublicId).build();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));

        WalletResponse walletResponse = WalletResponse.builder()
                .id(1L)
                .userId(userId)
                .available(500_000L)
                .escrowHeld(100_000L)
                .pendingWithdraw(0L)
                .totalBalance(600_000L)
                .build();
        when(walletService.getWallet(userId)).thenReturn(walletResponse);

        mockMvc.perform(get("/api/v1/wallet")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.available").value(500000))
                .andExpect(jsonPath("$.data.totalBalance").value(600000));
    }

    @Test
    @DisplayName("POST /api/v1/wallet/topups should return 200 with paymentUrl for authenticated user (UC-34, API-084)")
    void testTopupWallet_Success() throws Exception {
        User user = User.builder().id(userId).publicId(userPublicId).build();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));

        WalletTopupRequest request = WalletTopupRequest.builder()
                .amount(BigDecimal.valueOf(200_000))
                .bankCode("NCB")
                .language("vn")
                .build();

        PaymentCreationResponse response = PaymentCreationResponse.builder()
                .code("00")
                .message("success")
                .paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=20000000")
                .txnRef("TOPUP_100_123456789")
                .build();

        when(walletService.createTopupUrl(eq(userId), any(WalletTopupRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/wallet/topups")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("00"))
                .andExpect(jsonPath("$.data.paymentUrl").value("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=20000000"))
                .andExpect(jsonPath("$.data.txnRef").value("TOPUP_100_123456789"));
    }

    @Test
    @DisplayName("POST /api/wallet/topups should also route to WalletController and return 200")
    void testTopupWallet_AlternativePathSuccess() throws Exception {
        User user = User.builder().id(userId).publicId(userPublicId).build();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));

        WalletTopupRequest request = WalletTopupRequest.builder()
                .amount(BigDecimal.valueOf(100_000))
                .build();

        PaymentCreationResponse response = PaymentCreationResponse.builder()
                .code("00")
                .message("success")
                .paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=10000000")
                .txnRef("TOPUP_100_987654321")
                .build();

        when(walletService.createTopupUrl(eq(userId), any(WalletTopupRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/wallet/topups")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("00"));
    }
}

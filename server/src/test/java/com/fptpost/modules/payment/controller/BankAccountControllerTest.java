package com.fptpost.modules.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.BankAccountCreationRequest;
import com.fptpost.modules.payment.dto.BankAccountResponse;
import com.fptpost.modules.payment.service.BankAccountService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankAccountController Web Layer Tests (UC-39)")
class BankAccountControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private BankAccountService bankAccountService;

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

        mockMvc = MockMvcBuilders.standaloneSetup(new BankAccountController(bankAccountService, userRepository))
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
    @DisplayName("API-094: GET /api/v1/me/bank-accounts - Lấy danh sách tài khoản ngân hàng thành công")
    void getMyBankAccounts_Success() throws Exception {
        mockUserLookup();

        BankAccountResponse item = BankAccountResponse.builder()
                .id(1L)
                .userId(userId)
                .bankCode("VCB")
                .bankName("Vietcombank")
                .maskedAccountNumber("******6789")
                .accountLast4("6789")
                .holderName("LE NHUT HUY")
                .readyForWithdrawal(true)
                .cooldownUntil(Instant.now().minus(Duration.ofHours(2)))
                .createdAt(Instant.now().minus(Duration.ofHours(26)))
                .build();

        when(bankAccountService.getMyBankAccounts(userId)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/me/bank-accounts")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].bankCode").value("VCB"))
                .andExpect(jsonPath("$.data[0].maskedAccountNumber").value("******6789"))
                .andExpect(jsonPath("$.data[0].readyForWithdrawal").value(true));
    }

    @Test
    @DisplayName("API-095: POST /api/v1/me/bank-accounts - Thêm tài khoản ngân hàng thành công trả về 201 Created")
    void createBankAccount_Success() throws Exception {
        mockUserLookup();

        BankAccountCreationRequest request = BankAccountCreationRequest.builder()
                .bankCode("TCB")
                .bankName("Techcombank")
                .accountNumber("19034567890123")
                .holderName("LE NHUT HUY")
                .build();

        BankAccountResponse response = BankAccountResponse.builder()
                .id(2L)
                .userId(userId)
                .bankCode("TCB")
                .bankName("Techcombank")
                .maskedAccountNumber("******0123")
                .accountLast4("0123")
                .holderName("LE NHUT HUY")
                .readyForWithdrawal(false)
                .cooldownUntil(Instant.now().plus(Duration.ofHours(24)))
                .createdAt(Instant.now())
                .build();

        when(bankAccountService.createBankAccount(eq(userId), any(BankAccountCreationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/me/bank-accounts")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value(2L))
                .andExpect(jsonPath("$.data.bankCode").value("TCB"))
                .andExpect(jsonPath("$.data.maskedAccountNumber").value("******0123"))
                .andExpect(jsonPath("$.data.readyForWithdrawal").value(false));
    }

    @Test
    @DisplayName("API-096: DELETE /api/v1/me/bank-accounts/{id} - Xóa tài khoản ngân hàng thành công")
    void deleteBankAccount_Success() throws Exception {
        mockUserLookup();
        doNothing().when(bankAccountService).deleteBankAccount(userId, 2L);

        mockMvc.perform(delete("/api/v1/me/bank-accounts/2")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}

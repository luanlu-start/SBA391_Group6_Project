package com.fptpost.modules.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalExceptionHandler;
import com.fptpost.common.exception.ProblemResponseFactory;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.EscrowReleaseResponse;
import com.fptpost.modules.payment.dto.EscrowResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.service.OrderPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("EscrowController Web Layer Tests (API-092 & API-093, UC-36)")
class EscrowControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OrderPaymentService orderPaymentService;

    @Mock
    private UserRepository userRepository;

    private Jwt mockJwt;
    private final UUID userPublicId = UUID.randomUUID();
    private final Long userId = 100L;

    private static GlobalExceptionHandler exceptionHandler() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages/errors");
        messages.setDefaultEncoding("UTF-8");
        return new GlobalExceptionHandler(new ProblemResponseFactory(messages));
    }

    @BeforeEach
    void setUp() {
        mockJwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "HS256")
                .subject(userPublicId.toString())
                .claim("userId", userId)
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
                String authHeader = webRequest.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer valid")) {
                    return mockJwt;
                }
                return null;
            }
        };

        HandlerMethodArgumentResolver authArgumentResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(Authentication.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                String authHeader = webRequest.getHeader("Authorization");
                if (authHeader != null && authHeader.contains("admin")) {
                    return new UsernamePasswordAuthenticationToken(userPublicId.toString(), "n/a",
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                } else if (authHeader != null && authHeader.startsWith("Bearer valid")) {
                    return new UsernamePasswordAuthenticationToken(userPublicId.toString(), "n/a",
                            List.of(new SimpleGrantedAuthority("ROLE_SENDER")));
                }
                return null;
            }
        };

        EscrowController controller = new EscrowController(orderPaymentService, userRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(jwtArgumentResolver, authArgumentResolver)
                .setControllerAdvice(exceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}/escrow (API-092) - 200 OK khi người dùng hợp lệ")
    void testGetOrderEscrow_Success() throws Exception {
        User user = User.builder().id(userId).publicId(userPublicId).build();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));

        EscrowResponse escrowResponse = EscrowResponse.builder()
                .id(1L)
                .orderId(10L)
                .orderCode("ORD-20261024-001")
                .senderId(userId)
                .courierId(200L)
                .amount(150_000L)
                .status(EscrowStatus.HELD)
                .paymentMethod(EscrowPaymentMethod.WALLET)
                .releaseAt(Instant.now().plusSeconds(3600))
                .frozen(false)
                .build();

        when(orderPaymentService.getEscrowByOrderId(eq(10L), eq(userId), anyBoolean()))
                .thenReturn(escrowResponse);

        mockMvc.perform(get("/api/v1/orders/10/escrow")
                        .header("Authorization", "Bearer valid-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orderId").value(10))
                .andExpect(jsonPath("$.data.orderCode").value("ORD-20261024-001"))
                .andExpect(jsonPath("$.data.amount").value(150000))
                .andExpect(jsonPath("$.data.status").value("HELD"))
                .andExpect(jsonPath("$.data.paymentMethod").value("WALLET"))
                .andExpect(jsonPath("$.data.frozen").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}/escrow (API-092) - 401 khi chưa đăng nhập")
    void testGetOrderEscrow_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/10/escrow")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SYS_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id}/escrow (API-092) - 403 khi không có quyền xem ký quỹ")
    void testGetOrderEscrow_Forbidden() throws Exception {
        User user = User.builder().id(userId).publicId(userPublicId).build();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
        when(orderPaymentService.getEscrowByOrderId(eq(10L), eq(userId), anyBoolean()))
                .thenThrow(new AppException(PaymentErrorCode.ESCROW_ACCESS_DENIED));

        mockMvc.perform(get("/api/v1/orders/10/escrow")
                        .header("Authorization", "Bearer valid-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ESCROW_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/escrow/{orderId}/release (API-093) - 200 OK khi Admin giải ngân thành công")
    void testReleaseEscrow_Success() throws Exception {
        User user = User.builder().id(userId).publicId(userPublicId).build();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));

        EscrowReleaseResponse releaseResponse = EscrowReleaseResponse.builder()
                .orderId(10L)
                .orderCode("ORD-20261024-001")
                .courierId(200L)
                .amountReleased(150_000L)
                .status(EscrowStatus.RELEASED)
                .message("Giải ngân tiền ký quỹ cho người giao hàng thành công")
                .releasedAt(Instant.now())
                .build();

        when(orderPaymentService.releaseEscrow(eq(10L), eq(userId), anyBoolean()))
                .thenReturn(releaseResponse);

        mockMvc.perform(post("/api/v1/admin/escrow/10/release")
                        .header("Authorization", "Bearer valid-admin-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.orderId").value(10))
                .andExpect(jsonPath("$.data.orderCode").value("ORD-20261024-001"))
                .andExpect(jsonPath("$.data.courierId").value(200))
                .andExpect(jsonPath("$.data.amountReleased").value(150000))
                .andExpect(jsonPath("$.data.status").value("RELEASED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/escrow/{orderId}/release (API-093) - 400 khi ký quỹ bị phong tỏa")
    void testReleaseEscrow_Frozen_Returns400() throws Exception {
        User user = User.builder().id(userId).publicId(userPublicId).build();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));

        when(orderPaymentService.releaseEscrow(eq(10L), eq(userId), anyBoolean()))
                .thenThrow(new AppException(PaymentErrorCode.ESCROW_FROZEN));

        mockMvc.perform(post("/api/v1/admin/escrow/10/release")
                        .header("Authorization", "Bearer valid-admin-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ESCROW_FROZEN"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/escrow/{orderId}/release (API-093) - 400 khi chưa phân công shipper")
    void testReleaseEscrow_CourierNotAssigned_Returns400() throws Exception {
        User user = User.builder().id(userId).publicId(userPublicId).build();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));

        when(orderPaymentService.releaseEscrow(eq(10L), eq(userId), anyBoolean()))
                .thenThrow(new AppException(PaymentErrorCode.ORDER_COURIER_NOT_ASSIGNED));

        mockMvc.perform(post("/api/v1/admin/escrow/10/release")
                        .header("Authorization", "Bearer valid-admin-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_COURIER_NOT_ASSIGNED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/escrow/{orderId}/release (API-093) - 401 khi chưa đăng nhập")
    void testReleaseEscrow_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/escrow/10/release")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SYS_UNAUTHORIZED"));
    }
}

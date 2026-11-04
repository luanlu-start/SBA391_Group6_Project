package com.fptpost.modules.order.controller;

import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.response.MarketOrderAcceptResponse;
import com.fptpost.modules.order.dto.response.MarketplaceOrderDetailResponse;
import com.fptpost.modules.order.dto.response.MarketplaceOrderResponse;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketController Web Layer Tests (UC-16, UC-17)")
class MarketControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketController(orderService, userRepository))
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/market/orders should return 200 with available market orders (API-039)")
    void testGetMarketplaceOrders() throws Exception {
        var sample = new MarketplaceOrderResponse(
                1L, "FP001", "Hà Nội", "Đà Nẵng",
                "Laptop", 1, new BigDecimal("2.5"), new BigDecimal("500000"),
                OrderStatus.FINDING_SHIPPER, Instant.now()
        );
        var page = new PageImpl<>(List.of(sample), PageRequest.of(0, 10), 1);

        when(orderService.viewAvailableOrder(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/market/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].orderCode").value("FP001"));
    }

    @Test
    @DisplayName("GET /api/v1/market/orders/{id} should return 200 with masked details (API-041 / SCR-30)")
    void testGetMarketOrderDetail() throws Exception {
        var detail = new MarketplaceOrderDetailResponse(
                10L, "FP010", 1L, "Cổng trường FPT", "Ký túc xá",
                "Nguyễn ***", "0987***321", "Tài liệu", "Hồ sơ dự án",
                1, new BigDecimal("0.5"), new BigDecimal("200000"),
                new BigDecimal("25000"), new BigDecimal("3.0"), "Giao trước 17h",
                OrderStatus.FINDING_SHIPPER, Instant.now(), List.of(), false, true
        );

        when(orderService.getMarketOrderDetail(eq("10"), any())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/market/orders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.orderCode").value("FP010"))
                .andExpect(jsonPath("$.data.maskedReceiverPhone").value("0987***321"));
    }

    @Test
    @DisplayName("GET /api/v1/market/orders/{code} should work with alphanumeric orderCode (API-041 / SCR-30)")
    void testGetMarketOrderDetail_ByOrderCode() throws Exception {
        var detail = new MarketplaceOrderDetailResponse(
                2L, "ORD-TEST-002", 1L, "Hà Nội", "Đà Nẵng",
                "Nguyễn ***", "0987***321", "Tài liệu", "Hồ sơ dự án",
                1, new BigDecimal("0.5"), new BigDecimal("200000"),
                new BigDecimal("25000"), new BigDecimal("3.0"), "Giao trước 17h",
                OrderStatus.FINDING_SHIPPER, Instant.now(), List.of(), false, true
        );

        when(orderService.getMarketOrderDetail(eq("ORD-TEST-002"), any())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/market/orders/ORD-TEST-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.orderCode").value("ORD-TEST-002"));
    }

    @Test
    @DisplayName("POST /api/v1/market/orders/{id}/accept should succeed when courier authenticated (API-043 / UC-17)")
    void testAcceptOrder_Success() throws Exception {
        UUID publicId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "none")
                .subject(publicId.toString())
                .claim("roles", List.of("COURIER"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        var courier = User.builder()
                .id(2L)
                .publicId(publicId)
                .roles(Set.of(Role.builder().code("COURIER").build()))
                .build();

        when(userRepository.findByPublicId(publicId)).thenReturn(Optional.of(courier));

        var acceptResponse = new MarketOrderAcceptResponse(
                10L, "FP010", 1L, 2L,
                "Hà Nội", "Đà Nẵng", "Tài liệu", 1,
                new BigDecimal("0.5"), new BigDecimal("200000"),
                new BigDecimal("25000"), OrderStatus.AWAITING_PAYMENT,
                Instant.now(), "Nhận đơn thành công! Đơn hàng đã chuyển sang Chờ ký quỹ."
        );

        when(orderService.acceptMarketOrder(eq("10"), eq(courier))).thenReturn(acceptResponse);

        mockMvc.perform(post("/api/v1/market/orders/10/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.courierId").value(2))
                .andExpect(jsonPath("$.data.status").value("AWAITING_PAYMENT"));
    }
}

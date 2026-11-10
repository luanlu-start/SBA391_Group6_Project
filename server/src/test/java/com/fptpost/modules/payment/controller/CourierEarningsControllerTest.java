package com.fptpost.modules.payment.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.CourierEarningsResponse;
import com.fptpost.modules.payment.dto.CourierStatsResponse;
import com.fptpost.modules.payment.service.CourierEarningsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierEarningsControllerTest {

    @Mock
    private CourierEarningsService courierEarningsService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CourierEarningsController controller;

    private Jwt mockJwt;
    private final UUID userPublicId = UUID.randomUUID();
    private final Long userId = 15L;

    @BeforeEach
    void setUp() {
        mockJwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "HS256")
                .subject(userPublicId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        User user = new User();
        user.setId(userId);
        user.setPublicId(userPublicId);
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
    }

    @Test
    void getMyEarnings_returnsOkResponse() {
        CourierEarningsResponse mockEarnings = CourierEarningsResponse.builder()
                .period("MONTH")
                .completedOrdersCount(5)
                .grossEarnings(500_000L)
                .commissionAmount(35_000L)
                .netEarnings(465_000L)
                .chartPoints(List.of())
                .receipts(List.of())
                .build();

        when(courierEarningsService.getMyEarnings(userId, "MONTH", null, null)).thenReturn(mockEarnings);

        ResponseEntity<ApiResponse<CourierEarningsResponse>> response = controller.getMyEarnings(mockJwt, "MONTH", null, null);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(465_000L, response.getBody().getData().getNetEarnings());
    }

    @Test
    void getMyStats_returnsOkResponse() {
        CourierStatsResponse mockStats = CourierStatsResponse.builder()
                .userId(userId)
                .currentTier("BRONZE")
                .nextTier("SILVER")
                .trustScore(BigDecimal.valueOf(72.5))
                .completedOrders(18)
                .build();

        when(courierEarningsService.getMyStats(userId)).thenReturn(mockStats);

        ResponseEntity<ApiResponse<CourierStatsResponse>> response = controller.getMyStats(mockJwt);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("BRONZE", response.getBody().getData().getCurrentTier());
    }
}

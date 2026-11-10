package com.fptpost.modules.payment.service;

import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.dto.CourierEarningsResponse;
import com.fptpost.modules.payment.dto.CourierStatsResponse;
import com.fptpost.modules.payment.model.Escrow;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.repository.EscrowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierEarningsServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private EscrowRepository escrowRepository;
    @Mock
    private CourierProfileRepository courierProfileRepository;

    private CourierEarningsService courierEarningsService;

    @BeforeEach
    void setUp() {
        courierEarningsService = new CourierEarningsService(
                orderRepository,
                escrowRepository,
                courierProfileRepository
        );
    }

    @Test
    void getMyEarnings_calculatesGrossCommissionNetAndReceiptsCorrectly() {
        Long userId = 15L;
        CourierProfile profile = new CourierProfile();
        profile.setUserId(userId);
        profile.setTier(CourierTier.SILVER); // 6% commission
        profile.setTrustScore(BigDecimal.valueOf(78.0));
        profile.setCompletedOrders(35);

        Order completedOrder = Order.builder()
                .id(101L)
                .orderCode("FPT-101")
                .productName("Bàn phím cơ")
                .courierId(userId)
                .status(OrderStatus.COMPLETED)
                .priceSuggested(BigDecimal.valueOf(200_000))
                .createdAt(Instant.now())
                .endedAt(Instant.now())
                .build();

        Order inTransitOrder = Order.builder()
                .id(102L)
                .orderCode("FPT-102")
                .productName("Tai nghe Bluetooth")
                .courierId(userId)
                .status(OrderStatus.IN_DELIVERY)
                .priceSuggested(BigDecimal.valueOf(150_000))
                .createdAt(Instant.now())
                .build();

        Escrow releasedEscrow = Escrow.builder()
                .id(1L)
                .orderId(101L)
                .amount(200_000L)
                .status(EscrowStatus.RELEASED)
                .build();

        Escrow heldEscrow = Escrow.builder()
                .id(2L)
                .orderId(102L)
                .amount(150_000L)
                .status(EscrowStatus.HELD)
                .build();

        when(courierProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(orderRepository.findByCourierIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(completedOrder, inTransitOrder));
        when(escrowRepository.findByOrderId(101L)).thenReturn(Optional.of(releasedEscrow));
        when(escrowRepository.findByOrderId(102L)).thenReturn(Optional.of(heldEscrow));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        CourierEarningsResponse response = courierEarningsService.getMyEarnings(userId, "DAY", today.minusDays(2), today);

        assertEquals(1, response.getCompletedOrdersCount());
        assertEquals(200_000L, response.getGrossEarnings());
        assertEquals(12_000L, response.getCommissionAmount()); // 6% of 200,000
        assertEquals(188_000L, response.getNetEarnings());
        assertEquals(150_000L, response.getPendingEscrowAmount());
        assertEquals(1, response.getReceipts().size());
        assertEquals("FPT-101", response.getReceipts().get(0).getOrderCode());
    }

    @Test
    void getMyStats_calculatesNextTierProgressAndRates() {
        Long userId = 15L;
        CourierProfile profile = new CourierProfile();
        profile.setUserId(userId);
        profile.setTier(CourierTier.BRONZE);
        profile.setTrustScore(BigDecimal.valueOf(70.0));
        profile.setCompletedOrders(20);

        when(courierProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(orderRepository.findByCourierIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        CourierStatsResponse stats = courierEarningsService.getMyStats(userId);

        assertEquals("BRONZE", stats.getCurrentTier());
        assertEquals("SILVER", stats.getNextTier());
        assertEquals(30, stats.getNextTierMinOrders());
        assertEquals(10, stats.getOrdersNeededForNextTier());
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(stats.getScoreNeededForNextTier()));
        assertTrue(stats.getOrdersProgressPercent() > 60);
    }
}

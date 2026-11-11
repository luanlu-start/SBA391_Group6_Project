package com.fptpost.modules.payment.service;

import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.service.ParameterService;
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
import com.fptpost.modules.reputation.model.TrustScore;
import com.fptpost.modules.reputation.repository.TrustScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    @Mock
    private ParameterService parameterService;
    @Mock
    private TrustScoreRepository trustScoreRepository;

    private CourierEarningsService courierEarningsService;

    @BeforeEach
    void setUp() {
        courierEarningsService = new CourierEarningsService(
                orderRepository,
                escrowRepository,
                courierProfileRepository,
                parameterService,
                trustScoreRepository
        );
    }

    /** Giá trị mặc định của PRM-074..090 trong catalog tham số. */
    private static ParameterSnapshot trustParameters() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("TRUST_INIT", "50");
        values.put("W_ONTIME", "0.35");
        values.put("W_RATING", "0.3");
        values.put("W_COMPLETE", "0.2");
        values.put("W_COMPLAINT", "0.15");
        values.put("TRUST_RESTRICT", "30");
        values.put("TRUST_REVIEW", "20");
        values.put("CANCEL_PENALTY", "5");
        values.put("CANCEL_LIMIT", "3");
        values.put("CANCEL_BAN", "3");
        values.put("TIER_BRONZE", "≥ 55 điểm & ≥ 5 đơn");
        values.put("TIER_SILVER", "≥ 70 điểm & ≥ 20 đơn");
        values.put("TIER_GOLD", "≥ 85 điểm & ≥ 60 đơn");
        values.put("TIER_DIAMOND", "≥ 95 điểm & ≥ 150 đơn");
        Map<String, Long> versions = new LinkedHashMap<>();
        values.keySet().forEach(key -> versions.put(key, 1L));
        return new ParameterSnapshot(values, versions, Instant.parse("2026-11-10T00:00:00Z"));
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
        when(parameterService.getSnapshot()).thenReturn(trustParameters());
        when(trustScoreRepository.findById(userId)).thenReturn(Optional.empty());

        CourierStatsResponse stats = courierEarningsService.getMyStats(userId);

        assertEquals("BRONZE", stats.getCurrentTier());
        assertEquals("SILVER", stats.getNextTier());
        assertEquals(20, stats.getNextTierMinOrders());
        assertEquals(0, stats.getOrdersNeededForNextTier());
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.getScoreNeededForNextTier()));
        assertEquals(100, stats.getOrdersProgressPercent());
        assertNull(stats.getOnTimeRate());
        assertNull(stats.getRatingAvg());
        assertEquals(0.0, stats.getComplaintRate());
    }

    @Test
    void getMyStats_readsPerformanceFromTrustScoreAndTopTierStaysAtDiamond() {
        Long userId = 16L;
        CourierProfile profile = new CourierProfile();
        profile.setUserId(userId);
        profile.setTier(CourierTier.DIAMOND);
        profile.setTrustScore(BigDecimal.valueOf(96.0));
        profile.setCompletedOrders(180);
        TrustScore score = TrustScore.builder().courierId(userId).score(BigDecimal.valueOf(96.0)).tier(CourierTier.DIAMOND)
                .ontimeRate(new BigDecimal("0.8833")).ratingAverage(new BigDecimal("4.80")).ratingCount(20)
                .completionRate(new BigDecimal("0.9700")).complaintRate(new BigDecimal("0.0300"))
                .completedOrders(180).lostComplaints(20).recentCancellations(0).calculatedAt(Instant.now()).build();

        when(courierProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(orderRepository.findByCourierIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(parameterService.getSnapshot()).thenReturn(trustParameters());
        when(trustScoreRepository.findById(userId)).thenReturn(Optional.of(score));

        CourierStatsResponse stats = courierEarningsService.getMyStats(userId);

        assertEquals("DIAMOND", stats.getNextTier());
        assertEquals(150, stats.getNextTierMinOrders());
        assertEquals(88.3, stats.getOnTimeRate());
        assertEquals(4.8, stats.getRatingAvg());
        assertEquals(10.0, stats.getComplaintRate());
    }
}

package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.relay.dto.*;
import com.fptpost.modules.relay.model.*;
import com.fptpost.modules.relay.repository.*;
import com.fptpost.modules.relay.service.RelayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RelayServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderLegRepository orderLegRepository;

    @Mock
    private MeetingPointRepository meetingPointRepository;

    @Mock
    private HandoverTokenRepository handoverTokenRepository;

    @Mock
    private HandoverRecordRepository handoverRecordRepository;

    @Mock
    private HandoverIncidentRepository handoverIncidentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private com.fptpost.modules.order.service.PricingCalculatorService pricingCalculatorService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RelayService relayService;

    private User courier1;
    private User courier2;
    private Order order;
    private OrderLeg leg1;
    private OrderLeg leg2;
    private MeetingPoint meetingPoint;

    @BeforeEach
    void setUp() {
        courier1 = User.builder()
                .id(101L)
                .email("courier1@fptpost.vn")
                .phone("0901111111")
                .build();

        courier2 = User.builder()
                .id(102L)
                .email("courier2@fptpost.vn")
                .phone("0902222222")
                .build();

        meetingPoint = MeetingPoint.builder()
                .id(1L)
                .name("Cổng chính Đại học FPT - Tòa nhà Alpha")
                .address("Khu CNC Hòa Lạc, Hà Nội")
                .latitude(new BigDecimal("21.0128"))
                .longitude(new BigDecimal("105.5258"))
                .campusArea("Khu Công nghệ cao Hòa Lạc")
                .isActive(true)
                .build();

        order = Order.builder()
                .id(500L)
                .orderCode("FPT-261107-RELAYTEST")
                .senderId(1L)
                .courierId(courier1.getId())
                .productName("Tài liệu nghiên cứu khoa học")
                .status(OrderStatus.IN_DELIVERY)
                .isRelay(true)
                .currentLegNo(1)
                .totalLegs(2)
                .priceAgreed(new BigDecimal("60000"))
                .build();

        leg1 = OrderLeg.builder()
                .id(1001L)
                .order(order)
                .legNo(1)
                .courierId(courier1.getId())
                .courierName("Shipper 1")
                .courierPhone("0901111111")
                .startAddress("KTX Dom A")
                .endAddress("Cổng chính Alpha FPT")
                .meetingPoint(meetingPoint)
                .fee(new BigDecimal("25000"))
                .status(LegStatus.IN_TRANSIT)
                .build();

        leg2 = OrderLeg.builder()
                .id(1002L)
                .order(order)
                .legNo(2)
                .courierId(courier2.getId())
                .courierName("Shipper 2")
                .courierPhone("0902222222")
                .startAddress("Cổng chính Alpha FPT")
                .endAddress("Keangnam Landmark 72")
                .fee(new BigDecimal("35000"))
                .status(LegStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Tạo mã QR bàn giao thành công với thời hạn 10 phút (PRM-097)")
    void testGenerateHandoverToken_Success() {
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdAndLegNo(500L, 1)).thenReturn(Optional.of(leg1));
        when(orderLegRepository.findByOrderIdAndLegNo(500L, 2)).thenReturn(Optional.of(leg2));
        when(handoverTokenRepository.save(any(HandoverToken.class))).thenAnswer(i -> i.getArgument(0));

        HandoverTokenResponse response = relayService.generateHandoverToken(500L, 1, courier1);

        assertNotNull(response);
        assertEquals(500L, response.getOrderId());
        assertEquals(1, response.getLegNo());
        assertNotNull(response.getTokenCode());
        assertTrue(response.getTokenCode().startsWith("HO-"));
        assertTrue(response.getRemainingSeconds() > 500 && response.getRemainingSeconds() <= 600);
        verify(handoverTokenRepository, times(1)).save(any(HandoverToken.class));
    }

    @Test
    @DisplayName("Bàn giao chặng thành công giữa 2 tài xế (API-083, BR-F-060, BR-R-048)")
    void testConfirmHandover_Success() {
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdAndLegNo(500L, 1)).thenReturn(Optional.of(leg1));
        when(orderLegRepository.findByOrderIdAndLegNo(500L, 2)).thenReturn(Optional.of(leg2));

        HandoverToken validToken = HandoverToken.builder()
                .id(1L)
                .order(order)
                .leg(leg1)
                .legNo(1)
                .tokenCode("HO-TEST1234")
                .isUsed(false)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(8)))
                .build();

        when(handoverTokenRepository.findByTokenCode("HO-TEST1234")).thenReturn(Optional.of(validToken));
        when(handoverTokenRepository.save(any(HandoverToken.class))).thenAnswer(i -> i.getArgument(0));
        when(orderLegRepository.save(any(OrderLeg.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(handoverRecordRepository.save(any(HandoverRecord.class))).thenAnswer(i -> i.getArgument(0));

        HandoverConfirmRequest request = HandoverConfirmRequest.builder()
                .tokenCode("HO-TEST1234")
                .photoUrl("https://fptpost.vn/uploads/handover_photo.jpg")
                .notes("Hàng nguyên vẹn, bao bì sạch sẽ")
                .latitude(new BigDecimal("21.0128"))
                .longitude(new BigDecimal("105.5258"))
                .build();

        HandoverConfirmResponse response = relayService.confirmHandover(500L, 1, request, courier2);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getCompletedLegNo());
        assertEquals(LegStatus.HANDED_OVER, response.getCompletedLegStatus());
        assertEquals(2, response.getNextLegNo());
        assertEquals(LegStatus.IN_TRANSIT, response.getNextLegStatus());
        assertEquals(courier2.getId(), response.getNextCourierId());

        // Kiểm tra order được chuyển trách nhiệm sang courier 2
        assertEquals(courier2.getId(), order.getCourierId());
        assertEquals(2, order.getCurrentLegNo());

        // Kiểm tra token đã được đánh dấu used
        assertTrue(validToken.getIsUsed());
        assertEquals(courier2.getId(), validToken.getUsedByCourierId());
    }

    @Test
    @DisplayName("Ngoại lệ A1: Báo lỗi khi mã bàn giao đã hết hạn quá 10 phút")
    void testConfirmHandover_ExpiredToken() {
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdAndLegNo(500L, 1)).thenReturn(Optional.of(leg1));
        when(orderLegRepository.findByOrderIdAndLegNo(500L, 2)).thenReturn(Optional.of(leg2));

        HandoverToken expiredToken = HandoverToken.builder()
                .id(1L)
                .order(order)
                .leg(leg1)
                .legNo(1)
                .tokenCode("HO-EXPIRED1")
                .isUsed(false)
                .expiresAt(Instant.now().minus(Duration.ofMinutes(2))) // Đã hết hạn
                .build();

        when(handoverTokenRepository.findByTokenCode("HO-EXPIRED1")).thenReturn(Optional.of(expiredToken));

        HandoverConfirmRequest request = HandoverConfirmRequest.builder()
                .tokenCode("HO-EXPIRED1")
                .photoUrl("https://fptpost.vn/uploads/photo.jpg")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                relayService.confirmHandover(500L, 1, request, courier2)
        );

        assertTrue(ex.getMessage().contains("hết hạn"));
    }

    @Test
    @DisplayName("Ngoại lệ A2: Báo cáo sự cố khi bàn giao tại điểm hẹn")
    void testReportIncident_Success() {
        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdAndLegNo(500L, 1)).thenReturn(Optional.of(leg1));
        when(handoverIncidentRepository.save(any(HandoverIncident.class))).thenAnswer(i -> {
            HandoverIncident inc = i.getArgument(0);
            inc.setId(99L);
            return inc;
        });

        HandoverIncidentRequest request = HandoverIncidentRequest.builder()
                .incidentType("Hư hại bao bì")
                .description("Hộp bưu phẩm bị ướt ở góc đáy khi bàn giao")
                .photoUrl("https://fptpost.vn/uploads/damaged.jpg")
                .build();

        HandoverIncidentResponse response = relayService.reportIncident(500L, 1, request, courier2);

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals("Hư hại bao bì", response.getIncidentType());
        assertEquals(LegStatus.INCIDENT, leg1.getStatus());
        verify(handoverIncidentRepository, times(1)).save(any(HandoverIncident.class));
    }

    @Test
    @DisplayName("Ước tính cước phí tiếp sức chia theo tỷ lệ quãng đường (UC-32, BR-R-047)")
    void testEstimateRelayPricing_Success() {
        when(pricingCalculatorService.estimatePrice(any(com.fptpost.modules.order.dto.PricingEstimateRequest.class)))
                .thenAnswer(i -> {
                    com.fptpost.modules.order.dto.PricingEstimateRequest r = i.getArgument(0);
                    if (r.getDistanceKm() != null) {
                        return com.fptpost.modules.order.dto.PricingEstimateResponse.builder()
                                .distanceKm(r.getDistanceKm())
                                .totalEstimatedPrice(new BigDecimal("60000"))
                                .build();
                    }
                    return com.fptpost.modules.order.dto.PricingEstimateResponse.builder()
                            .distanceKm(new BigDecimal("15.0"))
                            .totalEstimatedPrice(new BigDecimal("30000"))
                            .build();
                });

        RelayPricingEstimateRequest request = RelayPricingEstimateRequest.builder()
                .pickupLat(new BigDecimal("21.0145"))
                .pickupLng(new BigDecimal("105.5280"))
                .deliveryLat(new BigDecimal("21.0173"))
                .deliveryLng(new BigDecimal("105.7840"))
                .weightKg(new BigDecimal("2.0"))
                .relayPoints(List.of(
                        RelayPointDto.builder()
                                .address("Điểm hẹn Alpha")
                                .latitude(new BigDecimal("21.0128"))
                                .longitude(new BigDecimal("105.5258"))
                                .name("Cổng chính Alpha")
                                .build()
                ))
                .build();

        RelayPricingEstimateResponse response = relayService.estimateRelayPricing(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("30.00"), response.getTotalDistanceKm());
        assertEquals(new BigDecimal("60000"), response.getTotalEstimatedPrice());
        assertEquals(2, response.getLegs().size());

        // Tổng cước các chặng phải đúng bằng tổng cước đơn hàng (BR-R-047)
        BigDecimal sumFees = response.getLegs().stream()
                .map(LegPricingBreakdownDto::getEstimatedFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(response.getTotalEstimatedPrice(), sumFees);
    }
}

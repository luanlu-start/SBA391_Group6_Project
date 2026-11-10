package com.fptpost.modules.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.dto.RelayPoint;
import com.fptpost.modules.order.dto.request.RelayOrderRequest;
import com.fptpost.modules.order.dto.request.RelayPricingEstimateRequest;
import com.fptpost.modules.order.dto.response.RelayOrderResponse;
import com.fptpost.modules.order.dto.response.RelayPricingEstimateResponse;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.DeliveryUrgency;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderLeg;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderLegRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RelayOrderServiceTest {

    private OrderRepository orderRepository;
    private OrderLegRepository orderLegRepository;
    private PricingCalculatorService pricingCalculatorService;
    private OrderSubmissionScreening orderSubmissionScreening;
    private OrderMapper orderMapper;

    private RelayOrderService relayOrderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderLegRepository = mock(OrderLegRepository.class);
        pricingCalculatorService = new PricingCalculatorService(new ObjectMapper());
        orderSubmissionScreening = mock(OrderSubmissionScreening.class);
        orderMapper = Mappers.getMapper(OrderMapper.class);

        relayOrderService = new RelayOrderService(
                orderRepository,
                orderLegRepository,
                pricingCalculatorService,
                orderSubmissionScreening,
                orderMapper
        );
    }

    @Test
    @DisplayName("PRM-102: Đơn tiếp sức vượt quá 2 điểm hẹn (tức hơn 3 chặng) phải bị từ chối")
    void estimateRelayPricing_whenExceedsMaxRelayPoints_shouldThrowException() {
        var request = new RelayPricingEstimateRequest();
        request.setPickupLat(new BigDecimal("10.7769"));
        request.setPickupLng(new BigDecimal("106.7009"));
        request.setDeliveryLat(new BigDecimal("10.8231"));
        request.setDeliveryLng(new BigDecimal("106.6297"));
        request.setRelayPoints(List.of(
                RelayPoint.builder().address("Điểm 1").latitude(new BigDecimal("10.78")).longitude(new BigDecimal("106.69")).build(),
                RelayPoint.builder().address("Điểm 2").latitude(new BigDecimal("10.79")).longitude(new BigDecimal("106.68")).build(),
                RelayPoint.builder().address("Điểm 3").latitude(new BigDecimal("10.80")).longitude(new BigDecimal("106.67")).build()
        ));

        assertThatThrownBy(() -> relayOrderService.estimateRelayPricing(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("tối đa 3 chặng");
    }

    @Test
    @DisplayName("Khi không có điểm hẹn tiếp sức nào thì phải bị từ chối")
    void estimateRelayPricing_whenEmptyRelayPoints_shouldThrowException() {
        var request = new RelayPricingEstimateRequest();
        request.setPickupLat(new BigDecimal("10.7769"));
        request.setPickupLng(new BigDecimal("106.7009"));
        request.setDeliveryLat(new BigDecimal("10.8231"));
        request.setDeliveryLng(new BigDecimal("106.6297"));
        request.setRelayPoints(List.of());

        assertThatThrownBy(() -> relayOrderService.estimateRelayPricing(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("ít nhất 1 điểm hẹn");
    }

    @Test
    @DisplayName("BR-R-047: Tính cước tiếp sức chia theo tỷ lệ quãng đường và tổng cước chặng đúng 100% tổng cước đơn")
    void estimateRelayPricing_shouldCalculateLegBreakdownAndEqualTotalFee() {
        var request = new RelayPricingEstimateRequest();
        request.setPickupLat(new BigDecimal("10.7769"));
        request.setPickupLng(new BigDecimal("106.7009"));
        request.setDeliveryLat(new BigDecimal("10.8500"));
        request.setDeliveryLng(new BigDecimal("106.7800"));
        request.setWeightKg(new BigDecimal("1.5"));
        request.setUrgency(DeliveryUrgency.STANDARD);
        request.setRelayPoints(List.of(
                RelayPoint.builder()
                        .address("Vincom Mega Mall Thảo Điền")
                        .latitude(new BigDecimal("10.8030"))
                        .longitude(new BigDecimal("106.7400"))
                        .build()
        ));

        RelayPricingEstimateResponse response = relayOrderService.estimateRelayPricing(request);

        assertThat(response).isNotNull();
        assertThat(response.getLegs()).hasSize(2);
        assertThat(response.getTotalDistanceKm()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getTotalEstimatedPrice()).isGreaterThan(BigDecimal.ZERO);

        // Kiểm tra tính bảo toàn tổng cước: Tổng phí các chặng = Đúng 100% phí đơn hàng (không lệch 1 đồng do làm tròn)
        BigDecimal sumLegFees = response.getLegs().stream()
                .map(RelayPricingEstimateResponse.LegPricing::getEstimatedFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sumLegFees).isEqualByComparingTo(response.getTotalEstimatedPrice());

        // Kiểm tra thứ tự và thông tin từng chặng
        var leg1 = response.getLegs().get(0);
        assertThat(leg1.getLegNo()).isEqualTo(1);
        assertThat(leg1.getFromAddress()).isEqualTo("Điểm gửi hàng");
        assertThat(leg1.getToAddress()).isEqualTo("Vincom Mega Mall Thảo Điền");
        assertThat(leg1.getEstimatedFee()).isGreaterThan(BigDecimal.ZERO);

        var leg2 = response.getLegs().get(1);
        assertThat(leg2.getLegNo()).isEqualTo(2);
        assertThat(leg2.getFromAddress()).isEqualTo("Vincom Mega Mall Thảo Điền");
        assertThat(leg2.getToAddress()).isEqualTo("Điểm nhận hàng");
        assertThat(leg2.getEstimatedFee()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Tạo đơn tiếp sức thành công: Sinh ra 1 Order (isRelay=true) và 2 OrderLegs có status FINDING_SHIPPER")
    void createRelayOrder_shouldSaveOrderAndAllLegs() {
        var relayRequest = new RelayOrderRequest();
        relayRequest.setPickupAddress("123 Lê Lợi, Quận 1");
        relayRequest.setPickupLatitude(new BigDecimal("10.7769"));
        relayRequest.setPickupLongitude(new BigDecimal("106.7009"));
        relayRequest.setDeliveryAddress("456 Xa Lộ Hà Nội, Thủ Đức");
        relayRequest.setDeliveryLatitude(new BigDecimal("10.8500"));
        relayRequest.setDeliveryLongitude(new BigDecimal("106.7800"));
        relayRequest.setReceiverName("Nguyễn Văn B");
        relayRequest.setReceiverPhone("0987654321");
        relayRequest.setProductName("Tài liệu hợp đồng");
        relayRequest.setQuantity(1);
        relayRequest.setWeightKg(new BigDecimal("0.5"));
        relayRequest.setUrgency(DeliveryUrgency.STANDARD);
        relayRequest.setRelayPoints(List.of(
                RelayPoint.builder()
                        .address("Cây xăng Petrolimex Hàng Xanh")
                        .latitude(new BigDecimal("10.8000"))
                        .longitude(new BigDecimal("106.7100"))
                        .build()
        ));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            long legIdCounter = 201L;
            for (OrderLeg leg : order.getLegs()) {
                leg.setId(legIdCounter++);
            }
            return order;
        });

        RelayOrderResponse response = relayOrderService.createRelayOrder(1L, relayRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getIsRelay()).isTrue();
        assertThat(response.getLegs()).hasSize(2);

        // Kiểm tra từng chặng
        var leg1 = response.getLegs().get(0);
        assertThat(leg1.getLegNo()).isEqualTo(1);
        assertThat(leg1.getPickupAddress()).isEqualTo("123 Lê Lợi, Quận 1");
        assertThat(leg1.getDeliveryAddress()).isEqualTo("Cây xăng Petrolimex Hàng Xanh");
        assertThat(leg1.getStatus()).isEqualTo(OrderStatus.FINDING_SHIPPER);

        var leg2 = response.getLegs().get(1);
        assertThat(leg2.getLegNo()).isEqualTo(2);
        assertThat(leg2.getPickupAddress()).isEqualTo("Cây xăng Petrolimex Hàng Xanh");
        assertThat(leg2.getDeliveryAddress()).isEqualTo("456 Xa Lộ Hà Nội, Thủ Đức");
        assertThat(leg2.getStatus()).isEqualTo(OrderStatus.FINDING_SHIPPER);

        // Kiểm tra screening được gọi
        verify(orderSubmissionScreening).screen(any(Order.class));
        verify(orderRepository).save(any(Order.class));
    }
}

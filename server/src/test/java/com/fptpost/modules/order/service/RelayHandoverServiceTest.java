package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.HandoverConfirmRequest;
import com.fptpost.modules.order.dto.response.HandoverQrResponse;
import com.fptpost.modules.order.dto.response.RelayHandoverResultResponse;
import com.fptpost.modules.order.dto.response.RelayMeetingPointDto;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderLeg;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderLegRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelayHandoverServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderLegRepository orderLegRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private RelayOrderService relayOrderService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    private RelayHandoverService service;

    private Order order;
    private OrderLeg leg1;
    private OrderLeg leg2;
    private OrderLeg leg3;
    private List<OrderLeg> legs;

    private final Long ORDER_ID = 33001L;
    private final Long COURIER_1 = 101L;
    private final Long COURIER_2 = 102L;
    private final Long COURIER_3 = 103L;

    private final MultipartFile photo = new MockMultipartFile(
            "photo", "handover_proof.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}
    );

    @BeforeEach
    void setup() {
        service = new RelayHandoverService(
                orderRepository,
                orderLegRepository,
                orderService,
                relayOrderService,
                userRepository,
                userProfileRepository
        );
        ReflectionTestUtils.setField(service, "qrSecret", "relay-test-secret-key-32bytes-long-12345");

        order = Order.builder()
                .id(ORDER_ID)
                .orderCode("RL33-TEST-001")
                .senderId(99L)
                .courierId(COURIER_1)
                .status(OrderStatus.IN_DELIVERY)
                .isRelay(true)
                .images(new ArrayList<>())
                .build();

        leg1 = OrderLeg.builder()
                .id(1L)
                .order(order)
                .legNo(1)
                .pickupAddress("Điểm gửi A")
                .deliveryAddress("Điểm hẹn 1 (Thư viện FPT)")
                .distanceKm(BigDecimal.valueOf(5.0))
                .legFee(BigDecimal.valueOf(25000))
                .courierId(COURIER_1)
                .status(OrderStatus.IN_DELIVERY)
                .createdAt(Instant.now())
                .build();

        leg2 = OrderLeg.builder()
                .id(2L)
                .order(order)
                .legNo(2)
                .pickupAddress("Điểm hẹn 1 (Thư viện FPT)")
                .deliveryAddress("Điểm hẹn 2 (Cổng chính FPT)")
                .distanceKm(BigDecimal.valueOf(4.0))
                .legFee(BigDecimal.valueOf(20000))
                .courierId(COURIER_2)
                .status(OrderStatus.FINDING_SHIPPER)
                .createdAt(Instant.now())
                .build();

        leg3 = OrderLeg.builder()
                .id(3L)
                .order(order)
                .legNo(3)
                .pickupAddress("Điểm hẹn 2 (Cổng chính FPT)")
                .deliveryAddress("Điểm nhận B")
                .distanceKm(BigDecimal.valueOf(6.0))
                .legFee(BigDecimal.valueOf(30000))
                .courierId(COURIER_3)
                .status(OrderStatus.FINDING_SHIPPER)
                .createdAt(Instant.now())
                .build();

        legs = new ArrayList<>(List.of(leg1, leg2, leg3));
    }

    @Test
    void testGetHandoverQrSuccess() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdOrderByLegNoAsc(ORDER_ID)).thenReturn(legs);

        HandoverQrResponse response = service.getHandoverQr(ORDER_ID, 1, COURIER_1);

        assertNotNull(response);
        assertNotNull(response.getQrToken());
        assertNotNull(response.getOtpCode());
        assertEquals(ORDER_ID, response.getOrderId());
        assertEquals(1, response.getLegNo());
        assertEquals(2, response.getNextLegNo());
        assertEquals(COURIER_1, response.getFromCourierId());
        assertTrue(response.getRemainingSeconds() > 500); // 10 phút PRM-097
    }

    @Test
    void testGetHandoverQrUnassignedCourierFails() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdOrderByLegNoAsc(ORDER_ID)).thenReturn(legs);

        AppException ex = assertThrows(AppException.class, () ->
                service.getHandoverQr(ORDER_ID, 1, 999L)
        );
        assertEquals(OrderErrorCode.HANDOVER_ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void testGetHandoverQrLastLegFails() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdOrderByLegNoAsc(ORDER_ID)).thenReturn(legs);

        AppException ex = assertThrows(AppException.class, () ->
                service.getHandoverQr(ORDER_ID, 3, COURIER_3)
        );
        assertEquals(OrderErrorCode.ORDER_INVALID, ex.getErrorCode());
    }

    @Test
    void testConfirmHandoverSuccess() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdOrderByLegNoAsc(ORDER_ID)).thenReturn(legs);
        when(orderService.uploadOrderImages(any())).thenReturn(List.of("https://fptpost.vn/uploads/handover_photo.jpg"));

        // Sinh token hợp lệ
        HandoverQrResponse qrResponse = service.getHandoverQr(ORDER_ID, 1, COURIER_1);

        HandoverConfirmRequest request = HandoverConfirmRequest.builder()
                .qrToken(qrResponse.getQrToken())
                .meetingPoint("Thư viện FPT - Tòa nhà Alpha")
                .note("Hàng nguyên vẹn, đã kiểm tra")
                .latitude(BigDecimal.valueOf(10.84155))
                .longitude(BigDecimal.valueOf(106.81015))
                .build();

        RelayHandoverResultResponse result = service.confirmHandover(
                ORDER_ID, 1, COURIER_2, request, photo
        );

        assertNotNull(result);
        assertEquals(ORDER_ID, result.getOrderId());
        assertEquals(1, result.getCompletedLegNo());
        assertEquals(2, result.getNextLegNo());
        assertEquals(COURIER_2, result.getNextCourierId());

        // Kiểm tra trạng thái chặng 1 hoàn thành, chặng 2 bắt đầu vận chuyển
        assertEquals(OrderStatus.COMPLETED, leg1.getStatus());
        assertEquals(OrderStatus.IN_DELIVERY, leg2.getStatus());
        assertEquals("https://fptpost.vn/uploads/handover_photo.jpg", leg1.getHandoverPhotoUrl());
        assertNotNull(leg1.getHandoverAt());
        assertEquals(COURIER_2, order.getCourierId());

        verify(orderLegRepository).save(leg1);
        verify(orderLegRepository).save(leg2);
        verify(orderRepository).save(order);
    }

    @Test
    void testConfirmHandoverWithOtpCodeSuccess() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdOrderByLegNoAsc(ORDER_ID)).thenReturn(legs);
        when(orderService.uploadOrderImages(any())).thenReturn(List.of("https://fptpost.vn/uploads/handover_photo.jpg"));

        HandoverQrResponse qrResponse = service.getHandoverQr(ORDER_ID, 1, COURIER_1);

        // Sử dụng mã OTP 6 số để xác nhận
        HandoverConfirmRequest request = HandoverConfirmRequest.builder()
                .qrToken(qrResponse.getOtpCode())
                .meetingPoint("Cổng chính FPT")
                .build();

        RelayHandoverResultResponse result = service.confirmHandover(
                ORDER_ID, 1, COURIER_2, request, photo
        );

        assertNotNull(result);
        assertEquals(OrderStatus.COMPLETED, leg1.getStatus());
        assertEquals(OrderStatus.IN_DELIVERY, leg2.getStatus());
    }

    @Test
    void testConfirmHandoverSelfForbidden() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdOrderByLegNoAsc(ORDER_ID)).thenReturn(legs);

        HandoverConfirmRequest request = HandoverConfirmRequest.builder()
                .qrToken("any-token")
                .build();

        // COURIER_1 tự bàn giao cho COURIER_1
        AppException ex = assertThrows(AppException.class, () ->
                service.confirmHandover(ORDER_ID, 1, COURIER_1, request, photo)
        );
        assertEquals(OrderErrorCode.HANDOVER_SELF_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void testConfirmHandoverInvalidQrTokenFails() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdOrderByLegNoAsc(ORDER_ID)).thenReturn(legs);

        HandoverConfirmRequest request = HandoverConfirmRequest.builder()
                .qrToken("invalid-or-fake-token")
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                service.confirmHandover(ORDER_ID, 1, COURIER_2, request, photo)
        );
        assertEquals(OrderErrorCode.HANDOVER_INVALID_QR, ex.getErrorCode());
    }

    @Test
    void testConfirmHandoverMissingPhotoFails() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderLegRepository.findByOrderIdOrderByLegNoAsc(ORDER_ID)).thenReturn(legs);

        HandoverQrResponse qrResponse = service.getHandoverQr(ORDER_ID, 1, COURIER_1);
        HandoverConfirmRequest request = HandoverConfirmRequest.builder()
                .qrToken(qrResponse.getQrToken())
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                service.confirmHandover(ORDER_ID, 1, COURIER_2, request, null)
        );
        assertEquals(OrderErrorCode.INVALID_IMAGE_FILE, ex.getErrorCode());
    }

    @Test
    void testGetSafeMeetingPoints() {
        List<RelayMeetingPointDto> points = service.getSafeMeetingPoints();
        assertNotNull(points);
        assertFalse(points.isEmpty());
        assertTrue(points.size() >= 4);
        assertTrue(points.stream().anyMatch(p -> p.getName().contains("FPT")));
    }
}

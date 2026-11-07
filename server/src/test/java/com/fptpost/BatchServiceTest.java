package com.fptpost;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.batch.dto.request.BatchCreateRequest;
import com.fptpost.modules.batch.dto.request.RouteOptimizeRequest;
import com.fptpost.modules.batch.dto.response.DeliveryBatchResponse;
import com.fptpost.modules.batch.dto.response.RouteOptimizationResponse;
import com.fptpost.modules.batch.model.BatchStopType;
import com.fptpost.modules.batch.model.DeliveryBatch;
import com.fptpost.modules.batch.repository.DeliveryBatchRepository;
import com.fptpost.modules.batch.repository.DeliveryBatchStopRepository;
import com.fptpost.modules.batch.service.BatchService;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.trip.model.Trip;
import com.fptpost.modules.trip.repository.TripRepository;
import com.fptpost.modules.batch.service.routing.ChCrpRoutingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BatchServiceTest {

    @Mock
    private DeliveryBatchRepository batchRepository;

    @Mock
    private DeliveryBatchStopRepository stopRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TripRepository tripRepository;

    @Spy
    private ChCrpRoutingEngine routingEngine = new ChCrpRoutingEngine();

    @InjectMocks
    private BatchService batchService;

    private User mockCourier;
    private Order order1;
    private Order order2;
    private Order order3;

    @BeforeEach
    void setUp() {
        mockCourier = User.builder()
                .id(100L)
                .email("courier@fptpost.vn")
                .phone("0987654321")
                .publicId(UUID.randomUUID())
                .build();

        order1 = Order.builder()
                .id(1L)
                .orderCode("FPT-261107-000001")
                .courierId(100L)
                .productName("Sách giáo trình")
                .weightKg(BigDecimal.valueOf(2.5))
                .pickupAddress("Cổng Đại học FPT Cần Thơ")
                .pickupLatitude(BigDecimal.valueOf(10.0299))
                .pickupLongitude(BigDecimal.valueOf(105.7705))
                .deliveryAddress("Vincom Plaza Xuân Khánh, Cần Thơ")
                .deliveryLatitude(BigDecimal.valueOf(10.0315))
                .deliveryLongitude(BigDecimal.valueOf(105.7780))
                .receiverName("Trần Văn Nhận 1")
                .status(OrderStatus.IN_DELIVERY)
                .build();

        order2 = Order.builder()
                .id(2L)
                .orderCode("FPT-261107-000002")
                .courierId(100L)
                .productName("Tài liệu thí nghiệm")
                .weightKg(BigDecimal.valueOf(1.0))
                .pickupAddress("Đường 30/4, Ninh Kiều, Cần Thơ")
                .pickupLatitude(BigDecimal.valueOf(10.0340))
                .pickupLongitude(BigDecimal.valueOf(105.7720))
                .deliveryAddress("Bến Ninh Kiều, Cần Thơ")
                .deliveryLatitude(BigDecimal.valueOf(10.0375))
                .deliveryLongitude(BigDecimal.valueOf(105.7865))
                .receiverName("Lê Thị Nhận 2")
                .status(OrderStatus.IN_DELIVERY)
                .build();

        order3 = Order.builder()
                .id(3L)
                .orderCode("FPT-261107-000003")
                .courierId(100L)
                .productName("Hộp quà lưu niệm")
                .weightKg(BigDecimal.valueOf(1.5))
                .pickupAddress("Đại học Cần Thơ")
                .pickupLatitude(BigDecimal.valueOf(10.0305))
                .pickupLongitude(BigDecimal.valueOf(105.7680))
                .deliveryAddress("Chợ nổi Cái Răng, Cần Thơ")
                .deliveryLatitude(BigDecimal.valueOf(10.0050))
                .deliveryLongitude(BigDecimal.valueOf(105.7500))
                .receiverName("Phạm Văn Nhận 3")
                .status(OrderStatus.IN_DELIVERY)
                .build();
    }

    @Test
    @DisplayName("Tối ưu hóa lộ trình: Điểm lấy hàng phải luôn đứng trước điểm giao hàng tương ứng")
    void testOptimizeRoute_PrecedenceConstraint() {
        when(orderRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(order1, order2));

        RouteOptimizeRequest request = RouteOptimizeRequest.builder()
                .orderIds(List.of(1L, 2L))
                .currentLatitude(10.0299)
                .currentLongitude(105.7705)
                .build();

        RouteOptimizationResponse response = batchService.optimizeRoute(request, mockCourier);

        assertNotNull(response);
        assertEquals(2, response.getTotalOrders());
        assertEquals(4, response.getTotalStops());
        assertTrue(response.getTotalDistanceKm().doubleValue() > 0);

        // Kiểm tra ràng buộc tiền đề: PICKUP của đơn phải diễn ra trước DELIVERY của cùng đơn đó
        Map<Long, Integer> pickupOrderIndex = new HashMap<>();
        Map<Long, Integer> deliveryOrderIndex = new HashMap<>();

        for (var stop : response.getStops()) {
            if (stop.getStopType() == BatchStopType.PICKUP) {
                pickupOrderIndex.put(stop.getOrderId(), stop.getStopOrder());
            } else if (stop.getStopType() == BatchStopType.DELIVERY) {
                deliveryOrderIndex.put(stop.getOrderId(), stop.getStopOrder());
            }
        }

        assertTrue(pickupOrderIndex.get(1L) < deliveryOrderIndex.get(1L),
                "Điểm lấy hàng đơn 1 phải đến trước điểm giao hàng đơn 1");
        assertTrue(pickupOrderIndex.get(2L) < deliveryOrderIndex.get(2L),
                "Điểm lấy hàng đơn 2 phải đến trước điểm giao hàng đơn 2");
    }

    @Test
    @DisplayName("Kiểm tra giới hạn gộp: Từ chối nếu số lượng đơn ít hơn 2")
    void testOptimizeRoute_LessThanMinOrders_ThrowsException() {
        RouteOptimizeRequest request = RouteOptimizeRequest.builder()
                .orderIds(List.of(1L))
                .build();

        assertThrows(AppException.class, () -> batchService.optimizeRoute(request, mockCourier));
    }

    @Test
    @DisplayName("Kiểm tra giới hạn gộp: Từ chối nếu số lượng đơn vượt quá 5 đơn")
    void testOptimizeRoute_ExceedsMaxOrders_ThrowsException() {
        RouteOptimizeRequest request = RouteOptimizeRequest.builder()
                .orderIds(List.of(1L, 2L, 3L, 4L, 5L, 6L))
                .build();

        assertThrows(AppException.class, () -> batchService.optimizeRoute(request, mockCourier));
    }

    @Test
    @DisplayName("Kiểm tra sức chứa: Ném ngoại lệ khi tổng khối lượng vượt quá sức chứa chuyến đi")
    void testCreateBatch_ExceedsCapacity_ThrowsException() {
        Trip trip = Trip.builder()
                .id(50L)
                .courier(mockCourier)
                .capacityKg(3.0) // Chuyến đi chỉ có sức chứa tối đa 3.0 kg
                .build();

        when(orderRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(order1, order2, order3));
        when(tripRepository.findById(50L)).thenReturn(Optional.of(trip));

        // Tổng khối lượng = 2.5 + 1.0 + 1.5 = 5.0 kg > 3.0 kg
        BatchCreateRequest request = BatchCreateRequest.builder()
                .orderIds(List.of(1L, 2L, 3L))
                .tripId(50L)
                .build();

        AppException ex = assertThrows(AppException.class, () -> batchService.createBatch(request, mockCourier));
        assertTrue(ex.getMessage().contains("vượt quá sức chứa"));
    }

    @Test
    @DisplayName("Tạo đợt giao gộp thành công khi thỏa mãn toàn bộ điều kiện")
    void testCreateBatch_Success() {
        when(orderRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(order1, order2));
        when(batchRepository.existsByBatchCode(anyString())).thenReturn(false);
        when(batchRepository.save(any(DeliveryBatch.class))).thenAnswer(invocation -> {
            DeliveryBatch saved = invocation.getArgument(0);
            saved.setId(999L);
            return saved;
        });

        BatchCreateRequest request = BatchCreateRequest.builder()
                .orderIds(List.of(1L, 2L))
                .batchName("Giao chiều Ninh Kiều")
                .notes("Hàng giao trước 17h")
                .build();

        DeliveryBatchResponse response = batchService.createBatch(request, mockCourier);

        assertNotNull(response);
        assertEquals(999L, response.getId());
        assertNotNull(response.getBatchCode());
        assertTrue(response.getBatchCode().startsWith("BAT-"));
        assertEquals("Giao chiều Ninh Kiều", response.getBatchName());
        assertEquals(2, response.getTotalOrders());
        assertEquals(4, response.getStops().size());
        verify(batchRepository, times(1)).save(any(DeliveryBatch.class));
    }
}

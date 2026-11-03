package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.catalog.model.SystemParameter;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.VehicleType;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.matching.dto.response.OrderCourierMatchResponse;
import com.fptpost.modules.matching.dto.response.TripOrderMatchResponse;
import com.fptpost.modules.matching.repository.TripOrderMatchRepository;
import com.fptpost.modules.matching.service.MatchingService;
import com.fptpost.modules.order.model.DeliveryUrgency;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.IOfferService;
import com.fptpost.modules.order.service.OrderService;
import com.fptpost.modules.trip.model.Trip;
import com.fptpost.modules.trip.model.TripStatus;
import com.fptpost.modules.trip.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TripOrderMatchRepository tripOrderMatchRepository;

    @Mock
    private CourierProfileRepository courierProfileRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private SystemParameterRepository systemParameterRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private IOfferService offerService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MatchingService matchingService;

    private User courierUser;
    private User senderUser;
    private Trip trip;
    private Order orderNear;
    private Order orderFar;

    @BeforeEach
    void setUp() {
        courierUser = User.builder()
                .id(101L)
                .publicId(UUID.randomUUID())
                .email("courier@fptpost.vn")
                .phone("+84901234567")
                .build();

        senderUser = User.builder()
                .id(202L)
                .publicId(UUID.randomUUID())
                .email("sender@fptpost.vn")
                .phone("+84909876543")
                .build();

        // Chuyến đi từ Cầu Giấy (21.0333, 105.7933) tới Hoàn Kiếm (21.0285, 105.8542)
        trip = Trip.builder()
                .id(1L)
                .courier(courierUser)
                .origin("Cầu Giấy, Hà Nội")
                .originLat(21.0333)
                .originLng(105.7933)
                .destination("Hoàn Kiếm, Hà Nội")
                .destinationLat(21.0285)
                .destinationLng(105.8542)
                .departAt(Instant.now().plusSeconds(3600))
                .vehicleType(VehicleType.MOTORBIKE)
                .capacityKg(15.0)
                .status(TripStatus.SCHEDULED)
                .distanceKm(7.5)
                .build();

        // Đơn hàng gần lộ trình (Ba Đình: 21.0345, 105.8200 -> Hoàn Kiếm: 21.0290, 105.8520) - Lệch rất ít (< 1km)
        orderNear = Order.builder()
                .id(501L)
                .orderCode("FPT-261102-000501")
                .senderId(senderUser.getId())
                .productName("Tài liệu hợp đồng")
                .weightKg(new BigDecimal("1.2"))
                .pickupAddress("Kim Mã, Ba Đình, Hà Nội")
                .pickupLatitude(new BigDecimal("21.0345"))
                .pickupLongitude(new BigDecimal("105.8200"))
                .deliveryAddress("Tràng Tiền, Hoàn Kiếm, Hà Nội")
                .deliveryLatitude(new BigDecimal("21.0290"))
                .deliveryLongitude(new BigDecimal("105.8520"))
                .priceSuggested(new BigDecimal("35000"))
                .status(OrderStatus.FINDING_SHIPPER)
                .urgency(DeliveryUrgency.STANDARD)
                .createdAt(Instant.now())
                .build();

        // Đơn hàng xa lộ trình (Hà Đông: 20.9700, 105.7700 -> Thanh Trì: 20.9400, 105.8500) - Lệch > 10km
        orderFar = Order.builder()
                .id(502L)
                .orderCode("FPT-261102-000502")
                .senderId(senderUser.getId())
                .productName("Kiện hàng xa")
                .weightKg(new BigDecimal("3.0"))
                .pickupAddress("Hà Đông, Hà Nội")
                .pickupLatitude(new BigDecimal("20.9700"))
                .pickupLongitude(new BigDecimal("105.7700"))
                .deliveryAddress("Thanh Trì, Hà Nội")
                .deliveryLatitude(new BigDecimal("20.9400"))
                .deliveryLongitude(new BigDecimal("105.8500"))
                .priceSuggested(new BigDecimal("50000"))
                .status(OrderStatus.FINDING_SHIPPER)
                .urgency(DeliveryUrgency.STANDARD)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("API-053: Gợi ý đơn hàng cho chuyến đi lọc chính xác độ lệch <= 3km")
    void testGetOrdersForTrip_FiltersDetourCorrectly() {
        when(tripRepository.findByIdAndCourierId(1L, courierUser.getId())).thenReturn(Optional.of(trip));
        when(systemParameterRepository.findByKey("MATCH_DETOUR"))
                .thenReturn(Optional.of(SystemParameter.builder().key("MATCH_DETOUR").value("3.0").build()));
        when(systemParameterRepository.findByKey("MATCH_TOPN"))
                .thenReturn(Optional.of(SystemParameter.builder().key("MATCH_TOPN").value("10").build()));
        when(courierProfileRepository.findByUserId(courierUser.getId()))
                .thenReturn(Optional.of(CourierProfile.builder().userId(courierUser.getId()).tier(CourierTier.GOLD).build()));

        when(orderRepository.findAvailableShippingOrder(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(orderNear, orderFar)));

        List<TripOrderMatchResponse> results = matchingService.getOrdersForTrip(1L, courierUser);

        assertNotNull(results);
        assertEquals(1, results.size(), "Chỉ có đơn hàng tiện đường độ lệch <= 3km được trả về");
        assertEquals(orderNear.getId(), results.get(0).getId());
        assertTrue(results.get(0).getDetourKm() <= 3.0, "Độ lệch phải <= 3.0 km");
        assertTrue(results.get(0).getMatchScore() >= 60.0, "Điểm phù hợp phải cao cho đơn gần tuyến");
        assertNotNull(results.get(0).getMatchReasons(), "Phải có danh sách lý do gợi ý phù hợp");
    }

    @Test
    @DisplayName("API-054: Gợi ý chuyến đi cho đơn hàng trả về danh sách tài xế tiện đường")
    void testGetCouriersForOrder_ReturnsMatchingTrips() {
        when(orderRepository.findById(501L)).thenReturn(Optional.of(orderNear));
        when(tripRepository.findByStatusAndDepartAtAfter(eq(TripStatus.SCHEDULED), any(Instant.class)))
                .thenReturn(List.of(trip));
        when(courierProfileRepository.findByUserId(courierUser.getId()))
                .thenReturn(Optional.of(CourierProfile.builder()
                        .userId(courierUser.getId())
                        .tier(CourierTier.GOLD)
                        .trustScore(BigDecimal.valueOf(95.0))
                        .completedOrders(88)
                        .build()));
        when(userProfileRepository.findById(courierUser.getId()))
                .thenReturn(Optional.of(UserProfile.builder()
                        .userId(courierUser.getId())
                        .fullName("Nguyễn Văn Tài Xế")
                        .build()));

        List<OrderCourierMatchResponse> results = matchingService.getCouriersForOrder(501L, senderUser);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(trip.getId(), results.get(0).getTripId());
        assertEquals("Nguyễn Văn Tài Xế", results.get(0).getCourierName());
        assertTrue(results.get(0).getDetourKm() <= 3.0);
        assertTrue(results.get(0).getMatchScore() > 0);
    }
}

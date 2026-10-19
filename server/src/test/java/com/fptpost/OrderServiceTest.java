package com.fptpost;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.dto.request.OrderRequest;
import com.fptpost.modules.order.dto.response.OrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
    private final com.fptpost.modules.identity.repository.CourierProfileRepository courierProfileRepository =
            mock(com.fptpost.modules.identity.repository.CourierProfileRepository.class);
    private final OrderService service = new OrderService(repository, mapper, courierProfileRepository);

    @Test
    void createOrder_shouldGenerateOrderCodeAndSaveSuccessfully() {
        var request = OrderRequest.builder()
                .senderId(1L)
                .pickupAddress("123 Le Loi, Da Nang")
                .deliveryAddress("456 Nguyen Hue, Da Nang")
                .receiverName("Nguyen Van A")
                .receiverPhone("0901234567")
                .productName("Ao thun FPT")
                .quantity(2)
                .weightKg(new BigDecimal("0.500"))
                .declaredValue(new BigDecimal("200000.00"))
                .imageUrls(List.of("https://example.com/img1.jpg"))
                .build();

        when(repository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        OrderResponse response = service.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getOrderCode()).isNotBlank().startsWith("FP");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getImageUrls()).containsExactly("https://example.com/img1.jpg");
        verify(repository).save(any(Order.class));
    }

    @Test
    void createOrder_whenGeneratedCodeCollides_shouldRegenerateUniqueCode() {
        var request = OrderRequest.builder()
                .senderId(1L)
                .pickupAddress("123 Le Loi")
                .deliveryAddress("456 Nguyen Hue")
                .receiverName("Nguyen Van A")
                .receiverPhone("0901234567")
                .productName("Sach")
                .quantity(1)
                .weightKg(new BigDecimal("0.300"))
                .build();

        when(repository.existsByOrderCode(anyString())).thenReturn(true, false);
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.createOrder(request);

        assertThat(response.getOrderCode()).isNotBlank().startsWith("FP");
        verify(repository, times(2)).existsByOrderCode(anyString());
        verify(repository).save(any(Order.class));
    }

    @Test
    void getOrderById_shouldReturnOrder_whenFound() {
        Order order = Order.builder()
                .id(1L)
                .orderCode("ORD001")
                .senderId(10L)
                .productName("Laptop")
                .status(OrderStatus.PENDING)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = service.getOrderById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOrderCode()).isEqualTo("ORD001");
    }

    @Test
    void getOrderById_shouldThrowException_whenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrderById(99L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void getAllOrders_shouldReturnList() {
        Order order1 = Order.builder().id(1L).orderCode("ORD001").status(OrderStatus.PENDING).build();
        Order order2 = Order.builder().id(2L).orderCode("ORD002").status(OrderStatus.COMPLETED).build();

        when(repository.findAll()).thenReturn(List.of(order1, order2));

        List<OrderResponse> results = service.getAllOrders();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getOrderCode()).isEqualTo("ORD001");
        assertThat(results.get(1).getOrderCode()).isEqualTo("ORD002");
    }

    @Test
    void uploadOrderImages_shouldUploadAndReturnUrls_whenValidImage() {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "files",
                "test-parcel.jpg",
                "image/jpeg",
                "sample-image-bytes".getBytes()
        );

        List<String> urls = service.uploadOrderImages(List.of(file));

        assertThat(urls).isNotEmpty();
        assertThat(urls.get(0)).contains("/uploads/orders/").endsWith(".jpg");
    }

    @Test
    void uploadOrderImages_shouldThrowException_whenInvalidMimeType() {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "files",
                "document.pdf",
                "application/pdf",
                "dummy-bytes".getBytes()
        );

        assertThatThrownBy(() -> service.uploadOrderImages(List.of(file)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.INVALID_IMAGE_FILE));
    }

    @Test
    void uploadOrderImages_shouldThrowException_whenEmptyList() {
        assertThatThrownBy(() -> service.uploadOrderImages(List.of()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.INVALID_IMAGE_FILE));
    }

    // ==========================================
    // UC-17 Unit Tests
    // ==========================================

    @Test
    void acceptMarketOrder_shouldSucceed_whenCourierEligible() {
        var courierRole = com.fptpost.modules.identity.model.Role.builder().code("COURIER").build();
        var courier = com.fptpost.modules.identity.model.User.builder()
                .id(2L)
                .status(com.fptpost.modules.identity.enums.UserStatus.ACTIVE)
                .roles(java.util.Set.of(courierRole))
                .build();

        var profile = com.fptpost.modules.identity.model.CourierProfile.builder()
                .id(1L)
                .user(courier)
                .tier(com.fptpost.modules.identity.enums.CourierTier.SILVER)
                .trustScore(80)
                .build();

        var order = Order.builder()
                .id(100L)
                .orderCode("FP100")
                .senderId(1L)
                .status(OrderStatus.FINDING_SHIPPER)
                .declaredValue(new BigDecimal("500000"))
                .priceSuggested(new BigDecimal("35000"))
                .pickupAddress("Da Nang")
                .deliveryAddress("Hue")
                .productName("Giay the thao")
                .quantity(1)
                .weightKg(new BigDecimal("1.0"))
                .build();

        when(repository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(courierProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(repository.countByCourierIdAndStatusIn(eq(2L), any())).thenReturn(1L);
        when(repository.sumDeclaredValueByCourierIdAndStatusIn(eq(2L), any())).thenReturn(new BigDecimal("300000"));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.acceptMarketOrder(100L, courier);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.courierId()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(result.priceAgreed()).isEqualTo(new BigDecimal("35000"));
        verify(repository).save(any(Order.class));
    }

    @Test
    void acceptMarketOrder_shouldThrowException_whenUserNotCourier() {
        var senderRole = com.fptpost.modules.identity.model.Role.builder().code("SENDER").build();
        var user = com.fptpost.modules.identity.model.User.builder()
                .id(3L)
                .status(com.fptpost.modules.identity.enums.UserStatus.ACTIVE)
                .roles(java.util.Set.of(senderRole))
                .build();

        assertThatThrownBy(() -> service.acceptMarketOrder(100L, user))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.COURIER_ROLE_REQUIRED));
    }

    @Test
    void acceptMarketOrder_shouldThrowException_whenSelfAccept() {
        var courierRole = com.fptpost.modules.identity.model.Role.builder().code("COURIER").build();
        var courier = com.fptpost.modules.identity.model.User.builder()
                .id(1L) // Same as senderId
                .status(com.fptpost.modules.identity.enums.UserStatus.ACTIVE)
                .roles(java.util.Set.of(courierRole))
                .build();

        var order = Order.builder()
                .id(100L)
                .senderId(1L)
                .status(OrderStatus.FINDING_SHIPPER)
                .build();

        when(repository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.acceptMarketOrder(100L, courier))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_SELF_ACCEPT_FORBIDDEN));
    }

    @Test
    void acceptMarketOrder_shouldThrowException_whenOrderNotAvailable() {
        var courierRole = com.fptpost.modules.identity.model.Role.builder().code("COURIER").build();
        var courier = com.fptpost.modules.identity.model.User.builder()
                .id(2L)
                .status(com.fptpost.modules.identity.enums.UserStatus.ACTIVE)
                .roles(java.util.Set.of(courierRole))
                .build();

        var order = Order.builder()
                .id(100L)
                .senderId(1L)
                .status(OrderStatus.SHIPPER_ASSIGNED) // Already assigned!
                .courierId(99L)
                .build();

        when(repository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.acceptMarketOrder(100L, courier))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_AVAILABLE));
    }

    @Test
    void acceptMarketOrder_shouldThrowException_whenHighValueAndTierBelowSilver() {
        var courierRole = com.fptpost.modules.identity.model.Role.builder().code("COURIER").build();
        var courier = com.fptpost.modules.identity.model.User.builder()
                .id(2L)
                .status(com.fptpost.modules.identity.enums.UserStatus.ACTIVE)
                .roles(java.util.Set.of(courierRole))
                .build();

        var profile = com.fptpost.modules.identity.model.CourierProfile.builder()
                .id(1L)
                .user(courier)
                .tier(com.fptpost.modules.identity.enums.CourierTier.NEW) // Tân binh cannot take > 1M
                .build();

        var order = Order.builder()
                .id(100L)
                .senderId(1L)
                .status(OrderStatus.FINDING_SHIPPER)
                .declaredValue(new BigDecimal("1500000")) // > 1,000,000
                .build();

        when(repository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(courierProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.acceptMarketOrder(100L, courier))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.COURIER_HIGH_VALUE_RESTRICTED));
    }

    @Test
    void acceptMarketOrder_shouldThrowException_whenMaxOrdersExceeded() {
        var courierRole = com.fptpost.modules.identity.model.Role.builder().code("COURIER").build();
        var courier = com.fptpost.modules.identity.model.User.builder()
                .id(2L)
                .status(com.fptpost.modules.identity.enums.UserStatus.ACTIVE)
                .roles(java.util.Set.of(courierRole))
                .build();

        var profile = com.fptpost.modules.identity.model.CourierProfile.builder()
                .id(1L)
                .user(courier)
                .tier(com.fptpost.modules.identity.enums.CourierTier.NEW) // Max 3
                .build();

        var order = Order.builder()
                .id(100L)
                .senderId(1L)
                .status(OrderStatus.FINDING_SHIPPER)
                .declaredValue(new BigDecimal("100000"))
                .build();

        when(repository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(courierProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        // Already holding 3 orders
        when(repository.countByCourierIdAndStatusIn(eq(2L), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.acceptMarketOrder(100L, courier))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.COURIER_MAX_ORDERS_EXCEEDED));
    }

    @Test
    void acceptMarketOrder_shouldSucceed_byOrderCode() {
        var courierRole = com.fptpost.modules.identity.model.Role.builder().code("COURIER").build();
        var courier = com.fptpost.modules.identity.model.User.builder()
                .id(2L)
                .status(com.fptpost.modules.identity.enums.UserStatus.ACTIVE)
                .roles(java.util.Set.of(courierRole))
                .build();

        var profile = com.fptpost.modules.identity.model.CourierProfile.builder()
                .id(1L)
                .user(courier)
                .tier(com.fptpost.modules.identity.enums.CourierTier.SILVER)
                .build();

        var order = Order.builder()
                .id(200L)
                .orderCode("ORD-TEST-002")
                .senderId(1L)
                .status(OrderStatus.FINDING_SHIPPER)
                .declaredValue(new BigDecimal("250000"))
                .priceSuggested(new BigDecimal("30000"))
                .pickupAddress("Ha Noi")
                .deliveryAddress("Da Nang")
                .productName("Sach")
                .quantity(1)
                .weightKg(new BigDecimal("0.5"))
                .build();

        when(repository.findByOrderCodeForUpdate("ORD-TEST-002")).thenReturn(Optional.of(order));
        when(courierProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(repository.countByCourierIdAndStatusIn(eq(2L), any())).thenReturn(0L);
        when(repository.sumDeclaredValueByCourierIdAndStatusIn(eq(2L), any())).thenReturn(BigDecimal.ZERO);
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.acceptMarketOrder("ORD-TEST-002", courier);

        assertThat(result).isNotNull();
        assertThat(result.orderCode()).isEqualTo("ORD-TEST-002");
        assertThat(result.courierId()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void getMarketOrderDetail_shouldSucceed_byOrderCode() {
        var order = Order.builder()
                .id(200L)
                .orderCode("ORD-TEST-002")
                .senderId(1L)
                .status(OrderStatus.FINDING_SHIPPER)
                .declaredValue(new BigDecimal("250000"))
                .priceSuggested(new BigDecimal("30000"))
                .pickupAddress("Ha Noi")
                .deliveryAddress("Da Nang")
                .receiverName("Tran Van B")
                .receiverPhone("0912345678")
                .productName("Sach")
                .quantity(1)
                .weightKg(new BigDecimal("0.5"))
                .build();

        when(repository.findByOrderCode("ORD-TEST-002")).thenReturn(Optional.of(order));

        var detail = service.getMarketOrderDetail("ORD-TEST-002", null);

        assertThat(detail).isNotNull();
        assertThat(detail.orderCode()).isEqualTo("ORD-TEST-002");
        assertThat(detail.maskedReceiverName()).isEqualTo("Tran ***");
        assertThat(detail.maskedReceiverPhone()).isEqualTo("0912***678");
        assertThat(detail.isAvailable()).isTrue();
    }
}

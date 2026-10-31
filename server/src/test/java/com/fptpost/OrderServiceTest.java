package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.dto.OrderRequest;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.OrderService;
import com.fptpost.modules.order.service.PricingCalculatorService;
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
    private final PricingCalculatorService pricingCalculatorService = new PricingCalculatorService(new ObjectMapper());
    private final com.fptpost.modules.identity.repository.CourierProfileRepository courierProfileRepository = mock(com.fptpost.modules.identity.repository.CourierProfileRepository.class);
    private final OrderService service = new OrderService(repository, mapper, pricingCalculatorService, courierProfileRepository);

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
        assertThat(response.getPriceSuggested()).isNotNull();
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
}

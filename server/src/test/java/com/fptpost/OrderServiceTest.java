package com.fptpost;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.dto.ShippingOrderRequest;
import com.fptpost.modules.order.dto.ShippingOrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.model.ShippingOrder;
import com.fptpost.modules.order.repository.ShippingOrderRepository;
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

    private final ShippingOrderRepository repository = mock(ShippingOrderRepository.class);
    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
    private final OrderService service = new OrderService(repository, mapper);

    @Test
    void createOrder_shouldGenerateOrderCodeAndSaveSuccessfully() {
        var request = ShippingOrderRequest.builder()
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

        when(repository.save(any(ShippingOrder.class))).thenAnswer(invocation -> {
            ShippingOrder saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        ShippingOrderResponse response = service.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getOrderCode()).isNotBlank().startsWith("FP");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getImageUrls()).containsExactly("https://example.com/img1.jpg");
        verify(repository).save(any(ShippingOrder.class));
    }

    @Test
    void createOrder_withExistingOrderCode_shouldThrowException() {
        var request = ShippingOrderRequest.builder()
                .orderCode("ORDER123")
                .senderId(1L)
                .build();

        when(repository.existsByOrderCode("ORDER123")).thenReturn(true);

        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_CODE_ALREADY_EXISTS));

        verify(repository, never()).save(any());
    }

    @Test
    void getOrderById_shouldReturnOrder_whenFound() {
        ShippingOrder order = ShippingOrder.builder()
                .id(1L)
                .orderCode("ORD001")
                .senderId(10L)
                .productName("Laptop")
                .status(OrderStatus.PENDING)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(order));

        ShippingOrderResponse response = service.getOrderById(1L);

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
        ShippingOrder order1 = ShippingOrder.builder().id(1L).orderCode("ORD001").status(OrderStatus.PENDING).build();
        ShippingOrder order2 = ShippingOrder.builder().id(2L).orderCode("ORD002").status(OrderStatus.COMPLETED).build();

        when(repository.findAll()).thenReturn(List.of(order1, order2));

        List<ShippingOrderResponse> results = service.getAllOrders();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getOrderCode()).isEqualTo("ORD001");
        assertThat(results.get(1).getOrderCode()).isEqualTo("ORD002");
    }
}

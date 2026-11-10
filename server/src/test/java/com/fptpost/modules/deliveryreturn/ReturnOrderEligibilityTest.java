package com.fptpost.modules.deliveryreturn;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.batch.dto.request.RouteOptimizeRequest;
import com.fptpost.modules.batch.repository.*;
import com.fptpost.modules.batch.service.BatchService;
import com.fptpost.modules.batch.service.routing.ChCrpRoutingEngine;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.trip.repository.TripRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnOrderEligibilityTest {
    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = {"DELIVERY_FAILED", "RETURNING", "RETURNED"})
    void returnWorkflowOrdersCannotBeUsedForDeliveryBatch(OrderStatus status) {
        var orders = mock(OrderRepository.class); var engine = mock(ChCrpRoutingEngine.class);
        var service = new BatchService(mock(DeliveryBatchRepository.class), mock(DeliveryBatchStopRepository.class), orders, mock(TripRepository.class), engine);
        var courier = User.builder().id(302L).build();
        var returned = Order.builder().id(1L).courierId(302L).status(status).build();
        var normal = Order.builder().id(2L).courierId(302L).status(OrderStatus.IN_DELIVERY).build();
        when(orders.findByCourierIdOrderByCreatedAtDesc(302L)).thenReturn(List.of(returned, normal));
        assertThat(service.getEligibleOrders(courier)).extracting(o -> o.getId()).containsExactly(2L);
        when(orders.findAllById(List.of(1L, 2L))).thenReturn(List.of(returned, normal));
        var request = new RouteOptimizeRequest(); request.setOrderIds(List.of(1L, 2L));
        assertThatThrownBy(() -> service.optimizeRoute(request, courier)).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_AVAILABLE));
        verifyNoInteractions(engine);
    }
}

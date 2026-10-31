
package com.fptpost.modules.chat.service;

import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatWritePolicyTest {

    private final ChatWritePolicy policy = new ChatWritePolicy();

    private final Instant endedAt =
            Instant.parse("2026-10-25T08:00:00Z");

    private OrderResponse order(
            OrderStatus status,
            Instant endedAt
    ) {
        OrderResponse order = mock(OrderResponse.class);

        when(order.getStatus()).thenReturn(status);

        // Chỉ stub endedAt khi trạng thái đã kết thúc.
        if (status == OrderStatus.COMPLETED
                || status == OrderStatus.CANCELLED) {
            when(order.getEndedAt()).thenReturn(endedAt);
        }

        return order;
    }

    @Test
    void activeOrder_shouldRemainWritable() {
        OrderResponse order = mock(OrderResponse.class);

        when(order.getStatus()).thenReturn(null);

        assertFalse(policy.isReadOnly(order, Instant.now()));
    }

    @Test
    void completed24Hours_shouldRemainWritable() {
        OrderResponse order =
                order(OrderStatus.COMPLETED, endedAt);

        assertFalse(
                policy.isReadOnly(
                        order,
                        endedAt.plus(Duration.ofHours(24))
                )
        );
    }

    @Test
    void completedBefore48Hours_shouldRemainWritable() {
        OrderResponse order =
                order(OrderStatus.COMPLETED, endedAt);

        Instant now = endedAt.plus(Duration.ofHours(48))
                .minusSeconds(60);

        assertFalse(policy.isReadOnly(order, now));
    }

    @Test
    void completedExactly48Hours_shouldBecomeReadOnly() {
        OrderResponse order =
                order(OrderStatus.COMPLETED, endedAt);

        assertTrue(
                policy.isReadOnly(
                        order,
                        endedAt.plus(Duration.ofHours(48))
                )
        );
    }

    @Test
    void cancelledAfter48Hours_shouldBecomeReadOnly() {
        OrderResponse order =
                order(OrderStatus.CANCELLED, endedAt);

        assertTrue(
                policy.isReadOnly(
                        order,
                        endedAt.plus(Duration.ofHours(49))
                )
        );
    }

    @Test
    void endedOrderWithoutEndedAt_shouldBeReadOnly() {
        OrderResponse order =
                order(OrderStatus.COMPLETED, null);

        assertTrue(policy.isReadOnly(order, Instant.now()));
        assertNull(policy.getReadOnlyAt(order));
    }

    @Test
    void completedOrder_shouldCalculateCorrectDeadline() {
        OrderResponse order =
                order(OrderStatus.COMPLETED, endedAt);

        assertEquals(
                endedAt.plus(Duration.ofHours(48)),
                policy.getReadOnlyAt(order)
        );
    }
}

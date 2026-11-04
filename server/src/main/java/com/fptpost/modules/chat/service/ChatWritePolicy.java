package com.fptpost.modules.chat.service;

import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class ChatWritePolicy {

    public boolean isEnded(OrderResponse order) {
        return order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.CANCELLED;
    }

    public Instant getReadOnlyAt(OrderResponse order) {
        if (!isEnded(order) || order.getEndedAt() == null) {
            return null;
        }

        return order.getEndedAt().plus(Duration.ofHours(48));
    }

    public boolean isReadOnly(OrderResponse order, Instant now) {
        if (!isEnded(order)) {
            return false;
        }

        Instant readOnlyAt = getReadOnlyAt(order);

        // Đơn đã kết thúc nhưng thiếu mốc thời gian:
        // giữ trạng thái khóa, không tự cấp thêm 48 giờ.
        return readOnlyAt == null || !now.isBefore(readOnlyAt);
    }
}
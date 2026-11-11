package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayHandoverResultResponse {
    private Long orderId;
    private String orderCode;
    private Integer completedLegNo;
    private Integer nextLegNo;
    private Long previousCourierId;
    private Long nextCourierId;
    private Instant handoverAt;
    private String handoverPhotoUrl;
    private String handoverMeetingPoint;
    private String handoverNote;
    private OrderStatus orderStatus;
    private String message;
    private List<OrderLegResponse> legs;
}

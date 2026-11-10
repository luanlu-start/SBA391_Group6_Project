package com.fptpost.modules.moderation.dto.response;

import com.fptpost.modules.moderation.enums.OrderModerationStatus;
import com.fptpost.modules.order.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Đơn trong hàng đợi kiểm duyệt (UC-54)")
public class OrderModerationResponse {
    Long orderId;
    String orderCode;
    OrderModerationStatus status;
    OrderStatus orderStatus;
    String productName;
    String productDescription;
    String deliveryNote;
    Integer quantity;
    BigDecimal weightKg;
    BigDecimal declaredValue;
    String categoryName;
    @Schema(description = "PROHIBITED hoặc REQUIRES_REVIEW khi danh mục hàng cần kiểm tra")
    String categoryFlag;
    List<String> matchedTerms;
    List<String> imageUrls;
    ModerationPartyResponse sender;
    @Schema(description = "Số đơn của người gửi bị từ chối trong cửa sổ cảnh báo lặp lại")
    long senderRecentRejections;
    Instant submittedAt;
    ModerationPartyResponse reviewer;
    Instant reviewedAt;
    String rejectionReason;
    boolean canDecide;
}

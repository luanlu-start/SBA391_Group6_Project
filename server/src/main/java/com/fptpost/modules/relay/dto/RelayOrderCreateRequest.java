package com.fptpost.modules.relay.dto;

import com.fptpost.modules.order.model.DeliveryUrgency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayOrderCreateRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String productName;

    private String productDescription;

    @NotNull(message = "Khối lượng không được để trống")
    @DecimalMin(value = "0.01", message = "Khối lượng phải lớn hơn 0")
    private BigDecimal weightKg;

    private BigDecimal declaredValue;

    @NotBlank(message = "Địa chỉ nhận hàng (đích) không được để trống")
    private String deliveryAddress;

    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;

    @NotBlank(message = "Tên người nhận không được để trống")
    private String receiverName;

    @NotBlank(message = "Số điện thoại người nhận không được để trống")
    private String receiverPhone;

    @NotBlank(message = "Địa chỉ lấy hàng (gốc) không được để trống")
    private String pickupAddress;

    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;

    private BigDecimal totalPrice;
    private BigDecimal priceSuggested;
    private DeliveryUrgency urgency;
    private Integer quantity;
    private Long categoryId;
    private List<String> imageUrls;

    private String deliveryNote;

    /**
     * Danh sách các điểm tiếp sức trung gian (UC-32).
     */
    private List<RelayPointDto> relayPoints;

    /**
     * Danh sách các chặng tiếp sức (tối đa 3 chặng theo quy định).
     */
    private List<LegPlanDto> legs;

    public BigDecimal getFinalPrice() {
        return totalPrice != null ? totalPrice : priceSuggested;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegPlanDto {
        private Integer legNo;
        private Long meetingPointId;
        private String startAddress;
        private BigDecimal startLatitude;
        private BigDecimal startLongitude;
        private String endAddress;
        private BigDecimal endLatitude;
        private BigDecimal endLongitude;
        private BigDecimal distanceKm;
        private BigDecimal fee;
        private Long assignedCourierId;
    }
}

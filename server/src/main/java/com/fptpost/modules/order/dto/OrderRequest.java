package com.fptpost.modules.order.dto;

import com.fptpost.modules.order.model.DeliveryUrgency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class OrderRequest {

    private Long senderId;

    @NotBlank(message = "Pickup address is required")
    @Size(max = 500, message = "Pickup address must not exceed 500 characters")
    private String pickupAddress;

    private BigDecimal pickupLatitude;

    private BigDecimal pickupLongitude;

    @NotBlank(message = "Delivery address is required")
    @Size(max = 500, message = "Delivery address must not exceed 500 characters")
    private String deliveryAddress;

    private BigDecimal deliveryLatitude;

    private BigDecimal deliveryLongitude;

    @NotBlank(message = "Receiver name is required")
    @Size(max = 100, message = "Receiver name must not exceed 100 characters")
    private String receiverName;

    @NotBlank(message = "Receiver phone is required")
    @Size(max = 20, message = "Receiver phone must not exceed 20 characters")
    private String receiverPhone;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String productName;

    @Size(max = 1000, message = "Product description must not exceed 1000 characters")
    private String productDescription;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.001", message = "Weight must be greater than 0")
    private BigDecimal weightKg;

    @DecimalMin(value = "0.0", inclusive = true, message = "Declared value must be greater than or equal to 0")
    private BigDecimal declaredValue;

    private BigDecimal priceSuggested;

    private BigDecimal distanceKm;

    private DeliveryUrgency urgency;

    @Size(max = 500, message = "Delivery note must not exceed 500 characters")
    private String deliveryNote;

    private List<String> imageUrls;
}

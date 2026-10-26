package com.fptpost.modules.order.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferCounterRequest {

    @NotNull(message = "Giá phản đề xuất không được để trống")
    @DecimalMin(value = "1000", message = "Giá phản đề xuất tối thiểu là 1.000đ")
    private BigDecimal price;

    @Size(max = 500, message = "Lời nhắn tối đa 500 ký tự")
    private String message;
}

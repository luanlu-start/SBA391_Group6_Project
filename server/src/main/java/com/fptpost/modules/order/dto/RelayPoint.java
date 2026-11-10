package com.fptpost.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayPoint {

    @NotBlank(message = "Địa chỉ điểm hẹn tiếp sức không được để trống")
    private String address;

    @NotNull(message = "Vĩ độ điểm hẹn không được để trống")
    private BigDecimal latitude;

    @NotNull(message = "Kinh độ điểm hẹn không được để trống")
    private BigDecimal longitude;

    private String name;
}

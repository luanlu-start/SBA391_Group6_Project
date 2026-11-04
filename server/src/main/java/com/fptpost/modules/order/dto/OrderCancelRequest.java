package com.fptpost.modules.order.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelRequest {

    @Size(max = 255, message = "Lý do hủy đơn không được vượt quá 255 ký tự")
    private String reason;
}

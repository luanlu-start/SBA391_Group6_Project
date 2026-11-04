package com.fptpost.modules.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationCreateRequest {

    @NotNull(message = "Vui lòng chọn người giao hàng")
    private Long courierId;

    @Size(max = 500, message = "Lời nhắn gửi người giao không được vượt quá 500 ký tự")
    private String message;

    private Integer expiryHours;
}

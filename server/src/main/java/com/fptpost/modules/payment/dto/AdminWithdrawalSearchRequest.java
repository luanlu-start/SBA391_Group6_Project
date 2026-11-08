package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bộ lọc hàng đợi rút tiền của Admin")
public class AdminWithdrawalSearchRequest {

    @Schema(description = "OPEN (mặc định), ALL hoặc một trạng thái cụ thể", example = "OPEN")
    private String status;

    @Size(max = 100)
    @Schema(description = "Mã yêu cầu, số tài khoản hoặc tên chủ tài khoản")
    private String q;

    @Builder.Default
    @Min(0)
    private Integer page = 0;

    @Builder.Default
    @Min(1)
    @Max(100)
    private Integer size = 20;
}

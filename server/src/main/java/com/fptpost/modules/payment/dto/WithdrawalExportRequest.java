package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Danh sách yêu cầu đã duyệt cần xuất lệnh chi; để trống để xuất toàn bộ (tối đa 200)")
public class WithdrawalExportRequest {

    @Size(max = 200)
    private List<@NotNull Long> ids;
}

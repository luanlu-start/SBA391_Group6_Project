package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.WithdrawalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Phản hồi thông tin yêu cầu rút tiền (UC-40, API-097, API-098, API-099).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết yêu cầu rút tiền")
public class WithdrawalResponse {

    @Schema(description = "ID yêu cầu rút tiền", example = "1")
    private Long id;

    @Schema(description = "Mã yêu cầu rút tiền", example = "WDR-20261024-ABCD1234")
    private String requestCode;

    @Schema(description = "ID người dùng yêu cầu rút", example = "1")
    private Long userId;

    @Schema(description = "Số tiền yêu cầu rút (VND)", example = "200000")
    private Long amount;

    @Schema(description = "Phí rút tiền (VND)", example = "0")
    private Long feeAmount;

    @Schema(description = "Số tiền thực nhận sau phí (VND)", example = "200000")
    private Long netAmount;

    @Schema(description = "Tên ngân hàng thụ hưởng", example = "Vietcombank")
    private String bankName;

    @Schema(description = "Số tài khoản ngân hàng thụ hưởng", example = "0123456789")
    private String accountNumber;

    @Schema(description = "Tên chủ tài khoản thụ hưởng", example = "NGUYEN VAN A")
    private String accountHolderName;

    @Schema(description = "Trạng thái yêu cầu", example = "PENDING_REVIEW")
    private WithdrawalStatus status;

    @Schema(description = "Lý do từ chối nếu có")
    private String rejectionReason;

    @Schema(description = "Thời gian tạo yêu cầu")
    private Instant createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất")
    private Instant updatedAt;
}

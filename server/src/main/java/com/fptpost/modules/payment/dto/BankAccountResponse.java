package com.fptpost.modules.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response thông tin tài khoản ngân hàng nhận tiền đã che số (UC-39, API-094, API-095).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin tài khoản ngân hàng nhận tiền (đã che số tài khoản)")
public class BankAccountResponse {

    @Schema(description = "ID tài khoản ngân hàng")
    private Long id;

    @Schema(description = "ID người dùng sở hữu")
    private Long userId;

    @Schema(description = "Mã ngân hàng", example = "VCB")
    private String bankCode;

    @Schema(description = "Tên ngân hàng", example = "Vietcombank")
    private String bankName;

    @Schema(description = "Số tài khoản đã che (chỉ hiển thị 4 số cuối)", example = "******6789")
    private String maskedAccountNumber;

    @Schema(description = "4 chữ số cuối của số tài khoản", example = "6789")
    private String accountLast4;

    @Schema(description = "Tên chủ tài khoản", example = "NGUYEN VAN A")
    private String holderName;

    @Schema(description = "Thời điểm đối chiếu tên chủ tài khoản với KYC")
    private Instant verifiedAt;

    @Schema(description = "Thời điểm kết thúc thời gian chờ 24 giờ bảo mật (BR-R-058)")
    private Instant cooldownUntil;

    @Schema(description = "Tài khoản đã hết thời gian chờ 24 giờ và sẵn sàng rút tiền hay chưa")
    private boolean readyForWithdrawal;

    @Schema(description = "Thời điểm tạo liên kết")
    private Instant createdAt;
}

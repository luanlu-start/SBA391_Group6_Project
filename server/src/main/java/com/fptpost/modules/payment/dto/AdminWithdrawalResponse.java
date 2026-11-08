package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.WithdrawalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu rút tiền trong hàng đợi duyệt của Admin (UC-41)")
public class AdminWithdrawalResponse {
    private Long id;
    private String requestCode;
    private WithdrawalStatus status;
    private long amount;
    private long feeAmount;
    private long netAmount;
    private String bankName;
    private String accountNumber;
    private String accountHolderName;
    private WithdrawalPartyResponse applicant;

    @Schema(description = "Số tiền từ ngưỡng DUAL_APPROVE cần hai Admin khác nhau duyệt")
    private boolean dualApprovalRequired;
    private WithdrawalPartyResponse firstApprover;
    private Instant firstApprovedAt;
    private WithdrawalPartyResponse approver;
    private Instant approvedAt;
    private WithdrawalPartyResponse rejectedBy;
    private Instant rejectedAt;
    private String rejectionReason;

    private String payoutProvider;
    private String payoutReference;
    private Instant payoutSubmittedAt;
    private WithdrawalPartyResponse settledBy;
    private Instant settledAt;
    private String failureReason;

    private Instant createdAt;
    private Instant updatedAt;
    @Schema(description = "Hạn xử lý theo WD_SLA ngày làm việc (giờ Việt Nam)")
    private Instant dueAt;
    private boolean overdue;

    private boolean canApprove;
    private boolean canReject;
    private boolean canSettle;

    @Schema(description = "Chỉ có ở API chi tiết")
    private WithdrawalReconciliationResponse reconciliation;
}

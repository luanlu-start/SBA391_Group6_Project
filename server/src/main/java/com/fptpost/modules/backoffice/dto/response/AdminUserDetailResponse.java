package com.fptpost.modules.backoffice.dto.response;

import com.fptpost.modules.identity.enums.KycStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserDetailResponse {
    AdminUserSummaryResponse account;
    LocalDate dob;
    KycStatus kycStatus;
    String courierTier;
    String courierVehicle;
    BigDecimal courierTrustScore;
    Integer courierCompletedOrders;
    long walletAvailable;
    long walletEscrowHeld;
    long walletPendingWithdraw;
    long sentOrderCount;
    long courierOrderCount;
    List<AdminUserOrderResponse> recentOrders;
    List<AdminUserTransactionResponse> recentTransactions;
    int activeSessions;
    List<AccountRequestResponse> dataRequests;
    boolean self;
    /** Lần khóa đang hiệu lực (UC-56). */
    AccountLockResponse lock;
    /** Đơn đã có người giao hoặc ký quỹ mà Admin cần chuyển người giao hoặc hủy, hoàn tiền (BR-R-081). */
    List<AdminUserOrderResponse> ordersNeedingAction;
}

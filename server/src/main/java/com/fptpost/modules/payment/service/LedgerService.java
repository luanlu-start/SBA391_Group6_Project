package com.fptpost.modules.payment.service;

import com.fptpost.modules.payment.model.LedgerTransaction;

/**
 * Service quản lý bút toán kép sổ cái kế toán (DB-026, DB-027).
 */
public interface LedgerService {

    /**
     * Ghi nhận bút toán nạp tiền vào ví qua VNPay (UC-34).
     * Nợ: VNPAY_CLEARING / Có: USER_WALLET_{userId}
     */
    LedgerTransaction recordWalletTopup(Long userId, Long amount, String txnRef);

    /**
     * Ghi nhận bút toán trích tiền từ ví sang ký quỹ đơn hàng (UC-35 - Ví).
     * Nợ: USER_WALLET_{senderId} / Có: ESCROW_ORDER_{orderId}
     */
    LedgerTransaction recordOrderEscrowFromWallet(Long orderId, Long senderId, Long amount, String escrowRef);

    /**
     * Ghi nhận bút toán ký quỹ đơn hàng trực tiếp qua VNPay (UC-35 - VNPay).
     * Nợ: VNPAY_CLEARING / Có: ESCROW_ORDER_{orderId}
     */
    LedgerTransaction recordOrderEscrowFromVNPay(Long orderId, Long senderId, Long amount, String txnRef);
}

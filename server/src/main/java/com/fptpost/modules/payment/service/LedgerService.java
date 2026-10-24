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
}

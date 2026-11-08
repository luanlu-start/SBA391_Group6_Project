package com.fptpost.modules.payment.model;

/**
 * Loại nghiệp vụ ghi sổ cái (Ledger Transaction Type).
 */
public enum LedgerTransactionType {
    /**
     * Nạp tiền vào ví người dùng (UC-34).
     */
    WALLET_TOPUP,

    /**
     * Giam giữ tiền ký quỹ đơn hàng (UC-35).
     */
    ORDER_ESCROW_HOLD,

    /**
     * Giải ngân tiền ký quỹ cho shipper (UC-36).
     */
    ESCROW_RELEASE,

    /**
     * Hoàn tiền ký quỹ cho người gửi (UC-37).
     */
    ESCROW_REFUND,

    /**
     * Yêu cầu rút tiền từ ví về ngân hàng (UC-40).
     */
    WITHDRAWAL_REQUEST,

    /**
     * Hủy yêu cầu rút tiền, hoàn trả tiền về ví (UC-40).
     */
    WITHDRAWAL_CANCEL,

    /**
     * Yêu cầu rút bị từ chối hoặc chi thất bại, hoàn tiền về ví (UC-41).
     */
    WITHDRAWAL_REFUND,

    /**
     * Chi trả yêu cầu rút tiền về tài khoản ngân hàng (UC-41).
     */
    WITHDRAWAL_PAYOUT
}

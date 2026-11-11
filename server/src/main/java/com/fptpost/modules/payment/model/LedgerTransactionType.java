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
     * Thu hồi từ ví người giao về ví người gửi theo phán quyết khiếu nại khi ký quỹ đã giải ngân (UC-51).
     */
    COMPLAINT_CLAWBACK,

    /**
     * Trích doanh thu nền tảng vào Quỹ bảo đảm (UC-52, BR-R-064).
     */
    GUARANTEE_FUND_CONTRIBUTION,

    /**
     * Chi bồi thường từ Quỹ bảo đảm vào ví người nhận theo phán quyết (UC-52).
     */
    GUARANTEE_FUND_PAYOUT,

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
    WITHDRAWAL_PAYOUT,

    /**
     * Bút toán điều chỉnh dòng lệch khi đối soát giao dịch hằng ngày (UC-43, BR-F-083).
     */
    RECONCILIATION_ADJUSTMENT,

    /**
     * Thưởng giới thiệu người dùng mới & thưởng huy hiệu thành tích (UC-62).
     */
    REFERRAL_REWARD,
    ACHIEVEMENT_REWARD
}

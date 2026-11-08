package com.fptpost.modules.payment.service.payout;

import com.fptpost.modules.payment.model.WithdrawalRequest;

/**
 * Chi trả yêu cầu rút tiền đã duyệt (BR-F-080, BR-C-008).
 * VNPay không có API chi hộ, nên nhà cung cấp được chọn bằng cấu hình {@code app.payout.provider}.
 */
public interface PayoutProvider {

    String code();

    PayoutResult submit(WithdrawalRequest withdrawal);
}

package com.fptpost.modules.payment.service;

import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.WalletResponse;
import com.fptpost.modules.payment.dto.WalletTopupRequest;
import com.fptpost.modules.payment.model.Wallet;
import jakarta.servlet.http.HttpServletRequest;

public interface WalletService {

    /**
     * Lấy hoặc tự động tạo mới ví với số dư = 0 cho user nếu chưa có.
     */
    Wallet getOrCreateWallet(Long userId);

    /**
     * Xem thông tin ví người dùng (UC-38).
     */
    WalletResponse getWallet(Long userId);

    /**
     * Khởi tạo giao dịch nạp tiền vào ví qua VNPay (UC-34).
     */
    PaymentCreationResponse createTopupUrl(Long userId, WalletTopupRequest request, HttpServletRequest httpRequest);
}

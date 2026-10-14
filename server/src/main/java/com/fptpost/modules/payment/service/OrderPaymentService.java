package com.fptpost.modules.payment.service;

import com.fptpost.modules.payment.dto.OrderPaymentRequest;
import com.fptpost.modules.payment.dto.OrderPaymentResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface OrderPaymentService {

    /**
     * Thanh toán và ký quỹ đơn hàng (UC-35).
     * Hỗ trợ 2 phương thức: WALLET (Ví nội bộ) hoặc VNPAY (Cổng VNPay).
     */
    OrderPaymentResponse payOrder(Long userId, Long orderId, OrderPaymentRequest request, HttpServletRequest httpRequest);
}

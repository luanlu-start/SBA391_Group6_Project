package com.fptpost.modules.payment.service;

import com.fptpost.modules.payment.dto.PaymentCreationRequest;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.VNPayIpnResponse;
import com.fptpost.modules.payment.dto.VNPayReturnResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Service handling VNPay payment generation, return callbacks, and IPN notifications.
 */
public interface VNPayService {

    /**
     * Generates a signed redirect payment URL for VNPay portal.
     *
     * @param request     payment details
     * @param httpRequest current HTTP servlet request
     * @return PaymentCreationResponse containing the checkout URL
     */
    PaymentCreationResponse createPaymentUrl(PaymentCreationRequest request, HttpServletRequest httpRequest);

    /**
     * Handles browser return redirect callback (vnp_ReturnUrl).
     *
     * @param queryParams all query parameters received in the return request
     * @return structured VNPayReturnResponse
     */
    VNPayReturnResponse processReturn(Map<String, String> queryParams);

    /**
     * Handles server-to-server asynchronous IPN webhook notification (vnp_IpnUrl).
     *
     * @param queryParams all query parameters sent by VNPay server
     * @return exact VNPay response protocol code and message
     */
    VNPayIpnResponse processIpn(Map<String, String> queryParams);
}

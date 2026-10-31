package com.fptpost.modules.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * VNPay Payment Gateway Configuration properties loaded from application.yml.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "vnpay")
public class VNPayConfig {

    /**
     * VNPay Merchant Terminal Code (vnp_TmnCode)
     */
    private String vnp_TmnCode = "";

    /**
     * VNPay Secret Hash Key (vnp_HashSecret / secretKey)
     */
    private String vnp_HashSecret = "";

    /**
     * VNPay Payment Gateway URL (vnp_PayUrl)
     */
    private String vnp_PayUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    /**
     * VNPay Return Callback URL (vnp_ReturnUrl)
     */
    private String vnp_ReturnUrl = "http://localhost:8080/api/payment/vnpay-return";

    /**
     * VNPay Merchant WebAPI Query/Refund URL (vnp_ApiUrl)
     */
    private String vnp_ApiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";

    /**
     * Protocol Version (default: 2.1.0)
     */
    private String version = "2.1.0";

    /**
     * Command (default: pay)
     */
    private String command = "pay";

    /**
     * Order Type (default: other)
     */
    private String orderType = "other";

    /**
     * Currency Code (default: VND)
     */
    private String currCode = "VND";

    /**
     * Default Locale (vn / en)
     */
    private String locale = "vn";

    /**
     * Transaction expiration time in minutes (default: 15)
     */
    private int timeoutMinutes = 15;

    // Convenient Aliases / Standard Getters
    public String getTmnCode() {
        return vnp_TmnCode;
    }

    public void setTmnCode(String tmnCode) {
        this.vnp_TmnCode = tmnCode;
    }

    public String getHashSecret() {
        return vnp_HashSecret;
    }

    public void setHashSecret(String hashSecret) {
        this.vnp_HashSecret = hashSecret;
    }

    public String getPayUrl() {
        return vnp_PayUrl;
    }

    public void setPayUrl(String payUrl) {
        this.vnp_PayUrl = payUrl;
    }

    public String getReturnUrl() {
        return vnp_ReturnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.vnp_ReturnUrl = returnUrl;
    }

    public String getApiUrl() {
        return vnp_ApiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.vnp_ApiUrl = apiUrl;
    }
}

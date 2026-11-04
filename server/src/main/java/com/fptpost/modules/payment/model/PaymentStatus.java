package com.fptpost.modules.payment.model;

/**
 * Lifecycle status of a payment transaction.
 */
public enum PaymentStatus {
    /**
     * Payment initialized and waiting for customer action on VNPay portal.
     */
    PENDING,

    /**
     * Payment successfully verified and confirmed via VNPay IPN.
     */
    SUCCESS,

    /**
     * Payment failed or was rejected by bank/gateway.
     */
    FAILED,

    /**
     * Payment was cancelled by the customer.
     */
    CANCELLED
}

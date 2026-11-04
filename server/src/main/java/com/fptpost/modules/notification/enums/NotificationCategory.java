package com.fptpost.modules.notification.enums;

public enum NotificationCategory {
    KYC(true), SECURITY(true), PAYMENT(true), DISPUTE(true), ORDER(true), PROMOTION(false), RECOMMENDATION(false);

    private final boolean mandatory;

    NotificationCategory(boolean mandatory) { this.mandatory = mandatory; }
    public boolean isMandatory() { return mandatory; }
}

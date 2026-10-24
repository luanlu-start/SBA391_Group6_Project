package com.fptpost.common.mail.exception;

public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException() {
        // SMTP/template exceptions may contain recipient details or secrets; do not retain them.
        super("Unable to prepare or send email");
    }
}

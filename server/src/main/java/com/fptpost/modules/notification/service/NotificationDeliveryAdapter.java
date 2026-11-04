package com.fptpost.modules.notification.service;

import com.fptpost.common.mail.dto.EmailRequest;
import com.fptpost.common.mail.enums.EmailTemplateType;
import com.fptpost.common.mail.service.EmailService;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service @RequiredArgsConstructor
public class NotificationDeliveryAdapter {
    private final NotificationInboxStore inbox;
    private final SimpMessagingTemplate messaging;
    private final EmailService email;
    private final NotificationRecipientLookup recipients;
    private final NotificationSettingsService settings;
    private final MockSmsDelivery sms;

    /** SMTP is at-least-once: a process failure after SMTP acceptance but before SQL acknowledgement can resend. */
    public boolean deliver(NotificationDeliveryClaim claim) {
        var event = claim.event();
        var recipient = recipients.find(event.getRecipient()).filter(NotificationRecipientLookup.Recipient::canReceive).orElse(null);
        if (recipient == null) return false;
        if (!settings.channels(event.getRecipient(), event.getCategory()).contains(claim.channel())) return false;
        switch (claim.channel()) {
            case IN_APP -> {
                var result = inbox.append(event);
                messaging.convertAndSendToUser(event.getRecipient().toString(), "/queue/notifications", result);
            }
            case EMAIL -> {
                if (!recipient.emailVerified() || recipient.email() == null || recipient.email().isBlank()) return false;
                email.sendEmail(EmailRequest.builder().email(recipient.email()).templateType(EmailTemplateType.NOTIFICATION)
                        .variables(Map.of("title", event.getTitle(), "body", event.getBody())).build());
            }
            case SMS -> {
                if (!settings.smsAvailable(recipient)) throw new IllegalStateException("SMS provider or verified contact unavailable");
                sms.send(claim, recipient.phone());
            }
        }
        return true;
    }
}

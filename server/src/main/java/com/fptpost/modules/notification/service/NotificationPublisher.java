package com.fptpost.modules.notification.service;

import com.fptpost.common.exception.*;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.model.*;
import com.fptpost.modules.notification.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class NotificationPublisher {
    private final NotificationEventRepository events;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationRecipientLookup recipients;
    private final NotificationSettingsService settings;
    private final Clock clock;

    /** Joins the business transaction. External delivery is performed only by the outbox worker after commit. */
    @Transactional
    public void publish(UUID recipient, NotificationCategory category, String eventKey, String title, String body, String relativeLink) {
        if (recipient == null || category == null || eventKey == null || !eventKey.matches("[A-Za-z0-9:_.-]{1,160}")
                || title == null || title.isBlank() || title.length() > 200 || title.contains("\r") || title.contains("\n")
                || body == null || body.isBlank() || body.length() > 2000 || !validLink(relativeLink))
            throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        var contact = recipients.lock(recipient);
        if (!contact.canReceive()) throw new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND);
        if (events.findByRecipientAndEventKey(recipient, eventKey).isPresent()) return;
        var now = clock.instant();
        var event = events.saveAndFlush(NotificationEvent.builder().id(UUID.randomUUID()).recipient(recipient).eventKey(eventKey)
                .category(category).title(title.strip()).body(body.strip()).relativeLink(relativeLink).at(now).build());
        for (var channel : settings.channels(recipient, category)) {
            deliveries.save(NotificationDelivery.builder().id(UUID.randomUUID()).event(event).channel(channel)
                    .status(NotificationDeliveryStatus.PENDING).nextAttemptAt(now).build());
        }
        deliveries.flush();
    }

    private boolean validLink(String link) {
        return link == null || link.length() <= 500 && link.matches("/[A-Za-z0-9/_?=&.%-]*")
                && !link.startsWith("//") && !link.contains("\\") && !link.contains("..")
                && !link.toLowerCase(java.util.Locale.ROOT).contains("%2f") && !link.toLowerCase(java.util.Locale.ROOT).contains("%5c");
    }
}

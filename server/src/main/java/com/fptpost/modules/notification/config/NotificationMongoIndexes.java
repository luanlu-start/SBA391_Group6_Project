package com.fptpost.modules.notification.config;

import com.fptpost.modules.notification.model.InboxNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class NotificationMongoIndexes {
    private final MongoTemplate mongo;
    private final NotificationOutboxProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (!properties.isEnabled()) return;
        try {
            mongo.indexOps(InboxNotification.class).ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                    .on("at", Sort.Direction.DESC).on("_id", Sort.Direction.DESC).named("ix_notification_user_time"));
            mongo.indexOps(InboxNotification.class).ensureIndex(new Index().on("userId", Sort.Direction.ASC)
                    .on("readAt", Sort.Direction.ASC).on("at", Sort.Direction.DESC).on("_id", Sort.Direction.DESC).named("ix_notification_unread"));
        } catch (RuntimeException failure) { log.warn("Notification indexes unavailable ({})", failure.getClass().getSimpleName()); }
    }
}

package com.fptpost.modules.notification.repository;

import com.fptpost.modules.notification.model.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {
    Optional<NotificationEvent> findByRecipientAndEventKey(UUID recipient, String eventKey);
}

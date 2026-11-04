package com.fptpost.modules.notification.repository;

import com.fptpost.modules.notification.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByRecipient(UUID recipient);
}

package com.fptpost.modules.notification.repository;

import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.model.NotificationDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d join fetch d.event where d.channel in :channels and "
            + "((d.status = :pending and d.nextAttemptAt <= :now) or (d.status = :processing and d.claimedAt < :cutoff)) "
            + "order by d.nextAttemptAt asc, d.id asc")
    List<NotificationDelivery> lockEligible(@Param("channels") Collection<NotificationChannel> channels,
            @Param("pending") NotificationDeliveryStatus pending, @Param("processing") NotificationDeliveryStatus processing,
            @Param("now") Instant now, @Param("cutoff") Instant cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d where d.id = :id")
    Optional<NotificationDelivery> lockById(@Param("id") UUID id);
}

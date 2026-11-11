package com.fptpost.modules.deliveryreturn.repository;

import com.fptpost.modules.deliveryreturn.model.DeliveryContactAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface DeliveryContactAttemptRepository extends JpaRepository<DeliveryContactAttempt, Long> {
    Optional<DeliveryContactAttempt> findByOrderIdAndRequestId(Long orderId, UUID requestId);
    List<DeliveryContactAttempt> findByOrderIdAndCourierIdAndReturnCaseIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            Long orderId, Long courierId, Instant start);
}

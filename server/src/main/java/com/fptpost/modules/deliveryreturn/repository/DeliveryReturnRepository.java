package com.fptpost.modules.deliveryreturn.repository;

import com.fptpost.modules.deliveryreturn.model.DeliveryReturnCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeliveryReturnRepository extends JpaRepository<DeliveryReturnCase, Long> {
    Optional<DeliveryReturnCase> findTopByOrderIdOrderByIdDesc(Long orderId);
    Optional<DeliveryReturnCase> findByIdAndOrderId(Long id, Long orderId);
}

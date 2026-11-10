package com.fptpost.modules.incident.repository;

import com.fptpost.modules.incident.model.DeliveryIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface DeliveryIncidentRepository extends JpaRepository<DeliveryIncident, Long> {
    Page<DeliveryIncident> findByOrderIdOrderByCreatedAtDescIdDesc(Long orderId, Pageable pageable);
    Optional<DeliveryIncident> findByIdAndOrderId(Long id, Long orderId);
}

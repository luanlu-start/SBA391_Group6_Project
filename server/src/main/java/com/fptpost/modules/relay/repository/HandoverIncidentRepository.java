package com.fptpost.modules.relay.repository;

import com.fptpost.modules.relay.model.HandoverIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HandoverIncidentRepository extends JpaRepository<HandoverIncident, Long> {

    List<HandoverIncident> findByOrderIdOrderByReportedAtDesc(Long orderId);
}

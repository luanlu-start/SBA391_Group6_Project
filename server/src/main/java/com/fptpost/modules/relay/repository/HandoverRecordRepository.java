package com.fptpost.modules.relay.repository;

import com.fptpost.modules.relay.model.HandoverRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HandoverRecordRepository extends JpaRepository<HandoverRecord, Long> {

    List<HandoverRecord> findByOrderIdOrderByLegNoAsc(Long orderId);
}

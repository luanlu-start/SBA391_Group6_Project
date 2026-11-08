package com.fptpost.modules.batch.repository;

import com.fptpost.modules.batch.model.DeliveryBatchStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryBatchStopRepository extends JpaRepository<DeliveryBatchStop, Long> {

    @Query("select s from DeliveryBatchStop s where s.batch.id = :batchId order by s.stopOrder asc")
    List<DeliveryBatchStop> findByBatchIdOrderByStopOrderAsc(@Param("batchId") Long batchId);
}

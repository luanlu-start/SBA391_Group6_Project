package com.fptpost.modules.batch.repository;

import com.fptpost.modules.batch.model.DeliveryBatchStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface DeliveryBatchStopRepository extends JpaRepository<DeliveryBatchStop, Long> {

    @Query("select s from DeliveryBatchStop s where s.batch.id = :batchId order by s.stopOrder asc")
    List<DeliveryBatchStop> findByBatchIdOrderByStopOrderAsc(@Param("batchId") Long batchId);

    @Query("select distinct s.order.id from DeliveryBatchStop s where s.order is not null and s.batch.status <> com.fptpost.modules.batch.model.DeliveryBatchStatus.CANCELLED")
    Set<Long> findBatchedOrderIds();
}

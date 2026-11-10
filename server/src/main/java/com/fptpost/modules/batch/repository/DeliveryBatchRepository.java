package com.fptpost.modules.batch.repository;

import com.fptpost.modules.batch.model.DeliveryBatch;
import com.fptpost.modules.batch.model.DeliveryBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryBatchRepository extends JpaRepository<DeliveryBatch, Long> {

    Optional<DeliveryBatch> findByBatchCode(String batchCode);

    boolean existsByBatchCode(String batchCode);

    Page<DeliveryBatch> findByCourierId(Long courierId, Pageable pageable);

    List<DeliveryBatch> findByCourierIdOrderByCreatedAtDesc(Long courierId);

    @Query("select b from DeliveryBatch b where b.courier.id = :courierId and b.status in :statuses order by b.createdAt desc")
    List<DeliveryBatch> findByCourierIdAndStatusIn(@Param("courierId") Long courierId, @Param("statuses") List<DeliveryBatchStatus> statuses);

    @Query("select b from DeliveryBatch b left join fetch b.stops s left join fetch b.orders o where b.id = :id")
    Optional<DeliveryBatch> findByIdWithDetails(@Param("id") Long id);
}

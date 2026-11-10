package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.RefundRequest;
import com.fptpost.modules.payment.model.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    List<RefundRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<RefundRequest> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(
            Long orderId,
            Collection<RefundStatus> statuses
    );

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<RefundStatus> statuses);
}

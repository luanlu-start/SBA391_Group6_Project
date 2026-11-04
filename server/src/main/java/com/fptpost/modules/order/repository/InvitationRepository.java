package com.fptpost.modules.order.repository;

import com.fptpost.modules.order.model.Invitation;
import com.fptpost.modules.order.model.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    List<Invitation> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<Invitation> findByOrderIdAndStatusInOrderByCreatedAtDesc(Long orderId, Collection<InvitationStatus> statuses);

    Page<Invitation> findByCourierIdOrderByCreatedAtDesc(Long courierId, Pageable pageable);

    Page<Invitation> findByCourierIdAndStatusOrderByCreatedAtDesc(Long courierId, InvitationStatus status, Pageable pageable);

    Page<Invitation> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);

    boolean existsByOrderIdAndCourierIdAndStatus(Long orderId, Long courierId, InvitationStatus status);

    @Query("SELECT i FROM Invitation i WHERE i.order.id = :orderId AND i.courier.id = :courierId AND i.status = :status")
    Optional<Invitation> findByOrderIdAndCourierIdAndStatus(
            @Param("orderId") Long orderId,
            @Param("courierId") Long courierId,
            @Param("status") InvitationStatus status
    );

    @Query("SELECT i FROM Invitation i WHERE i.status = 'PENDING' AND i.expiresAt < :now")
    List<Invitation> findExpiredPendingInvitations(@Param("now") Instant now);
}

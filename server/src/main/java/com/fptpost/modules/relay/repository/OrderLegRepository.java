package com.fptpost.modules.relay.repository;

import com.fptpost.modules.relay.model.LegStatus;
import com.fptpost.modules.relay.model.OrderLeg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderLegRepository extends JpaRepository<OrderLeg, Long> {

    List<OrderLeg> findByOrderIdOrderByLegNoAsc(Long orderId);

    Optional<OrderLeg> findByOrderIdAndLegNo(Long orderId, Integer legNo);

    List<OrderLeg> findByCourierIdOrderByCreatedAtDesc(Long courierId);

    List<OrderLeg> findByCourierIdAndStatusIn(Long courierId, List<LegStatus> statuses);

    @Query("SELECT l FROM OrderLeg l JOIN FETCH l.order WHERE l.courierId = :courierId ORDER BY l.createdAt DESC")
    List<OrderLeg> findByCourierIdWithOrder(@Param("courierId") Long courierId);
}

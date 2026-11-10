package com.fptpost.modules.matching.repository;

import com.fptpost.modules.matching.model.TripOrderMatch;
import com.fptpost.modules.matching.model.TripOrderMatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripOrderMatchRepository extends JpaRepository<TripOrderMatch, Long> {

    Optional<TripOrderMatch> findByTripIdAndOrderId(Long tripId, Long orderId);

    List<TripOrderMatch> findByTripIdOrderByScoreDesc(Long tripId);

    List<TripOrderMatch> findByOrderIdOrderByScoreDesc(Long orderId);

    @Modifying
    @Query("update TripOrderMatch m set m.status = :status where m.trip.id = :tripId and m.order.id = :orderId")
    int updateStatusByTripAndOrder(@Param("tripId") Long tripId, @Param("orderId") Long orderId, @Param("status") TripOrderMatchStatus status);

    @Modifying
    @Query("update TripOrderMatch m set m.status = :status where m.order.id = :orderId and m.trip.id <> :excludeTripId")
    int updateOtherMatchesForOrder(@Param("orderId") Long orderId, @Param("excludeTripId") Long excludeTripId, @Param("status") TripOrderMatchStatus status);
}

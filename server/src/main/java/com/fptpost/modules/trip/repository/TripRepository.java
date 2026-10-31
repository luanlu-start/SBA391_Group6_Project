package com.fptpost.modules.trip.repository;

import com.fptpost.modules.trip.model.Trip;
import com.fptpost.modules.trip.model.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    Page<Trip> findByCourierId(Long courierId, Pageable pageable);

    Page<Trip> findByCourierIdAndStatus(Long courierId, TripStatus status, Pageable pageable);

    Optional<Trip> findByIdAndCourierId(Long id, Long courierId);

    List<Trip> findByStatusAndDepartAtAfter(TripStatus status, Instant departAfter);
}

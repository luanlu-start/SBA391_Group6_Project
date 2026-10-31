package com.fptpost.modules.trip.service;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.trip.dto.request.TripCreateRequest;
import com.fptpost.modules.trip.dto.response.TripResponse;
import com.fptpost.modules.trip.model.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ITripService {

    TripResponse createTrip(TripCreateRequest request, User courierUser);

    Page<TripResponse> getMyTrips(User courierUser, TripStatus status, Pageable pageable);

    TripResponse getTripDetail(Long id, User courierUser);

    TripResponse cancelTrip(Long id, User courierUser);
}

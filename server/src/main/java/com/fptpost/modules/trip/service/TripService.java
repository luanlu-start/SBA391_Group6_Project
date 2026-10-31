package com.fptpost.modules.trip.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.trip.dto.request.TripCreateRequest;
import com.fptpost.modules.trip.dto.response.TripResponse;
import com.fptpost.modules.trip.model.Trip;
import com.fptpost.modules.trip.model.TripRecurrence;
import com.fptpost.modules.trip.model.TripStatus;
import com.fptpost.modules.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TripService implements ITripService {

    private final TripRepository tripRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public TripResponse createTrip(TripCreateRequest request, User courierUser) {
        // 1. Kiểm tra tiền điều kiện: Người giao đã được duyệt (UC-22 Pre-condition)
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);
        boolean hasCourierRole = courierUser.getRoles() != null &&
                courierUser.getRoles().stream().anyMatch(role -> "COURIER".equalsIgnoreCase(role.getCode()));

        if (courierProfile == null && !hasCourierRole) {
            log.warn("User {} attempted to create trip without courier profile/role", courierUser.getId());
            throw new AppException(GlobalErrorCode.FORBIDDEN, "Bạn cần hoàn tất đăng ký Người giao để đăng chuyến đi.");
        }

        if (courierProfile != null && courierProfile.getRestrictedUntil() != null &&
                courierProfile.getRestrictedUntil().isAfter(Instant.now())) {
            throw new AppException(GlobalErrorCode.FORBIDDEN, "Tài khoản người giao của bạn đang bị tạm khóa nhận đơn.");
        }

        // 2. Kiểm tra thông tin bắt buộc & thời gian khởi hành (BR-R-035)
        Instant now = Instant.now();
        // Cho phép dung sai 1 phút do độ trễ truyền dữ liệu mạng
        if (request.getDepartAt() == null || request.getDepartAt().isBefore(now.minus(1, ChronoUnit.MINUTES))) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Thời gian khởi hành phải ở tương lai.");
        }

        if (request.getCapacityKg() == null || request.getCapacityKg() <= 0) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Sức chứa vận chuyển phải lớn hơn 0 kg.");
        }

        // 3. Khởi tạo thực thể Chuyến đi (BR-F-036)
        Trip trip = Trip.builder()
                .courier(courierUser)
                .origin(request.getOrigin().trim())
                .originLat(request.getOriginLat())
                .originLng(request.getOriginLng())
                .destination(request.getDestination().trim())
                .destinationLat(request.getDestinationLat())
                .destinationLng(request.getDestinationLng())
                .routePolyline(request.getRoutePolyline())
                .distanceKm(request.getDistanceKm())
                .estimatedDurationMinutes(request.getEstimatedDurationMinutes())
                .departAt(request.getDepartAt())
                .vehicleType(request.getVehicleType())
                .capacityKg(request.getCapacityKg())
                .floorPrice(request.getFloorPrice())
                .recurrence(request.getRecurrence() != null ? request.getRecurrence() : TripRecurrence.NONE)
                .status(TripStatus.SCHEDULED)
                .build();

        Trip savedTrip = tripRepository.save(trip);
        log.info("Trip #{} created successfully by courier user #{}", savedTrip.getId(), courierUser.getId());

        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        return TripResponse.fromEntity(savedTrip, userProfile, courierProfile);
    }

    @Override
    public Page<TripResponse> getMyTrips(User courierUser, TripStatus status, Pageable pageable) {
        Page<Trip> tripPage;
        if (status != null) {
            tripPage = tripRepository.findByCourierIdAndStatus(courierUser.getId(), status, pageable);
        } else {
            tripPage = tripRepository.findByCourierId(courierUser.getId(), pageable);
        }

        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);

        return tripPage.map(trip -> TripResponse.fromEntity(trip, userProfile, courierProfile));
    }

    @Override
    public TripResponse getTripDetail(Long id, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);

        return TripResponse.fromEntity(trip, userProfile, courierProfile);
    }

    @Override
    @Transactional
    public TripResponse cancelTrip(Long id, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Chuyến đi đã hoàn tất hoặc đã được hủy trước đó.");
        }

        trip.setStatus(TripStatus.CANCELLED);
        Trip updatedTrip = tripRepository.save(trip);
        log.info("Trip #{} cancelled by courier user #{}", updatedTrip.getId(), courierUser.getId());

        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);

        return TripResponse.fromEntity(updatedTrip, userProfile, courierProfile);
    }
}

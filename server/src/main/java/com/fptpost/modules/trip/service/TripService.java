package com.fptpost.modules.trip.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.trip.dto.request.TripCreateRequest;
import com.fptpost.modules.trip.dto.request.TripUpdateRequest;
import com.fptpost.modules.trip.dto.response.TripAttachedOrderResponse;
import com.fptpost.modules.trip.dto.response.TripResponse;
import com.fptpost.modules.trip.model.Trip;
import com.fptpost.modules.trip.model.TripRecurrence;
import com.fptpost.modules.trip.model.TripStatus;
import com.fptpost.modules.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TripService implements ITripService {

    private final TripRepository tripRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final UserProfileRepository userProfileRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public TripResponse createTrip(TripCreateRequest request, User courierUser) {
        // 1. Kiểm tra tiền điều kiện: Người giao đã được duyệt (Pre-condition)
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
        return TripResponse.fromEntity(savedTrip, userProfile, courierProfile, List.of());
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

        return tripPage.map(trip -> {
            List<Order> attached = orderRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());
            List<TripAttachedOrderResponse> attachedResponses = attached.stream()
                    .map(TripAttachedOrderResponse::fromOrder)
                    .toList();
            return TripResponse.fromEntity(trip, userProfile, courierProfile, attachedResponses);
        });
    }

    @Override
    public TripResponse getTripDetail(Long id, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);

        List<Order> attached = orderRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());
        List<TripAttachedOrderResponse> attachedResponses = attached.stream()
                .map(TripAttachedOrderResponse::fromOrder)
                .toList();

        return TripResponse.fromEntity(trip, userProfile, courierProfile, attachedResponses);
    }

    @Override
    @Transactional
    public TripResponse updateTrip(Long id, TripUpdateRequest request, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        // Kiểm tra trạng thái có thể sửa
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Không thể chỉnh sửa chuyến đi đã kết thúc hoặc đã hủy.");
        }

        if (request.getCapacityKg() == null || request.getCapacityKg() <= 0) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Sức chứa vận chuyển phải lớn hơn 0 kg.");
        }

        // Cập nhật thông tin chuyến đi (BR-F-039)
        trip.setOrigin(request.getOrigin().trim());
        trip.setOriginLat(request.getOriginLat());
        trip.setOriginLng(request.getOriginLng());
        trip.setDestination(request.getDestination().trim());
        trip.setDestinationLat(request.getDestinationLat());
        trip.setDestinationLng(request.getDestinationLng());
        trip.setDepartAt(request.getDepartAt());
        trip.setVehicleType(request.getVehicleType());
        trip.setCapacityKg(request.getCapacityKg());
        trip.setFloorPrice(request.getFloorPrice());
        if (request.getRecurrence() != null) {
            trip.setRecurrence(request.getRecurrence());
        }
        if (request.getRoutePolyline() != null && !request.getRoutePolyline().isBlank()) {
            trip.setRoutePolyline(request.getRoutePolyline());
        }
        if (request.getDistanceKm() != null) {
            trip.setDistanceKm(request.getDistanceKm());
        }
        if (request.getEstimatedDurationMinutes() != null) {
            trip.setEstimatedDurationMinutes(request.getEstimatedDurationMinutes());
        }

        Trip updatedTrip = tripRepository.save(trip);
        log.info("Trip #{} updated by courier user #{}", updatedTrip.getId(), courierUser.getId());

        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);
        List<Order> attached = orderRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());

        return TripResponse.fromEntity(updatedTrip, userProfile, courierProfile,
                attached.stream().map(TripAttachedOrderResponse::fromOrder).toList());
    }

    @Override
    @Transactional
    public TripResponse togglePauseTrip(Long id, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Chuyến đi đã hoàn tất hoặc đã bị hủy, không thể thay đổi trạng thái nhận đơn.");
        }

        // BR-F-040: Bật tắt nhận đơn cho chuyến (tạm dừng / mở lại)
        if (trip.getStatus() == TripStatus.PAUSED) {
            if (trip.getDepartAt().isAfter(Instant.now())) {
                trip.setStatus(TripStatus.SCHEDULED);
            } else {
                trip.setStatus(TripStatus.ACTIVE);
            }
            log.info("Trip #{} resumed by courier #{}", trip.getId(), courierUser.getId());
        } else {
            trip.setStatus(TripStatus.PAUSED);
            log.info("Trip #{} paused from accepting orders by courier #{}", trip.getId(), courierUser.getId());
        }

        Trip updatedTrip = tripRepository.save(trip);
        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);
        List<Order> attached = orderRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());

        return TripResponse.fromEntity(updatedTrip, userProfile, courierProfile,
                attached.stream().map(TripAttachedOrderResponse::fromOrder).toList());
    }

    @Override
    @Transactional
    public TripResponse cancelTrip(Long id, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Chuyến đi đã hoàn tất hoặc đã được hủy trước đó.");
        }

        // BR-R-037: Hủy chuyến có đơn: Chuyến đã có đơn được chọn không thể hủy trực tiếp
        long activeOrdersCount = orderRepository.countActiveOrdersByTripId(id);
        if (activeOrdersCount > 0) {
            log.warn("Courier user #{} attempted to cancel trip #{} which still has {} active orders",
                    courierUser.getId(), id, activeOrdersCount);
            throw new AppException(GlobalErrorCode.INVALID_REQUEST,
                    "Chuyến đi đang có đơn hàng ghép nối chưa hoàn tất. Bạn không thể hủy chuyến trực tiếp mà cần hủy từng đơn theo quy tắc hoặc chuyển đơn về sàn.");
        }

        trip.setStatus(TripStatus.CANCELLED);
        Trip updatedTrip = tripRepository.save(trip);
        log.info("Trip #{} cancelled by courier user #{}", updatedTrip.getId(), courierUser.getId());

        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);
        List<Order> attached = orderRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());

        return TripResponse.fromEntity(updatedTrip, userProfile, courierProfile,
                attached.stream().map(TripAttachedOrderResponse::fromOrder).toList());
    }

    @Override
    @Transactional
    public TripResponse completeTrip(Long id, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Chuyến đi đã hoàn tất hoặc đã được hủy trước đó.");
        }

        // Kết thúc chuyến đi (BR-F-039)
        trip.setStatus(TripStatus.COMPLETED);
        Trip updatedTrip = tripRepository.save(trip);
        log.info("Trip #{} marked as completed by courier user #{}", updatedTrip.getId(), courierUser.getId());

        UserProfile userProfile = userProfileRepository.findById(courierUser.getId()).orElse(null);
        CourierProfile courierProfile = courierProfileRepository.findById(courierUser.getId()).orElse(null);
        List<Order> attached = orderRepository.findByTripIdOrderByCreatedAtDesc(trip.getId());

        return TripResponse.fromEntity(updatedTrip, userProfile, courierProfile,
                attached.stream().map(TripAttachedOrderResponse::fromOrder).toList());
    }

    @Override
    public List<TripAttachedOrderResponse> getAttachedOrders(Long id, User courierUser) {
        tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        List<Order> orders = orderRepository.findByTripIdOrderByCreatedAtDesc(id);
        return orders.stream()
                .map(TripAttachedOrderResponse::fromOrder)
                .toList();
    }

    @Override
    public List<TripAttachedOrderResponse> getSuggestedOrders(Long id, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(id, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        // BR-F-040: Chuyến tạm dừng hoặc kết thúc không hiển thị gợi ý mới
        if (trip.getStatus() == TripStatus.PAUSED || trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            return List.of();
        }

        // Lấy danh sách đơn mở trên sàn (FINDING_SHIPPER)
        Page<Order> availableOrdersPage = orderRepository.findAvailableShippingOrder(PageRequest.of(0, 50));
        List<Order> availableOrders = availableOrdersPage.getContent();

        // BR-R-036: Điều kiện ghép đơn-chuyến: Độ lệch <= 3.0 km (PRM-099), tối đa 10 gợi ý (PRM-100)
        double maxDetourKm = 3.0;
        int maxSuggestions = 10;

        List<TripAttachedOrderResponse> suggestions = new ArrayList<>();

        for (Order order : availableOrders) {
            // Không gợi ý đơn do chính courier tạo (BR-R-008)
            if (order.getSenderId() != null && order.getSenderId().equals(courierUser.getId())) {
                continue;
            }

            // Kiểm tra tải trọng hàng không vượt quá sức chứa chuyến
            if (order.getWeightKg() != null && trip.getCapacityKg() != null &&
                    order.getWeightKg().doubleValue() > trip.getCapacityKg()) {
                continue;
            }

            // Kiểm tra giá sàn (nếu chuyến có đặt floorPrice)
            if (trip.getFloorPrice() != null && order.getPriceSuggested() != null &&
                    order.getPriceSuggested().compareTo(trip.getFloorPrice()) < 0) {
                continue;
            }

            double detour = calculateDetourKm(trip, order);
            if (detour <= maxDetourKm || (order.getPickupLatitude() == null && detour <= 5.0)) {
                suggestions.add(TripAttachedOrderResponse.fromOrder(order, Math.round(detour * 10.0) / 10.0));
            }
        }

        suggestions.sort(Comparator.comparingDouble(o -> o.getDetourKm() != null ? o.getDetourKm() : 999.0));

        if (suggestions.size() > maxSuggestions) {
            return suggestions.subList(0, maxSuggestions);
        }
        return suggestions;
    }

    /**
     * Tính toán khoảng cách lệch tuyến ước tính (detour km) giữa hành trình chuyến đi và điểm lấy/giao của đơn hàng.
     */
    private double calculateDetourKm(Trip trip, Order order) {
        if (order.getPickupLatitude() == null || order.getPickupLongitude() == null ||
                order.getDeliveryLatitude() == null || order.getDeliveryLongitude() == null) {
            // Trường hợp không có tọa độ chính xác, sử dụng 1.5km ước tính tượng trưng
            return 1.5;
        }

        double pickupLat = order.getPickupLatitude().doubleValue();
        double pickupLng = order.getPickupLongitude().doubleValue();
        double dropoffLat = order.getDeliveryLatitude().doubleValue();
        double dropoffLng = order.getDeliveryLongitude().doubleValue();

        // Khoảng cách từ điểm đón tới origin của trip
        double dPickupToOrigin = haversine(pickupLat, pickupLng, trip.getOriginLat(), trip.getOriginLng());
        // Khoảng cách từ điểm giao tới destination của trip
        double dDropoffToDest = haversine(dropoffLat, dropoffLng, trip.getDestinationLat(), trip.getDestinationLng());

        // Độ lệch tương đối so với đường đi thẳng
        return Math.min(dPickupToOrigin, dDropoffToDest);
    }

    /**
     * Công thức Haversine tính khoảng cách giữa hai tọa độ địa lý (km).
     */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Bán kính Trái Đất (km)
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}

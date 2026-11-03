package com.fptpost.modules.matching.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.VehicleType;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.matching.dto.request.QuickBidRequest;
import com.fptpost.modules.matching.dto.response.OrderCourierMatchResponse;
import com.fptpost.modules.matching.dto.response.TripOrderMatchResponse;
import com.fptpost.modules.matching.model.TripOrderMatch;
import com.fptpost.modules.matching.model.TripOrderMatchStatus;
import com.fptpost.modules.matching.repository.TripOrderMatchRepository;
import com.fptpost.modules.order.dto.request.OfferCreateRequest;
import com.fptpost.modules.order.dto.response.MarketOrderAcceptResponse;
import com.fptpost.modules.order.dto.response.OfferResponse;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.IOfferService;
import com.fptpost.modules.order.service.OrderService;
import com.fptpost.modules.trip.model.Trip;
import com.fptpost.modules.trip.model.TripStatus;
import com.fptpost.modules.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService implements IMatchingService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final TripOrderMatchRepository tripOrderMatchRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final UserProfileRepository userProfileRepository;
    private final SystemParameterRepository systemParameterRepository;
    private final OrderService orderService;
    private final IOfferService offerService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm, dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    /**
     * API-053 / UC-21: Gợi ý các đơn hàng tiện đường phù hợp nhất cho chuyến đi của người giao.
     */
    @Override
    @Transactional
    public List<TripOrderMatchResponse> getOrdersForTrip(Long tripId, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(tripId, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin chuyến đi."));

        // BR-F-040: Chuyến tạm dừng hoặc kết thúc không hiển thị gợi ý mới
        if (trip.getStatus() == TripStatus.PAUSED || trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            return Collections.emptyList();
        }

        // Lấy tham số cấu hình: MATCH_DETOUR (PRM-099) và MATCH_TOPN (PRM-100)
        double maxDetourKm = getDoubleParameter("MATCH_DETOUR", 3.0);
        int maxSuggestions = getIntParameter("MATCH_TOPN", 10);
        BigDecimal highValueThreshold = getBigDecimalParameter("HIGH_VALUE", new BigDecimal("1000000"));

        // Hồ sơ bưu tá để kiểm tra điều kiện hạng & hạn mức
        CourierProfile courierProfile = courierProfileRepository.findByUserId(courierUser.getId()).orElse(null);
        CourierTier tier = (courierProfile != null && courierProfile.getTier() != null)
                ? courierProfile.getTier() : CourierTier.SILVER;

        // Lấy danh sách các đơn hàng đang mở tìm shipper trên sàn (status = FINDING_SHIPPER)
        Page<Order> availableOrdersPage = orderRepository.findAvailableShippingOrder(PageRequest.of(0, 50));
        List<Order> availableOrders = availableOrdersPage.getContent();

        List<double[]> routeCoordinates = parsePolylineCoordinates(trip.getRoutePolyline());
        List<TripOrderMatchResponse> candidateResponses = new ArrayList<>();

        for (Order order : availableOrders) {
            // BR-R-008: Không gợi ý đơn do chính người giao tạo
            if (order.getSenderId() != null && order.getSenderId().equals(courierUser.getId())) {
                continue;
            }

            // Kiểm tra tải trọng hàng không vượt quá sức chứa của chuyến
            if (order.getWeightKg() != null && trip.getCapacityKg() != null
                    && order.getWeightKg().doubleValue() > trip.getCapacityKg()) {
                continue;
            }

            // Kiểm tra giá sàn (nếu chuyến có đặt floorPrice)
            if (trip.getFloorPrice() != null && order.getPriceSuggested() != null
                    && order.getPriceSuggested().compareTo(trip.getFloorPrice()) < 0) {
                continue;
            }

            // BR-R-024: Đơn hàng giá trị cao (> 1.000.000đ) chỉ shipper hạng Bạc trở lên mới nhận được
            if (order.getDeclaredValue() != null && order.getDeclaredValue().compareTo(highValueThreshold) > 0) {
                if (!tier.isCanAcceptHighValue()) {
                    continue;
                }
            }

            // Tính toán khoảng cách lệch tuyến (detour km)
            double detourKm = calculateDetourKm(trip, order, routeCoordinates);

            // BR-R-036: Điều kiện ghép đơn - chuyến: Độ lệch <= 3.0 km (PRM-099)
            if (detourKm <= maxDetourKm || (order.getPickupLatitude() == null && detourKm <= 4.0)) {
                double roundedDetour = Math.round(detourKm * 10.0) / 10.0;
                double matchScore = calculateMatchScore(trip, order, roundedDetour, tier);
                List<String> reasons = generateMatchReasons(trip, order, roundedDetour, matchScore);

                candidateResponses.add(TripOrderMatchResponse.fromOrder(order, roundedDetour, matchScore, reasons));

                // Lưu / cập nhật kết quả ghép vào DB-021 (trip_order_matches)
                saveOrUpdateMatchRecord(trip, order, roundedDetour, matchScore, reasons);
            }
        }

        // Xếp hạng: ưu tiên điểm phù hợp cao nhất (score desc), sau đó độ lệch thấp nhất (detour asc)
        candidateResponses.sort((a, b) -> {
            int scoreCompare = Double.compare(
                    b.getMatchScore() != null ? b.getMatchScore() : 0.0,
                    a.getMatchScore() != null ? a.getMatchScore() : 0.0
            );
            if (scoreCompare != 0) return scoreCompare;
            return Double.compare(
                    a.getDetourKm() != null ? a.getDetourKm() : 999.0,
                    b.getDetourKm() != null ? b.getDetourKm() : 999.0
            );
        });

        // Giới hạn số lượng gợi ý tối đa (PRM-100)
        if (candidateResponses.size() > maxSuggestions) {
            return candidateResponses.subList(0, maxSuggestions);
        }
        return candidateResponses;
    }

    /**
     * API-054 / UC-21: Gợi ý các chuyến đi / người giao tiện đường cho đơn hàng của người gửi.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrderCourierMatchResponse> getCouriersForOrder(Long orderId, User senderUser) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng yêu cầu."));

        // Người gửi chỉ xem gợi ý cho đơn hàng của mình
        if (order.getSenderId() != null && !order.getSenderId().equals(senderUser.getId())) {
            throw new AppException(GlobalErrorCode.FORBIDDEN, "Bạn không có quyền truy cập thông tin đơn hàng này.");
        }

        // Lấy tham số cấu hình: MATCH_DETOUR (PRM-099) và MATCH_TOPN (PRM-100)
        double maxDetourKm = getDoubleParameter("MATCH_DETOUR", 3.0);
        int maxSuggestions = getIntParameter("MATCH_TOPN", 10);
        BigDecimal highValueThreshold = getBigDecimalParameter("HIGH_VALUE", new BigDecimal("1000000"));

        // Lấy các chuyến đi đang lên lịch (SCHEDULED) hoặc đang di chuyển (ACTIVE)
        List<Trip> activeTrips = tripRepository.findByStatusAndDepartAtAfter(
                TripStatus.SCHEDULED,
                Instant.now().minus(Duration.ofHours(2)) // Cho phép chuyến sắp hoặc vừa khởi hành
        );

        List<OrderCourierMatchResponse> matchResponses = new ArrayList<>();

        for (Trip trip : activeTrips) {
            User courier = trip.getCourier();
            if (courier == null) continue;

            // BR-R-008: Người gửi không thể được gợi ý chuyến đi do chính mình tạo
            if (courier.getId().equals(senderUser.getId())) {
                continue;
            }

            // Kiểm tra tải trọng của chuyến đủ chứa kiện hàng
            if (order.getWeightKg() != null && trip.getCapacityKg() != null
                    && order.getWeightKg().doubleValue() > trip.getCapacityKg()) {
                continue;
            }

            // Kiểm tra giá sàn của chuyến
            if (trip.getFloorPrice() != null && order.getPriceSuggested() != null
                    && order.getPriceSuggested().compareTo(trip.getFloorPrice()) < 0) {
                continue;
            }

            // Thông tin hồ sơ bưu tá
            CourierProfile courierProfile = courierProfileRepository.findByUserId(courier.getId()).orElse(null);
            CourierTier tier = (courierProfile != null && courierProfile.getTier() != null)
                    ? courierProfile.getTier() : CourierTier.BRONZE;

            // BR-R-024: Kiểm tra hàng giá trị cao
            if (order.getDeclaredValue() != null && order.getDeclaredValue().compareTo(highValueThreshold) > 0) {
                if (!tier.isCanAcceptHighValue()) {
                    continue;
                }
            }

            List<double[]> routeCoordinates = parsePolylineCoordinates(trip.getRoutePolyline());
            double detourKm = calculateDetourKm(trip, order, routeCoordinates);

            if (detourKm <= maxDetourKm || (order.getPickupLatitude() == null && detourKm <= 4.0)) {
                double roundedDetour = Math.round(detourKm * 10.0) / 10.0;
                double matchScore = calculateMatchScore(trip, order, roundedDetour, tier);
                List<String> reasons = generateMatchReasons(trip, order, roundedDetour, matchScore);

                UserProfile profile = userProfileRepository.findById(courier.getId()).orElse(null);

                String displayName = profile != null && profile.getFullName() != null && !profile.getFullName().isBlank()
                        ? profile.getFullName()
                        : "Tài xế FPTPost #" + courier.getId();

                String maskedPhone = maskPhoneNumber(courier.getPhone());
                String avatarUrl = profile != null && profile.getAvatarStorageKey() != null
                        ? "/api/v1/files/" + profile.getAvatarStorageKey()
                        : null;

                OrderCourierMatchResponse item = OrderCourierMatchResponse.builder()
                        .tripId(trip.getId())
                        .courierId(courier.getId())
                        .courierPublicId(courier.getPublicId())
                        .courierName(displayName)
                        .courierAvatar(avatarUrl)
                        .courierPhone(maskedPhone)
                        .courierTier(tier)
                        .trustScore(courierProfile != null ? courierProfile.getTrustScore() : BigDecimal.valueOf(80))
                        .completedOrders(courierProfile != null ? courierProfile.getCompletedOrders() : 0)
                        .vehicleType(trip.getVehicleType())
                        .vehicleLabel(getVehicleLabel(trip.getVehicleType()))
                        .origin(trip.getOrigin())
                        .originLat(trip.getOriginLat())
                        .originLng(trip.getOriginLng())
                        .destination(trip.getDestination())
                        .destinationLat(trip.getDestinationLat())
                        .destinationLng(trip.getDestinationLng())
                        .departAt(trip.getDepartAt())
                        .distanceKm(trip.getDistanceKm())
                        .capacityKg(trip.getCapacityKg())
                        .floorPrice(trip.getFloorPrice())
                        .routePolyline(trip.getRoutePolyline())
                        .detourKm(roundedDetour)
                        .matchScore(matchScore)
                        .matchReasons(reasons)
                        .build();

                matchResponses.add(item);
            }
        }

        // Xếp hạng chuyến theo điểm phù hợp cao nhất
        matchResponses.sort((a, b) -> {
            int scoreCompare = Double.compare(
                    b.getMatchScore() != null ? b.getMatchScore() : 0.0,
                    a.getMatchScore() != null ? a.getMatchScore() : 0.0
            );
            if (scoreCompare != 0) return scoreCompare;
            return Double.compare(
                    a.getDetourKm() != null ? a.getDetourKm() : 999.0,
                    b.getDetourKm() != null ? b.getDetourKm() : 999.0
            );
        });

        if (matchResponses.size() > maxSuggestions) {
            return matchResponses.subList(0, maxSuggestions);
        }
        return matchResponses;
    }

    /**
     * Nhận đơn nhanh từ danh sách gợi ý của chuyến đi (Flow bước 5 & BR-F-038).
     */
    @Override
    @Transactional
    public MarketOrderAcceptResponse quickAcceptOrder(Long tripId, Long orderId, User courierUser) {
        Trip trip = tripRepository.findByIdAndCourierId(tripId, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST, "Chuyến đi này đã kết thúc hoặc đã hủy, không thể nhận đơn.");
        }

        // Gọi dịch vụ chấp nhận đơn hàng
        MarketOrderAcceptResponse acceptResponse = orderService.acceptMarketOrder(String.valueOf(orderId), courierUser);

        // Gắn đơn hàng vào chuyến đi
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setTripId(trip.getId());
            orderRepository.save(order);
        }

        // Cập nhật trạng thái ghép trong DB-021 sang ACCEPTED
        tripOrderMatchRepository.updateStatusByTripAndOrder(tripId, orderId, TripOrderMatchStatus.ACCEPTED);
        tripOrderMatchRepository.updateOtherMatchesForOrder(orderId, tripId, TripOrderMatchStatus.EXPIRED);

        log.info("Courier user #{} quick-accepted order #{} attached to trip #{}",
                courierUser.getId(), orderId, tripId);

        return acceptResponse;
    }

    /**
     * Chào giá nhanh từ danh sách gợi ý của chuyến đi (Flow bước 5 & BR-F-038).
     */
    @Override
    @Transactional
    public OfferResponse quickBidOrder(Long tripId, Long orderId, QuickBidRequest request, User courierUser) {
        tripRepository.findByIdAndCourierId(tripId, courierUser.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến đi yêu cầu."));

        OfferCreateRequest offerRequest = OfferCreateRequest.builder()
                .price(request.getPrice())
                .message(request.getMessage() != null && !request.getMessage().isBlank()
                        ? request.getMessage()
                        : "Tôi có chuyến đi tiện đường khởi hành gần thời gian này, sẵn sàng hỗ trợ giao an toàn!")
                .etaPickup(request.getEtaPickup())
                .build();

        OfferResponse offerResponse = offerService.createOffer(String.valueOf(orderId), offerRequest, courierUser);

        log.info("Courier user #{} quick-bid on order #{} via trip #{} with price: {}",
                courierUser.getId(), orderId, tripId, request.getPrice());

        return offerResponse;
    }

    // =========================================================================
    // THUẬT TOÁN ĐỊNH LƯỢNG ĐỘ LỆCH VÀ CHẤM ĐIỂM GHÉP ĐƠN (MATCHING ALGORITHM)
    // =========================================================================

    /**
     * Tính toán khoảng cách lệch tuyến ước tính (detour km).
     * Hỗ trợ giải mã routePolyline GeoJSON hoặc công thức phân đoạn Haversine.
     */
    private double calculateDetourKm(Trip trip, Order order, List<double[]> routeCoordinates) {
        if (order.getPickupLatitude() == null || order.getPickupLongitude() == null ||
                order.getDeliveryLatitude() == null || order.getDeliveryLongitude() == null) {
            return 1.5; // Ước lượng mặc định an toàn khi thiếu tọa độ
        }

        double pLat = order.getPickupLatitude().doubleValue();
        double pLng = order.getPickupLongitude().doubleValue();
        double dLat = order.getDeliveryLatitude().doubleValue();
        double dLng = order.getDeliveryLongitude().doubleValue();

        // 1. Nếu có tọa độ polyline chi tiết từ bản đồ
        if (routeCoordinates != null && routeCoordinates.size() >= 2) {
            double minPickupDist = Double.MAX_VALUE;
            double minDropoffDist = Double.MAX_VALUE;
            int closestPickupIdx = 0;
            int closestDropoffIdx = 0;

            for (int i = 0; i < routeCoordinates.size() - 1; i++) {
                double[] a = routeCoordinates.get(i);
                double[] b = routeCoordinates.get(i + 1);

                double distP = distanceToSegment(pLat, pLng, a[0], a[1], b[0], b[1]);
                if (distP < minPickupDist) {
                    minPickupDist = distP;
                    closestPickupIdx = i;
                }

                double distD = distanceToSegment(dLat, dLng, a[0], a[1], b[0], b[1]);
                if (distD < minDropoffDist) {
                    minDropoffDist = distD;
                    closestDropoffIdx = i;
                }
            }

            // Kiểm tra chiều di chuyển: điểm đón phải nằm trước hoặc cùng khu vực điểm giao
            double directionPenalty = (closestPickupIdx > closestDropoffIdx + 2) ? 2.5 : 0.0;
            return minPickupDist + minDropoffDist + directionPenalty;
        }

        // 2. Dự phòng: Tính khoảng cách theo cung đường thẳng O -> P -> D -> Dest so với O -> Dest
        double oLat = trip.getOriginLat();
        double oLng = trip.getOriginLng();
        double destLat = trip.getDestinationLat();
        double destLng = trip.getDestinationLng();

        double distDirect = haversine(oLat, oLng, destLat, destLng);
        double distViaOrder = haversine(oLat, oLng, pLat, pLng)
                + haversine(pLat, pLng, dLat, dLng)
                + haversine(dLat, dLng, destLat, destLng);

        double extraDetour = distViaOrder - distDirect;
        return Math.max(0.2, extraDetour);
    }

    /**
     * Chấm điểm độ phù hợp của đơn hàng và chuyến đi (Score: 0.0 - 100.0).
     */
    private double calculateMatchScore(Trip trip, Order order, double detourKm, CourierTier tier) {
        // 1. Điểm lệch tuyến (Tối đa 45 điểm)
        double detourScore;
        if (detourKm <= 0.5) {
            detourScore = 45.0;
        } else if (detourKm <= 3.0) {
            detourScore = 45.0 * (1.0 - (detourKm - 0.5) / 2.5);
        } else {
            detourScore = Math.max(5.0, 45.0 * (1.0 - (detourKm - 0.5) / 4.0));
        }

        // 2. Điểm tương thích thời gian khởi hành (Tối đa 25 điểm)
        double timeScore = 20.0;
        if (trip.getDepartAt() != null) {
            long hoursDiff = Math.abs(Duration.between(Instant.now(), trip.getDepartAt()).toHours());
            if (hoursDiff <= 4) {
                timeScore = 25.0;
            } else if (hoursDiff <= 12) {
                timeScore = 20.0;
            } else if (hoursDiff <= 24) {
                timeScore = 15.0;
            } else {
                timeScore = 10.0;
            }
        }

        // 3. Điểm tối ưu tải trọng bưu kiện (Tối đa 20 điểm)
        double capacityScore = 15.0;
        if (order.getWeightKg() != null && trip.getCapacityKg() != null && trip.getCapacityKg() > 0) {
            double loadRatio = order.getWeightKg().doubleValue() / trip.getCapacityKg();
            if (loadRatio >= 0.1 && loadRatio <= 0.75) {
                capacityScore = 20.0;
            } else if (loadRatio <= 1.0) {
                capacityScore = 16.0;
            } else {
                capacityScore = 5.0;
            }
        }

        // 4. Điểm kinh tế và xếp hạng uy tín (Tối đa 10 điểm)
        double trustScore = 7.0;
        if (tier == CourierTier.DIAMOND) trustScore = 10.0;
        else if (tier == CourierTier.GOLD) trustScore = 9.0;
        else if (tier == CourierTier.SILVER) trustScore = 8.0;

        double total = detourScore + timeScore + capacityScore + trustScore;
        return Math.min(100.0, Math.max(35.0, Math.round(total)));
    }

    /**
     * Tạo danh sách các lý do đánh giá độ phù hợp hiển thị trực quan cho người dùng.
     */
    private List<String> generateMatchReasons(Trip trip, Order order, double detourKm, double score) {
        List<String> reasons = new ArrayList<>();

        if (detourKm <= 1.0) {
            reasons.add("Lệch tuyến chỉ " + detourKm + " km, đi qua sát điểm giao nhận");
        } else {
            reasons.add("Lệch tuyến tiện đường ~" + detourKm + " km");
        }

        if (trip.getDepartAt() != null) {
            reasons.add("Giờ khởi hành: " + TIME_FORMATTER.format(trip.getDepartAt()));
        }

        if (order.getWeightKg() != null && trip.getCapacityKg() != null) {
            reasons.add("Kiện hàng " + order.getWeightKg() + " kg phù hợp sức chứa chuyến");
        }

        if (score >= 90.0) {
            reasons.add("Độ ăn khớp lộ trình xuất sắc");
        }

        return reasons;
    }

    /**
     * Lưu vết bản ghi kết quả gợi ý ghép đơn - chuyến theo đặc tả DB-021.
     */
    private void saveOrUpdateMatchRecord(Trip trip, Order order, double detourKm, double score, List<String> reasons) {
        try {
            TripOrderMatch match = tripOrderMatchRepository.findByTripIdAndOrderId(trip.getId(), order.getId())
                    .orElse(null);

            String reasonsStr = String.join("; ", reasons);

            if (match == null) {
                match = TripOrderMatch.builder()
                        .trip(trip)
                        .order(order)
                        .score(score)
                        .detourKm(detourKm)
                        .status(TripOrderMatchStatus.SUGGESTED)
                        .matchReasons(reasonsStr)
                        .build();
            } else {
                match.setScore(score);
                match.setDetourKm(detourKm);
                match.setMatchReasons(reasonsStr);
            }
            tripOrderMatchRepository.save(match);
        } catch (Exception ex) {
            // Không làm gián đoạn API nếu việc lưu vết gặp sự cố
            log.debug("Could not persist trip_order_match: {}", ex.getMessage());
        }
    }

    // =========================================================================
    // HÀM TIỆN ÍCH HÌNH HỌC & ĐỌC THAM SỐ
    // =========================================================================

    private double distanceToSegment(double pLat, double pLon, double aLat, double aLon, double bLat, double bLon) {
        double dAB = haversine(aLat, aLon, bLat, bLon);
        if (dAB < 1e-6) return haversine(pLat, pLon, aLat, aLon);

        double dAP = haversine(aLat, aLon, pLat, pLon);
        double dBP = haversine(bLat, bLon, pLat, pLon);

        // Vector projection approximation on local tangent plane
        double x1 = 0, y1 = 0;
        double x2 = dAB, y2 = 0;
        double xp = (dAP * dAP - dBP * dBP + dAB * dAB) / (2 * dAB);
        double ypSquared = Math.max(0.0, dAP * dAP - xp * xp);
        double yp = Math.sqrt(ypSquared);

        if (xp <= 0) return dAP;
        if (xp >= dAB) return dBP;
        return yp;
    }

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

    private List<double[]> parsePolylineCoordinates(String routePolyline) {
        if (routePolyline == null || routePolyline.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(routePolyline);
            JsonNode coordsNode = null;
            if (root.has("coordinates")) {
                coordsNode = root.get("coordinates");
            } else if (root.has("geometry") && root.get("geometry").has("coordinates")) {
                coordsNode = root.get("geometry").get("coordinates");
            } else if (root.isArray()) {
                coordsNode = root;
            }

            if (coordsNode != null && coordsNode.isArray()) {
                List<double[]> list = new ArrayList<>();
                for (JsonNode item : coordsNode) {
                    if (item.isArray() && item.size() >= 2) {
                        double c0 = item.get(0).asDouble();
                        double c1 = item.get(1).asDouble();
                        // GeoJSON uses [longitude, latitude]
                        if (c0 > 90 || c0 < -90) {
                            list.add(new double[]{c1, c0}); // [lat, lng]
                        } else {
                            list.add(new double[]{c0, c1});
                        }
                    }
                }
                return list;
            }
        } catch (Exception ignored) {
            // fallback
        }
        return Collections.emptyList();
    }

    private double getDoubleParameter(String key, double defaultValue) {
        try {
            return systemParameterRepository.findByKey(key)
                    .map(p -> Double.parseDouble(p.getValue()))
                    .orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getIntParameter(String key, int defaultValue) {
        try {
            return systemParameterRepository.findByKey(key)
                    .map(p -> Integer.parseInt(p.getValue()))
                    .orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BigDecimal getBigDecimalParameter(String key, BigDecimal defaultValue) {
        try {
            return systemParameterRepository.findByKey(key)
                    .map(p -> new BigDecimal(p.getValue()))
                    .orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        int len = phone.length();
        return phone.substring(0, 3) + "****" + phone.substring(len - 3);
    }

    private String getVehicleLabel(VehicleType type) {
        if (type == null) return "Phương tiện đường bộ";
        return switch (type) {
            case MOTORBIKE -> "Xe máy";
            case CAR -> "Ô tô";
            case BICYCLE -> "Xe đạp";
        };
    }
}

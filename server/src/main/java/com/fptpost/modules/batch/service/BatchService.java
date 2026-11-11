package com.fptpost.modules.batch.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.batch.dto.request.BatchCreateRequest;
import com.fptpost.modules.batch.dto.request.RouteOptimizeRequest;
import com.fptpost.modules.batch.dto.response.*;
import com.fptpost.modules.batch.model.BatchStopStatus;
import com.fptpost.modules.batch.model.BatchStopType;
import com.fptpost.modules.batch.model.DeliveryBatch;
import com.fptpost.modules.batch.model.DeliveryBatchStatus;
import com.fptpost.modules.batch.model.DeliveryBatchStop;
import com.fptpost.modules.batch.repository.DeliveryBatchRepository;
import com.fptpost.modules.batch.repository.DeliveryBatchStopRepository;
import com.fptpost.modules.batch.service.routing.ChCrpRoutingEngine;
import com.fptpost.modules.batch.service.routing.ChCrpRoutingEngine.GeoPoint;
import com.fptpost.modules.batch.service.routing.ChCrpRoutingEngine.RouteOptimizationPlan;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.trip.model.Trip;
import com.fptpost.modules.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService implements IBatchService {
    private static final Set<OrderStatus> RETURN_STATUSES = EnumSet.of(OrderStatus.DELIVERY_FAILED, OrderStatus.RETURNING, OrderStatus.RETURNED);

    private static final int BATCH_MAX_ORDERS = 5; // [PRM-101]
    private static final int BATCH_MIN_ORDERS = 2;
    private static final double DEFAULT_MAX_CAPACITY_KG = 20.0; // [PRM-012]

    private final DeliveryBatchRepository batchRepository;
    private final DeliveryBatchStopRepository stopRepository;
    private final OrderRepository orderRepository;
    private final TripRepository tripRepository;
    private final ChCrpRoutingEngine routingEngine;

    private final SecureRandom secureRandom = new SecureRandom();
    private static final DateTimeFormatter CODE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneId.of("UTC+7"));

    @Override
    @Transactional(readOnly = true)
    public List<EligibleBatchOrderResponse> getEligibleOrders(User courier) {
        if (courier == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        List<Order> courierOrders = orderRepository.findByCourierIdOrderByCreatedAtDesc(courier.getId());

        // Lọc các đơn hàng chưa hoàn thành, chưa hủy và đang trong giai đoạn giao nhận
        return courierOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .filter(o -> !RETURN_STATUSES.contains(o.getStatus()))
                .map(this::mapToEligibleOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RouteOptimizationResponse optimizeRoute(RouteOptimizeRequest request, User courier) {
        if (courier == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        List<Long> orderIds = request.getOrderIds();
        validateOrderCount(orderIds);

        List<Order> orders = orderRepository.findAllById(orderIds);
        validateCourierOwnership(orders, orderIds, courier.getId());

        // Kiểm tra tải trọng / sức chứa
        Trip trip = null;
        BigDecimal maxCapacity = BigDecimal.valueOf(DEFAULT_MAX_CAPACITY_KG);
        if (request.getTripId() != null) {
            trip = tripRepository.findById(request.getTripId()).orElse(null);
            if (trip != null && trip.getCapacityKg() != null) {
                maxCapacity = BigDecimal.valueOf(trip.getCapacityKg());
            }
        }

        BigDecimal totalWeight = orders.stream()
                .map(o -> o.getWeightKg() != null ? o.getWeightKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean isCapacityExceeded = totalWeight.compareTo(maxCapacity) > 0;

        // Vị trí xuất phát của tài xế
        Double startLat = request.getCurrentLatitude();
        Double startLng = request.getCurrentLongitude();
        if (startLat == null || startLng == null) {
            if (trip != null && trip.getOriginLat() != null && trip.getOriginLng() != null) {
                startLat = trip.getOriginLat();
                startLng = trip.getOriginLng();
            }
        }

        // Tính toán thứ tự điểm dừng tối ưu
        OptimizationResult optResult = calculateOptimalRoute(orders, startLat, startLng);

        return RouteOptimizationResponse.builder()
                .totalOrders(orders.size())
                .totalStops(optResult.stops.size())
                .totalWeightKg(totalWeight.setScale(2, RoundingMode.HALF_UP))
                .maxCapacityKg(maxCapacity.setScale(2, RoundingMode.HALF_UP))
                .isCapacityExceeded(isCapacityExceeded)
                .totalDistanceKm(BigDecimal.valueOf(optResult.totalDistanceKm).setScale(2, RoundingMode.HALF_UP))
                .estimatedDurationMinutes(optResult.estimatedDurationMinutes)
                .stops(optResult.stops)
                .optimizationMethod(optResult.algorithm != null ? optResult.algorithm : "Contraction Hierarchies (CH) & Customizable Route Planning (CRP)")
                .message(isCapacityExceeded
                        ? "Cảnh báo: Tổng khối lượng hàng vượt quá sức chứa cho phép (" + totalWeight + "kg > " + maxCapacity + "kg)"
                        : "Lộ trình đã được tối ưu hóa để có quãng đường di chuyển ngắn nhất")
                .build();
    }

    @Override
    @Transactional
    public DeliveryBatchResponse createBatch(BatchCreateRequest request, User courier) {
        if (courier == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        List<Long> orderIds = request.getOrderIds();
        validateOrderCount(orderIds);

        List<Order> orders = orderRepository.findAllById(orderIds);
        validateCourierOwnership(orders, orderIds, courier.getId());

        // Kiểm tra sức chứa theo BR-R-046
        Trip trip = null;
        BigDecimal maxCapacity = BigDecimal.valueOf(DEFAULT_MAX_CAPACITY_KG);
        if (request.getTripId() != null) {
            trip = tripRepository.findById(request.getTripId()).orElse(null);
            if (trip != null) {
                if (!trip.getCourier().getId().equals(courier.getId())) {
                    throw new AppException(GlobalErrorCode.FORBIDDEN, "Chuyến đi không thuộc quyền quản lý của bạn");
                }
                if (trip.getCapacityKg() != null) {
                    maxCapacity = BigDecimal.valueOf(trip.getCapacityKg());
                }
            }
        }

        BigDecimal totalWeight = orders.stream()
                .map(o -> o.getWeightKg() != null ? o.getWeightKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(maxCapacity) > 0) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST,
                    "Tổng khối lượng các đơn hàng (" + totalWeight + " kg) vượt quá sức chứa cho phép (" + maxCapacity + " kg)");
        }

        // Tối ưu hóa thứ tự các điểm dừng
        Double startLat = null;
        Double startLng = null;
        if (trip != null && trip.getOriginLat() != null && trip.getOriginLng() != null) {
            startLat = trip.getOriginLat();
            startLng = trip.getOriginLng();
        }

        OptimizationResult optResult = calculateOptimalRoute(orders, startLat, startLng);

        // Tạo Batch Code
        String batchCode = generateBatchCode();

        String batchName = request.getBatchName();
        if (batchName == null || batchName.trim().isEmpty()) {
            batchName = "Lượt giao " + orders.size() + " đơn - " + CODE_DATE_FORMAT.format(Instant.now());
        }

        DeliveryBatch batch = DeliveryBatch.builder()
                .batchCode(batchCode)
                .courier(courier)
                .trip(trip)
                .batchName(batchName)
                .status(DeliveryBatchStatus.IN_PROGRESS)
                .totalOrders(orders.size())
                .totalWeightKg(totalWeight.setScale(3, RoundingMode.HALF_UP))
                .totalDistanceKm(BigDecimal.valueOf(optResult.totalDistanceKm).setScale(2, RoundingMode.HALF_UP))
                .estimatedDurationMinutes(optResult.estimatedDurationMinutes)
                .notes(request.getNotes())
                .orders(new HashSet<>(orders))
                .build();

        // Tạo các điểm dừng DeliveryBatchStop
        Map<Long, Order> orderMap = orders.stream().collect(Collectors.toMap(Order::getId, o -> o));

        for (BatchStopResponse stopDto : optResult.stops) {
            Order ord = orderMap.get(stopDto.getOrderId());
            DeliveryBatchStop stop = DeliveryBatchStop.builder()
                    .order(ord)
                    .stopOrder(stopDto.getStopOrder())
                    .stopType(stopDto.getStopType())
                    .address(stopDto.getAddress())
                    .latitude(stopDto.getLatitude())
                    .longitude(stopDto.getLongitude())
                    .contactName(stopDto.getContactName())
                    .contactPhone(stopDto.getContactPhone())
                    .itemDescription(stopDto.getItemDescription())
                    .weightKg(stopDto.getWeightKg())
                    .distanceFromPrevKm(stopDto.getDistanceFromPrevKm())
                    .estimatedArrivalMinutes(stopDto.getEstimatedArrivalMinutes())
                    .status(BatchStopStatus.PENDING)
                    .build();
            batch.addStop(stop);
        }

        DeliveryBatch savedBatch = batchRepository.save(batch);
        log.info("Đã tạo thành công lượt giao gộp mã {} với {} đơn hàng cho tài xế {}",
                savedBatch.getBatchCode(), savedBatch.getTotalOrders(), courier.getId());

        return mapToDeliveryBatchResponse(savedBatch);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteOptimizationResponse getBatchRoute(Long batchId, User courier) {
        DeliveryBatch batch = getBatchAndValidateCourier(batchId, courier);

        List<BatchStopResponse> stopResponses = batch.getStops().stream()
                .map(this::mapToBatchStopResponse)
                .collect(Collectors.toList());

        return RouteOptimizationResponse.builder()
                .totalOrders(batch.getTotalOrders())
                .totalStops(stopResponses.size())
                .totalWeightKg(batch.getTotalWeightKg())
                .totalDistanceKm(batch.getTotalDistanceKm())
                .estimatedDurationMinutes(batch.getEstimatedDurationMinutes())
                .stops(stopResponses)
                .optimizationMethod("Đã tối ưu hóa và lưu trong hệ thống")
                .message("Lấy lộ trình chi tiết của lượt giao thành công")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryBatchResponse getBatchById(Long batchId, User courier) {
        DeliveryBatch batch = getBatchAndValidateCourier(batchId, courier);
        return mapToDeliveryBatchResponse(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeliveryBatchResponse> getMyBatches(User courier, Pageable pageable) {
        if (courier == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        Pageable effectivePageable = (pageable == null || pageable.getSort().isUnsorted())
                ? org.springframework.data.domain.PageRequest.of(
                        pageable != null ? pageable.getPageNumber() : 0,
                        pageable != null ? pageable.getPageSize() : 10,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                : pageable;
        Page<DeliveryBatch> batches = batchRepository.findByCourierIdOrderByCreatedAtDesc(courier.getId(), effectivePageable);
        return batches.map(this::mapToDeliveryBatchResponse);
    }

    @Override
    @Transactional
    public DeliveryBatchResponse completeStop(Long batchId, Long stopId, User courier) {
        DeliveryBatch batch = getBatchAndValidateCourier(batchId, courier);

        DeliveryBatchStop targetStop = batch.getStops().stream()
                .filter(s -> s.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy điểm dừng chỉ định"));

        targetStop.setStatus(BatchStopStatus.COMPLETED);
        targetStop.setCompletedAt(Instant.now());
        stopRepository.save(targetStop);

        // Kiểm tra xem tất cả các điểm dừng đã hoàn thành chưa
        boolean allCompleted = batch.getStops().stream()
                .allMatch(s -> s.getStatus() == BatchStopStatus.COMPLETED || s.getStatus() == BatchStopStatus.SKIPPED);

        if (allCompleted) {
            batch.setStatus(DeliveryBatchStatus.COMPLETED);
            batchRepository.save(batch);
        }

        return mapToDeliveryBatchResponse(batch);
    }

    @Override
    @Transactional
    public DeliveryBatchResponse updateBatchStatus(Long batchId, DeliveryBatchStatus status, User courier) {
        DeliveryBatch batch = getBatchAndValidateCourier(batchId, courier);
        batch.setStatus(status);
        DeliveryBatch updated = batchRepository.save(batch);
        return mapToDeliveryBatchResponse(updated);
    }

    // =========================================================================
    // =========================================================================
    // THUẬT TOÁN TỐI ƯU HÓA LỘ TRÌNH (Contraction Hierarchies & CRP Routing)
    // =========================================================================

    private static class OptimizationResult {
        List<BatchStopResponse> stops;
        double totalDistanceKm;
        int estimatedDurationMinutes;
        String algorithm;

        OptimizationResult(List<BatchStopResponse> stops, double totalDistanceKm, int estimatedDurationMinutes, String algorithm) {
            this.stops = stops;
            this.totalDistanceKm = totalDistanceKm;
            this.estimatedDurationMinutes = estimatedDurationMinutes;
            this.algorithm = algorithm;
        }
    }

    private OptimizationResult calculateOptimalRoute(List<Order> orders, Double startLat, Double startLng) {
        List<GeoPoint> pickups = new ArrayList<>();
        List<GeoPoint> deliveries = new ArrayList<>();

        for (Order o : orders) {
            double pLat = o.getPickupLatitude() != null ? o.getPickupLatitude().doubleValue() : 10.0299;
            double pLng = o.getPickupLongitude() != null ? o.getPickupLongitude().doubleValue() : 105.7705;
            double dLat = o.getDeliveryLatitude() != null ? o.getDeliveryLatitude().doubleValue() : 10.0350;
            double dLng = o.getDeliveryLongitude() != null ? o.getDeliveryLongitude().doubleValue() : 105.7800;

            GeoPoint p = GeoPoint.builder()
                    .orderId(o.getId())
                    .orderCode(o.getOrderCode())
                    .pointType("PICKUP")
                    .address(o.getPickupAddress())
                    .latitude(pLat)
                    .longitude(pLng)
                    .contactName("Người gửi")
                    .contactPhone("")
                    .itemDescription(o.getProductName())
                    .weightKg(o.getWeightKg() != null ? o.getWeightKg().doubleValue() : 1.0)
                    .build();

            GeoPoint d = GeoPoint.builder()
                    .orderId(o.getId())
                    .orderCode(o.getOrderCode())
                    .pointType("DELIVERY")
                    .address(o.getDeliveryAddress())
                    .latitude(dLat)
                    .longitude(dLng)
                    .contactName(o.getReceiverName())
                    .contactPhone(o.getReceiverPhone())
                    .itemDescription(o.getProductName())
                    .weightKg(o.getWeightKg() != null ? o.getWeightKg().doubleValue() : 1.0)
                    .build();

            pickups.add(p);
            deliveries.add(d);
        }

        RouteOptimizationPlan plan = routingEngine.solveOptimalRoute(pickups, deliveries, startLat, startLng);

        List<BatchStopResponse> stopResponses = new ArrayList<>();
        int cumulativeMinutes = 0;

        for (int i = 0; i < plan.getOrderedStops().size(); i++) {
            GeoPoint pt = plan.getOrderedStops().get(i);
            double legKm = plan.getLegDistancesKm().get(i);
            int legDur = plan.getLegDurationsMinutes().get(i);
            cumulativeMinutes += legDur;

            BatchStopResponse dto = BatchStopResponse.builder()
                    .orderId(pt.getOrderId())
                    .orderCode(pt.getOrderCode())
                    .stopOrder(i + 1)
                    .stopType(BatchStopType.valueOf(pt.getPointType()))
                    .address(pt.getAddress())
                    .latitude(BigDecimal.valueOf(pt.getLatitude()).setScale(7, RoundingMode.HALF_UP))
                    .longitude(BigDecimal.valueOf(pt.getLongitude()).setScale(7, RoundingMode.HALF_UP))
                    .contactName(pt.getContactName())
                    .contactPhone(pt.getContactPhone())
                    .itemDescription(pt.getItemDescription())
                    .weightKg(pt.getWeightKg() != null ? BigDecimal.valueOf(pt.getWeightKg()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ONE)
                    .distanceFromPrevKm(BigDecimal.valueOf(legKm).setScale(2, RoundingMode.HALF_UP))
                    .estimatedArrivalMinutes(cumulativeMinutes)
                    .status(BatchStopStatus.PENDING)
                    .stopKey(pt.getPointType() + "_" + pt.getOrderId())
                    .build();

            stopResponses.add(dto);
        }

        return new OptimizationResult(stopResponses, plan.getTotalDistanceKm(), plan.getTotalDurationMinutes(), plan.getRoutingAlgorithm());
    }
    // =========================================================================
    // VALIDATIONS VÀ MAPPERS
    // =========================================================================

    private void validateOrderCount(List<Long> orderIds) {
        if (orderIds == null || orderIds.size() < BATCH_MIN_ORDERS) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST,
                    "Cần chọn tối thiểu " + BATCH_MIN_ORDERS + " đơn hàng để thực hiện gộp lộ trình giao hàng");
        }
        if (orderIds.size() > BATCH_MAX_ORDERS) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST,
                    "Một lượt giao gộp tối đa không quá " + BATCH_MAX_ORDERS + " đơn hàng theo quy định nghiệp vụ");
        }
    }

    private void validateCourierOwnership(List<Order> orders, List<Long> requestedIds, Long courierId) {
        if (orders.size() != requestedIds.size()) {
            throw new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Một số đơn hàng được chọn không tồn tại trên hệ thống");
        }
        for (Order o : orders) {
            if (RETURN_STATUSES.contains(o.getStatus())) {
                throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
            }
            if (o.getCourierId() == null || !o.getCourierId().equals(courierId)) {
                throw new AppException(GlobalErrorCode.FORBIDDEN,
                        "Đơn hàng " + o.getOrderCode() + " không thuộc quyền giao hàng của bạn");
            }
        }
    }

    private DeliveryBatch getBatchAndValidateCourier(Long batchId, User courier) {
        if (courier == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        DeliveryBatch batch = batchRepository.findByIdWithDetails(batchId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lượt giao gộp với mã chỉ định"));

        if (!batch.getCourier().getId().equals(courier.getId())) {
            throw new AppException(GlobalErrorCode.FORBIDDEN, "Bạn không có quyền truy cập vào lượt giao gộp này");
        }
        return batch;
    }

    private String generateBatchCode() {
        String datePart = CODE_DATE_FORMAT.format(Instant.now());
        String code;
        int attempts = 0;
        do {
            int randomNum = 100000 + secureRandom.nextInt(900000);
            code = "BAT-" + datePart + "-" + randomNum;
            attempts++;
            if (attempts > 10) {
                code = "BAT-" + datePart + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                break;
            }
        } while (batchRepository.existsByBatchCode(code));
        return code;
    }

    private EligibleBatchOrderResponse mapToEligibleOrderResponse(Order o) {
        return EligibleBatchOrderResponse.builder()
                .id(o.getId())
                .orderCode(o.getOrderCode())
                .productName(o.getProductName())
                .productDescription(o.getProductDescription())
                .weightKg(o.getWeightKg())
                .declaredValue(o.getDeclaredValue())
                .priceAgreed(o.getPriceAgreed() != null ? o.getPriceAgreed() : o.getPriceSuggested())
                .pickupAddress(o.getPickupAddress())
                .pickupLatitude(o.getPickupLatitude())
                .pickupLongitude(o.getPickupLongitude())
                .deliveryAddress(o.getDeliveryAddress())
                .deliveryLatitude(o.getDeliveryLatitude())
                .deliveryLongitude(o.getDeliveryLongitude())
                .receiverName(o.getReceiverName())
                .receiverPhone(o.getReceiverPhone())
                .status(o.getStatus() != null ? o.getStatus().name() : "")
                .tripId(o.getTripId())
                .createdAt(o.getCreatedAt())
                .build();
    }

    private BatchStopResponse mapToBatchStopResponse(DeliveryBatchStop stop) {
        return BatchStopResponse.builder()
                .id(stop.getId())
                .orderId(stop.getOrder() != null ? stop.getOrder().getId() : null)
                .orderCode(stop.getOrder() != null ? stop.getOrder().getOrderCode() : "")
                .stopOrder(stop.getStopOrder())
                .stopType(stop.getStopType())
                .address(stop.getAddress())
                .latitude(stop.getLatitude())
                .longitude(stop.getLongitude())
                .contactName(stop.getContactName())
                .contactPhone(stop.getContactPhone())
                .itemDescription(stop.getItemDescription())
                .weightKg(stop.getWeightKg())
                .distanceFromPrevKm(stop.getDistanceFromPrevKm())
                .estimatedArrivalMinutes(stop.getEstimatedArrivalMinutes())
                .status(stop.getStatus())
                .completedAt(stop.getCompletedAt())
                .stopKey(stop.getStopType().name() + "_" + (stop.getOrder() != null ? stop.getOrder().getId() : "0"))
                .build();
    }

    private BatchOrderSummaryResponse mapToBatchOrderSummaryResponse(Order o) {
        return BatchOrderSummaryResponse.builder()
                .orderId(o.getId())
                .orderCode(o.getOrderCode())
                .productName(o.getProductName())
                .weightKg(o.getWeightKg())
                .declaredValue(o.getDeclaredValue())
                .priceAgreed(o.getPriceAgreed())
                .pickupAddress(o.getPickupAddress())
                .deliveryAddress(o.getDeliveryAddress())
                .receiverName(o.getReceiverName())
                .receiverPhone(o.getReceiverPhone())
                .status(o.getStatus() != null ? o.getStatus().name() : "")
                .build();
    }

    private DeliveryBatchResponse mapToDeliveryBatchResponse(DeliveryBatch b) {
        List<BatchStopResponse> stops = b.getStops().stream()
                .map(this::mapToBatchStopResponse)
                .collect(Collectors.toList());

        List<BatchOrderSummaryResponse> orders = b.getOrders().stream()
                .map(this::mapToBatchOrderSummaryResponse)
                .collect(Collectors.toList());

        return DeliveryBatchResponse.builder()
                .id(b.getId())
                .batchCode(b.getBatchCode())
                .batchName(b.getBatchName())
                .status(b.getStatus())
                .totalOrders(b.getTotalOrders())
                .totalWeightKg(b.getTotalWeightKg())
                .totalDistanceKm(b.getTotalDistanceKm())
                .estimatedDurationMinutes(b.getEstimatedDurationMinutes())
                .notes(b.getNotes())
                .tripId(b.getTrip() != null ? b.getTrip().getId() : null)
                .tripOrigin(b.getTrip() != null ? b.getTrip().getOrigin() : null)
                .tripDestination(b.getTrip() != null ? b.getTrip().getDestination() : null)
                .stops(stops)
                .orders(orders)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}

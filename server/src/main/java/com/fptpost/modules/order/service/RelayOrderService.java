package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.order.dto.PricingEstimateRequest;
import com.fptpost.modules.order.dto.PricingEstimateResponse;
import com.fptpost.modules.order.dto.RelayPoint;
import com.fptpost.modules.order.dto.request.RelayOrderRequest;
import com.fptpost.modules.order.dto.request.RelayPricingEstimateRequest;
import com.fptpost.modules.order.dto.response.OrderLegResponse;
import com.fptpost.modules.order.dto.response.RelayOrderResponse;
import com.fptpost.modules.order.dto.response.RelayPricingEstimateResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.*;
import com.fptpost.modules.order.repository.OrderLegRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelayOrderService {

    private static final int MAX_RELAY_POINTS = 2; // Tối đa 2 điểm hẹn -> tối đa 3 chặng (PRM-102)

    private final OrderRepository orderRepository;
    private final OrderLegRepository orderLegRepository;
    private final PricingCalculatorService pricingCalculatorService;
    private final OrderSubmissionScreening orderSubmissionScreening;
    private final OrderMapper orderMapper;

    /**
     * Ước tính quãng đường và phân chia cước phí các chặng tiếp sức theo quy tắc BR-R-047.
     */
    public RelayPricingEstimateResponse estimateRelayPricing(RelayPricingEstimateRequest request) {
        validateRelayPoints(request.getRelayPoints());

        // Tạo danh sách các điểm dừng: Bắt đầu -> Điểm hẹn 1 [-> Điểm hẹn 2] -> Kết thúc
        List<Waypoint> waypoints = buildWaypoints(
                request.getPickupLat(), request.getPickupLng(), "Điểm gửi hàng",
                request.getRelayPoints(),
                request.getDeliveryLat(), request.getDeliveryLng(), "Điểm nhận hàng"
        );

        int numLegs = waypoints.size() - 1;
        List<BigDecimal> legDistances = new ArrayList<>();
        BigDecimal totalDistance = BigDecimal.ZERO;

        for (int i = 0; i < numLegs; i++) {
            Waypoint from = waypoints.get(i);
            Waypoint to = waypoints.get(i + 1);

            PricingEstimateRequest legReq = PricingEstimateRequest.builder()
                    .pickupLat(from.lat)
                    .pickupLng(from.lng)
                    .deliveryLat(to.lat)
                    .deliveryLng(to.lng)
                    .weightKg(request.getWeightKg() != null ? request.getWeightKg() : BigDecimal.valueOf(1.0))
                    .build();

            PricingEstimateResponse legEst = pricingCalculatorService.estimatePrice(legReq);
            BigDecimal d = legEst.getDistanceKm() != null ? legEst.getDistanceKm().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            legDistances.add(d);
            totalDistance = totalDistance.add(d);
        }

        // Tính tổng cước phí chuẩn cho cả đơn hàng
        DeliveryUrgency urgency = request.getUrgency() != null ? request.getUrgency() : DeliveryUrgency.STANDARD;
        PricingEstimateRequest totalReq = PricingEstimateRequest.builder()
                .distanceKm(totalDistance)
                .weightKg(request.getWeightKg() != null ? request.getWeightKg() : BigDecimal.valueOf(1.0))
                .declaredValue(request.getDeclaredValue())
                .urgency(urgency)
                .build();
        PricingEstimateResponse totalEstimate = pricingCalculatorService.estimatePrice(totalReq);
        BigDecimal totalPrice = totalEstimate.getTotalEstimatedPrice() != null ? totalEstimate.getTotalEstimatedPrice().setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // Phân chia phí từng chặng theo tỷ lệ quãng đường (BR-R-047)
        List<RelayPricingEstimateResponse.LegPricing> legPricings = splitLegFees(
                waypoints, legDistances, totalDistance, totalPrice
        );

        return RelayPricingEstimateResponse.builder()
                .totalDistanceKm(totalDistance)
                .totalEstimatedPrice(totalPrice)
                .urgency(urgency)
                .legs(legPricings)
                .build();
    }

    /**
     * Tạo mới đơn giao tiếp sức chính thức (UC-32, API-082).
     */
    @Transactional
    public RelayOrderResponse createRelayOrder(Long senderId, RelayOrderRequest request) {
        validateRelayPoints(request.getRelayPoints());

        // 1. Tính toán lộ trình và cước phí các chặng
        RelayPricingEstimateRequest estimateReq = new RelayPricingEstimateRequest();
        estimateReq.setPickupLat(request.getPickupLatitude());
        estimateReq.setPickupLng(request.getPickupLongitude());
        estimateReq.setDeliveryLat(request.getDeliveryLatitude());
        estimateReq.setDeliveryLng(request.getDeliveryLongitude());
        estimateReq.setWeightKg(request.getWeightKg());
        estimateReq.setDeclaredValue(request.getDeclaredValue());
        estimateReq.setUrgency(request.getUrgency() != null ? request.getUrgency() : DeliveryUrgency.STANDARD);
        estimateReq.setRelayPoints(request.getRelayPoints());

        RelayPricingEstimateResponse estimate = estimateRelayPricing(estimateReq);

        // 2. Tạo đơn hàng chính
        String orderCode = generateUniqueOrderCode();
        Order order = Order.builder()
                .orderCode(orderCode)
                .senderId(senderId)
                .pickupAddress(request.getPickupAddress().trim())
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .deliveryAddress(request.getDeliveryAddress().trim())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .receiverName(request.getReceiverName().trim())
                .receiverPhone(request.getReceiverPhone().trim())
                .productName(request.getProductName().trim())
                .productDescription(request.getProductDescription() != null ? request.getProductDescription().trim() : null)
                .categoryId(request.getCategoryId())
                .quantity(request.getQuantity() != null ? request.getQuantity() : 1)
                .weightKg(request.getWeightKg() != null ? request.getWeightKg() : BigDecimal.valueOf(1.0))
                .declaredValue(request.getDeclaredValue() != null ? request.getDeclaredValue() : BigDecimal.ZERO)
                .priceSuggested(estimate.getTotalEstimatedPrice())
                .distanceKm(estimate.getTotalDistanceKm())
                .urgency(estimate.getUrgency())
                .deliveryNote(request.getDeliveryNote() != null ? request.getDeliveryNote().trim() : null)
                .status(OrderStatus.PENDING)
                .isRelay(true)
                .build();

        // 3. Thêm hình ảnh nếu có
        if (request.getImageUrls() != null) {
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    order.addImage(OrderImage.builder().imageUrl(imageUrl.trim()).build());
                }
            }
        }

        // 4. Khởi tạo các bản ghi chặng (OrderLeg - DB-022)
        List<Waypoint> waypoints = buildWaypoints(
                request.getPickupLatitude(), request.getPickupLongitude(), request.getPickupAddress(),
                request.getRelayPoints(),
                request.getDeliveryLatitude(), request.getDeliveryLongitude(), request.getDeliveryAddress()
        );

        for (int i = 0; i < estimate.getLegs().size(); i++) {
            RelayPricingEstimateResponse.LegPricing legPricing = estimate.getLegs().get(i);
            Waypoint from = waypoints.get(i);
            Waypoint to = waypoints.get(i + 1);

            OrderLeg leg = OrderLeg.builder()
                    .order(order)
                    .legNo(legPricing.getLegNo())
                    .pickupAddress(from.address)
                    .pickupLatitude(from.lat)
                    .pickupLongitude(from.lng)
                    .deliveryAddress(to.address)
                    .deliveryLatitude(to.lat)
                    .deliveryLongitude(to.lng)
                    .distanceKm(legPricing.getDistanceKm())
                    .legFee(legPricing.getEstimatedFee())
                    .status(OrderStatus.FINDING_SHIPPER)
                    .build();

            order.addLeg(leg);
        }

        // 5. Lưu vào CSDL & kiểm duyệt tự động
        Order saved = orderRepository.save(order);
        orderSubmissionScreening.screen(saved);
        log.info("Created relay order: id={}, orderCode={}, totalFee={}, numLegs={}",
                saved.getId(), saved.getOrderCode(), saved.getPriceSuggested(), saved.getLegs().size());

        // 6. Ánh xạ trả về RelayOrderResponse
        return toRelayResponse(saved);
    }

    /**
     * Lấy danh sách các chặng của đơn tiếp sức.
     */
    public List<OrderLegResponse> getOrderLegs(Long orderId) {
        List<OrderLeg> legs = orderLegRepository.findByOrderIdOrderByLegNoAsc(orderId);
        return legs.stream().map(this::toLegResponse).toList();
    }

    private void validateRelayPoints(List<RelayPoint> relayPoints) {
        if (relayPoints == null || relayPoints.isEmpty()) {
            throw new AppException(OrderErrorCode.ORDER_CANNOT_BE_EDITED, "Đơn tiếp sức cần ít nhất 1 điểm hẹn bàn giao trung gian.");
        }
        if (relayPoints.size() > MAX_RELAY_POINTS) {
            throw new AppException(OrderErrorCode.ORDER_CANNOT_BE_EDITED, "Đơn tiếp sức có tối đa 2 điểm hẹn trung gian (tối đa 3 chặng theo PRM-102).");
        }
    }

    private List<Waypoint> buildWaypoints(BigDecimal startLat, BigDecimal startLng, String startAddr,
                                          List<RelayPoint> relayPoints,
                                          BigDecimal endLat, BigDecimal endLng, String endAddr) {
        List<Waypoint> list = new ArrayList<>();
        list.add(new Waypoint(startLat, startLng, startAddr));
        for (RelayPoint rp : relayPoints) {
            list.add(new Waypoint(rp.getLatitude(), rp.getLongitude(), rp.getAddress()));
        }
        list.add(new Waypoint(endLat, endLng, endAddr));
        return list;
    }

    private List<RelayPricingEstimateResponse.LegPricing> splitLegFees(
            List<Waypoint> waypoints, List<BigDecimal> legDistances,
            BigDecimal totalDistance, BigDecimal totalPrice) {

        int numLegs = legDistances.size();
        List<RelayPricingEstimateResponse.LegPricing> result = new ArrayList<>();
        BigDecimal allocatedTotal = BigDecimal.ZERO;

        for (int i = 0; i < numLegs; i++) {
            BigDecimal d = legDistances.get(i);
            BigDecimal fee;

            if (i == numLegs - 1) {
                // Chặng cuối cùng: lấy toàn bộ phần cước còn lại để đảm bảo tổng đúng 100%
                fee = totalPrice.subtract(allocatedTotal).max(BigDecimal.ZERO);
            } else {
                double ratio = totalDistance.compareTo(BigDecimal.ZERO) > 0
                        ? d.doubleValue() / totalDistance.doubleValue()
                        : 1.0 / numLegs;
                fee = totalPrice.multiply(BigDecimal.valueOf(ratio))
                        .setScale(0, RoundingMode.HALF_UP);
                allocatedTotal = allocatedTotal.add(fee);
            }

            double percentage = totalDistance.compareTo(BigDecimal.ZERO) > 0
                    ? (d.doubleValue() / totalDistance.doubleValue()) * 100.0
                    : 100.0 / numLegs;

            result.add(RelayPricingEstimateResponse.LegPricing.builder()
                    .legNo(i + 1)
                    .fromAddress(waypoints.get(i).address)
                    .toAddress(waypoints.get(i + 1).address)
                    .distanceKm(d)
                    .estimatedFee(fee)
                    .percentage(Math.round(percentage * 10.0) / 10.0)
                    .build());
        }

        return result;
    }

    private String generateUniqueOrderCode() {
        String code;
        do {
            code = "RL" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }

    private RelayOrderResponse toRelayResponse(Order order) {
        RelayOrderResponse res = new RelayOrderResponse();
        res.setId(order.getId());
        res.setOrderCode(order.getOrderCode());
        res.setSenderId(order.getSenderId());
        res.setCourierId(order.getCourierId());
        res.setPickupAddress(order.getPickupAddress());
        res.setPickupLatitude(order.getPickupLatitude());
        res.setPickupLongitude(order.getPickupLongitude());
        res.setDeliveryAddress(order.getDeliveryAddress());
        res.setDeliveryLatitude(order.getDeliveryLatitude());
        res.setDeliveryLongitude(order.getDeliveryLongitude());
        res.setReceiverName(order.getReceiverName());
        res.setReceiverPhone(order.getReceiverPhone());
        res.setProductName(order.getProductName());
        res.setProductDescription(order.getProductDescription());
        res.setQuantity(order.getQuantity());
        res.setWeightKg(order.getWeightKg());
        res.setDeclaredValue(order.getDeclaredValue());
        res.setPriceSuggested(order.getPriceSuggested());
        res.setDistanceKm(order.getDistanceKm());
        res.setUrgency(order.getUrgency());
        res.setDeliveryNote(order.getDeliveryNote());
        res.setStatus(order.getStatus());
        res.setCreatedAt(order.getCreatedAt());
        res.setUpdatedAt(order.getUpdatedAt());
        res.setIsRelay(order.getIsRelay());

        if (order.getImages() != null) {
            res.setImageUrls(order.getImages().stream().map(OrderImage::getImageUrl).toList());
        }

        if (order.getLegs() != null) {
            res.setLegs(order.getLegs().stream().map(this::toLegResponse).toList());
        }

        return res;
    }

    private OrderLegResponse toLegResponse(OrderLeg leg) {
        return OrderLegResponse.builder()
                .id(leg.getId())
                .legNo(leg.getLegNo())
                .pickupAddress(leg.getPickupAddress())
                .pickupLatitude(leg.getPickupLatitude())
                .pickupLongitude(leg.getPickupLongitude())
                .deliveryAddress(leg.getDeliveryAddress())
                .deliveryLatitude(leg.getDeliveryLatitude())
                .deliveryLongitude(leg.getDeliveryLongitude())
                .distanceKm(leg.getDistanceKm())
                .legFee(leg.getLegFee())
                .courierId(leg.getCourierId())
                .status(leg.getStatus())
                .createdAt(leg.getCreatedAt())
                .build();
    }

    private record Waypoint(BigDecimal lat, BigDecimal lng, String address) {}
}

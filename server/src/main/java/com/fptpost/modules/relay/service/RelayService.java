package com.fptpost.modules.relay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.DeliveryUrgency;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.PricingCalculatorService;
import com.fptpost.modules.order.dto.PricingEstimateRequest;
import com.fptpost.modules.order.dto.PricingEstimateResponse;
import com.fptpost.modules.relay.dto.*;
import com.fptpost.modules.relay.model.*;
import com.fptpost.modules.relay.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelayService implements IRelayService {

    private final OrderRepository orderRepository;
    private final OrderLegRepository orderLegRepository;
    private final MeetingPointRepository meetingPointRepository;
    private final HandoverTokenRepository handoverTokenRepository;
    private final HandoverRecordRepository handoverRecordRepository;
    private final HandoverIncidentRepository handoverIncidentRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PricingCalculatorService pricingCalculatorService;
    private final ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * Thời hạn hiệu lực của mã QR bàn giao: 10 phút (theo PRM-097).
     */
    private static final int HANDOVER_QR_TTL_MINUTES = 10;

    /**
     * Số chặng tối đa của một đơn hàng tiếp sức: 3 chặng (theo PRM-102).
     */
    private static final int MAX_RELAY_LEGS = 3;

    @Override
    @Transactional(readOnly = true)
    public List<MeetingPointResponse> getSafeMeetingPoints(String campusArea) {
        List<MeetingPoint> points = (campusArea == null || campusArea.isBlank())
                ? meetingPointRepository.findByIsActiveTrue()
                : meetingPointRepository.findByCampusAreaAndIsActiveTrue(campusArea);

        return points.stream()
                .map(this::mapToMeetingPointResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderLegResponse markReadyForHandover(Long orderId, Integer legNo, User courier) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        OrderLeg leg = orderLegRepository.findByOrderIdAndLegNo(orderId, legNo)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin chặng " + legNo));

        if (leg.getCourierId() == null || !leg.getCourierId().equals(courier.getId())) {
            throw new AppException(GlobalErrorCode.FORBIDDEN);
        }

        if (leg.getStatus() == LegStatus.HANDED_OVER || leg.getStatus() == LegStatus.COMPLETED) {
            throw new IllegalArgumentException("Chặng này đã được bàn giao hoặc hoàn tất");
        }

        leg.setStatus(LegStatus.READY_FOR_HANDOVER);
        orderLegRepository.save(leg);

        boolean isFinalLeg = order.getTotalLegs() != null && Objects.equals(order.getTotalLegs(), leg.getLegNo());
        boolean hasNextLeg = !isFinalLeg;

        return OrderLegResponse.builder()
                .id(leg.getId())
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .productName(order.getProductName())
                .legNo(leg.getLegNo())
                .courierId(leg.getCourierId())
                .courierName(leg.getCourierName())
                .courierPhone(leg.getCourierPhone())
                .startAddress(leg.getStartAddress())
                .startLatitude(leg.getStartLatitude())
                .startLongitude(leg.getStartLongitude())
                .endAddress(leg.getEndAddress())
                .endLatitude(leg.getEndLatitude())
                .endLongitude(leg.getEndLongitude())
                .meetingPoint(leg.getMeetingPoint() != null ? mapToMeetingPointResponse(leg.getMeetingPoint()) : null)
                .distanceKm(leg.getDistanceKm())
                .fee(leg.getFee())
                .status(leg.getStatus())
                .startedAt(leg.getStartedAt())
                .handedOverAt(leg.getHandedOverAt())
                .completedAt(leg.getCompletedAt())
                .handoverNote(leg.getHandoverNote())
                .isCurrentLeg(true)
                .isFinalLeg(isFinalLeg)
                .canHandover(hasNextLeg)
                .canReceiveHandover(false)
                .build();
    }

    @Override
    @Transactional
    public HandoverTokenResponse generateHandoverToken(Long orderId, Integer legNo, User courier) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        OrderLeg currentLeg = orderLegRepository.findByOrderIdAndLegNo(orderId, legNo)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin chặng " + legNo + " của đơn hàng"));

        // Kiểm tra quyền sở hữu chặng: Người tạo mã phải là người giao chặng này
        if (currentLeg.getCourierId() == null || !currentLeg.getCourierId().equals(courier.getId())) {
            throw new AppException(GlobalErrorCode.FORBIDDEN);
        }

        // Chặng hiện tại không được ở trạng thái đã bàn giao hoặc hoàn tất
        if (currentLeg.getStatus() == LegStatus.HANDED_OVER || currentLeg.getStatus() == LegStatus.COMPLETED) {
            throw new IllegalArgumentException("Chặng này đã được bàn giao hoặc hoàn tất trước đó");
        }

        // Kiểm tra chặng tiếp theo có tồn tại không
        orderLegRepository.findByOrderIdAndLegNo(orderId, legNo + 1)
                .orElseThrow(() -> new IllegalArgumentException("Chặng này là chặng cuối cùng của đơn hàng, bưu kiện sẽ được giao trực tiếp cho người nhận"));

        // Hủy hoặc vô hiệu các token cũ chưa sử dụng của chặng này
        handoverTokenRepository.findTopByOrderIdAndLegNoAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                orderId, legNo, Instant.now()
        ).ifPresent(oldToken -> {
            oldToken.setIsUsed(true);
            handoverTokenRepository.save(oldToken);
        });

        // Tạo mã ngẫu nhiên 8 ký tự dễ đọc
        String tokenCode = generateRandomTokenCode();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(HANDOVER_QR_TTL_MINUTES));

        HandoverToken token = HandoverToken.builder()
                .order(order)
                .leg(currentLeg)
                .legNo(legNo)
                .tokenCode(tokenCode)
                .purpose("HANDOVER")
                .generatedByCourierId(courier.getId())
                .expiresAt(expiresAt)
                .attempts(0)
                .isUsed(false)
                .build();

        handoverTokenRepository.save(token);

        // Chuyển trạng thái chặng hiện tại sang READY_FOR_HANDOVER nếu đang IN_TRANSIT hoặc ASSIGNED
        if (currentLeg.getStatus() == LegStatus.IN_TRANSIT || currentLeg.getStatus() == LegStatus.ASSIGNED) {
            currentLeg.setStatus(LegStatus.READY_FOR_HANDOVER);
            orderLegRepository.save(currentLeg);
        }

        // Xây dựng QR payload
        String qrData = buildQrData(order, currentLeg, tokenCode, expiresAt);

        String meetingPointName = currentLeg.getMeetingPoint() != null ? currentLeg.getMeetingPoint().getName() : currentLeg.getEndAddress();
        String meetingPointAddress = currentLeg.getMeetingPoint() != null ? currentLeg.getMeetingPoint().getAddress() : currentLeg.getEndAddress();

        return HandoverTokenResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .legNo(legNo)
                .tokenCode(tokenCode)
                .qrData(qrData)
                .generatedByCourierId(courier.getId())
                .generatedByCourierName(resolveCourierName(courier))
                .expiresAt(expiresAt)
                .remainingSeconds(Duration.between(Instant.now(), expiresAt).getSeconds())
                .meetingPointName(meetingPointName)
                .meetingPointAddress(meetingPointAddress)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HandoverTokenResponse getActiveHandoverToken(Long orderId, Integer legNo, User courier) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        OrderLeg currentLeg = orderLegRepository.findByOrderIdAndLegNo(orderId, legNo)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin chặng"));

        Optional<HandoverToken> activeTokenOpt = handoverTokenRepository
                .findTopByOrderIdAndLegNoAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(orderId, legNo, Instant.now());

        if (activeTokenOpt.isEmpty()) {
            return null;
        }

        HandoverToken token = activeTokenOpt.get();
        String qrData = buildQrData(order, currentLeg, token.getTokenCode(), token.getExpiresAt());

        String meetingPointName = currentLeg.getMeetingPoint() != null ? currentLeg.getMeetingPoint().getName() : currentLeg.getEndAddress();
        String meetingPointAddress = currentLeg.getMeetingPoint() != null ? currentLeg.getMeetingPoint().getAddress() : currentLeg.getEndAddress();

        return HandoverTokenResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .legNo(legNo)
                .tokenCode(token.getTokenCode())
                .qrData(qrData)
                .generatedByCourierId(token.getGeneratedByCourierId())
                .generatedByCourierName(resolveCourierName(courier))
                .expiresAt(token.getExpiresAt())
                .remainingSeconds(Math.max(0, Duration.between(Instant.now(), token.getExpiresAt()).getSeconds()))
                .meetingPointName(meetingPointName)
                .meetingPointAddress(meetingPointAddress)
                .build();
    }

    @Override
    @Transactional
    public HandoverConfirmResponse confirmHandover(Long orderId, Integer legNo, HandoverConfirmRequest request, User courier) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        OrderLeg currentLeg = orderLegRepository.findByOrderIdAndLegNo(orderId, legNo)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin chặng " + legNo));

        // Kiểm tra quy tắc BR-R-048: Chặng trước chưa hoàn thành/bàn giao thì chặng sau không thể bắt đầu
        if (currentLeg.getStatus() == LegStatus.HANDED_OVER) {
            throw new IllegalArgumentException("Chặng này đã được bàn giao thành công trước đó");
        }

        // Tìm chặng kế tiếp
        OrderLeg nextLeg = orderLegRepository.findByOrderIdAndLegNo(orderId, legNo + 1)
                .orElseThrow(() -> new IllegalArgumentException("Chặng này là chặng cuối cùng của đơn hàng, bưu kiện sẽ được giao trực tiếp cho người nhận"));

        // Người quét mã phải là người giao chặng tiếp theo (hoặc tiếp nhận chặng này nếu chưa được gán)
        if (nextLeg.getCourierId() != null && !nextLeg.getCourierId().equals(courier.getId())) {
            throw new IllegalArgumentException("Bạn không phải người giao được chỉ định cho chặng tiếp theo (" + (legNo + 1) + ")");
        }

        // Người giao chặng trước không được tự bàn giao cho chính mình
        if (currentLeg.getCourierId() != null && currentLeg.getCourierId().equals(courier.getId())) {
            throw new IllegalArgumentException("Người giao không thể tự bàn giao cho chính mình");
        }

        // Kiểm tra mã xác nhận bàn giao
        String inputCode = request.getTokenCode().trim().toUpperCase();
        HandoverToken token = handoverTokenRepository.findByTokenCode(inputCode)
                .orElseThrow(() -> new IllegalArgumentException("Mã xác thực bàn giao không chính xác"));

        if (!token.getOrder().getId().equals(orderId) || !token.getLegNo().equals(legNo)) {
            throw new IllegalArgumentException("Mã xác thực không thuộc về chặng bàn giao này");
        }

        if (Boolean.TRUE.equals(token.getIsUsed())) {
            throw new IllegalArgumentException("Mã xác thực này đã được sử dụng");
        }

        // Kiểm tra hết hạn: Ngoại lệ A1 QR hết hạn
        if (token.isExpired()) {
            throw new IllegalArgumentException("Mã bàn giao đã hết hạn (quá " + HANDOVER_QR_TTL_MINUTES + " phút). Vui lòng yêu cầu người giao trước làm mới mã");
        }

        // Đánh dấu token đã sử dụng
        token.setIsUsed(true);
        token.setUsedAt(Instant.now());
        token.setUsedByCourierId(courier.getId());
        handoverTokenRepository.save(token);

        Instant now = Instant.now();
        String nextCourierName = resolveCourierName(courier);

        // Chuyển trạng thái chặng hiện tại sang HANDED_OVER (BR-F-060)
        currentLeg.setStatus(LegStatus.HANDED_OVER);
        currentLeg.setHandedOverAt(now);
        currentLeg.setHandoverNote(request.getNotes());
        orderLegRepository.save(currentLeg);

        // Bắt đầu chặng tiếp theo và gán trách nhiệm cho người giao mới (BR-R-048)
        nextLeg.setCourierId(courier.getId());
        nextLeg.setCourierName(nextCourierName);
        nextLeg.setCourierPhone(courier.getPhone());
        nextLeg.setStatus(LegStatus.IN_TRANSIT);
        nextLeg.setStartedAt(now);
        orderLegRepository.save(nextLeg);

        // Chuyển trách nhiệm đơn hàng sang người giao chặng tiếp theo
        order.setCourierId(courier.getId());
        order.setCurrentLegNo(legNo + 1);
        order.setStatus(OrderStatus.IN_DELIVERY);
        orderRepository.save(order);

        // Lưu bằng chứng bàn giao (BR-F-060: ảnh hàng, vị trí, thời gian)
        String meetingPointName = currentLeg.getMeetingPoint() != null
                ? currentLeg.getMeetingPoint().getName()
                : currentLeg.getEndAddress();

        HandoverRecord record = HandoverRecord.builder()
                .order(order)
                .leg(currentLeg)
                .legNo(legNo)
                .fromCourierId(currentLeg.getCourierId())
                .fromCourierName(currentLeg.getCourierName())
                .toCourierId(courier.getId())
                .toCourierName(nextCourierName)
                .tokenCode(inputCode)
                .handoverPhotoUrl(request.getPhotoUrl())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .meetingPointName(meetingPointName)
                .notes(request.getNotes())
                .handoverTime(now)
                .build();

        handoverRecordRepository.save(record);

        log.info("Handover completed for order {} leg {} -> next courier: {}", order.getOrderCode(), legNo, courier.getId());

        return HandoverConfirmResponse.builder()
                .success(true)
                .message("Bàn giao chặng thành công! Trách nhiệm vận chuyển đã chuyển sang chặng tiếp theo.")
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .completedLegNo(legNo)
                .completedLegStatus(LegStatus.HANDED_OVER)
                .nextLegNo(legNo + 1)
                .nextLegStatus(LegStatus.IN_TRANSIT)
                .nextCourierId(courier.getId())
                .nextCourierName(nextCourierName)
                .nextDestinationAddress(nextLeg.getEndAddress())
                .handoverTime(now)
                .proofPhotoUrl(request.getPhotoUrl())
                .build();
    }

    @Override
    @Transactional
    public HandoverIncidentResponse reportIncident(Long orderId, Integer legNo, HandoverIncidentRequest request, User courier) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        OrderLeg currentLeg = orderLegRepository.findByOrderIdAndLegNo(orderId, legNo)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin chặng"));

        HandoverIncident incident = HandoverIncident.builder()
                .order(order)
                .leg(currentLeg)
                .legNo(legNo)
                .reporterCourierId(courier.getId())
                .reporterName(resolveCourierName(courier))
                .incidentType(request.getIncidentType())
                .description(request.getDescription())
                .photoUrl(request.getPhotoUrl())
                .reportedAt(Instant.now())
                .build();

        handoverIncidentRepository.save(incident);

        // Đánh dấu chặng có sự cố
        currentLeg.setStatus(LegStatus.INCIDENT);
        orderLegRepository.save(currentLeg);

        return HandoverIncidentResponse.builder()
                .id(incident.getId())
                .orderId(order.getId())
                .legNo(legNo)
                .incidentType(incident.getIncidentType())
                .description(incident.getDescription())
                .photoUrl(incident.getPhotoUrl())
                .reportedAt(incident.getReportedAt())
                .message("Đã tiếp nhận báo cáo sự cố bàn giao. Đội ngũ vận hành sẽ hỗ trợ kiểm tra và xử lý.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RelayOrderDetailResponse getRelayOrderDetails(Long orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        List<OrderLeg> legs = orderLegRepository.findByOrderIdOrderByLegNoAsc(orderId);
        List<HandoverRecord> history = handoverRecordRepository.findByOrderIdOrderByLegNoAsc(orderId);

        List<OrderLegResponse> legResponses = legs.stream().map(leg -> {
            boolean isCurrentLeg = Objects.equals(order.getCurrentLegNo(), leg.getLegNo());
            boolean isFinalLeg = order.getTotalLegs() != null && Objects.equals(leg.getLegNo(), order.getTotalLegs());
            boolean hasNextLeg = !isFinalLeg;
            boolean canHandover = isCurrentLeg && hasNextLeg && user != null && Objects.equals(leg.getCourierId(), user.getId())
                    && (leg.getStatus() == LegStatus.IN_TRANSIT || leg.getStatus() == LegStatus.READY_FOR_HANDOVER || leg.getStatus() == LegStatus.ASSIGNED);

            // Chặng tiếp theo có thể nhận bàn giao nếu chặng trước đó đang READY_FOR_HANDOVER hoặc IN_TRANSIT
            boolean canReceiveHandover = false;
            if (leg.getLegNo() > 1) {
                OrderLeg prevLeg = legs.stream()
                        .filter(l -> l.getLegNo() == leg.getLegNo() - 1)
                        .findFirst()
                        .orElse(null);

                if (prevLeg != null && (prevLeg.getStatus() == LegStatus.READY_FOR_HANDOVER || prevLeg.getStatus() == LegStatus.IN_TRANSIT)) {
                    canReceiveHandover = (user != null && (leg.getCourierId() == null || Objects.equals(leg.getCourierId(), user.getId())));
                }
            }

            return OrderLegResponse.builder()
                    .id(leg.getId())
                    .orderId(order.getId())
                    .orderCode(order.getOrderCode())
                    .productName(order.getProductName())
                    .legNo(leg.getLegNo())
                    .courierId(leg.getCourierId())
                    .courierName(leg.getCourierName())
                    .courierPhone(leg.getCourierPhone())
                    .startAddress(leg.getStartAddress())
                    .startLatitude(leg.getStartLatitude())
                    .startLongitude(leg.getStartLongitude())
                    .endAddress(leg.getEndAddress())
                    .endLatitude(leg.getEndLatitude())
                    .endLongitude(leg.getEndLongitude())
                    .meetingPoint(leg.getMeetingPoint() != null ? mapToMeetingPointResponse(leg.getMeetingPoint()) : null)
                    .distanceKm(leg.getDistanceKm())
                    .fee(leg.getFee())
                    .status(leg.getStatus())
                    .startedAt(leg.getStartedAt())
                    .handedOverAt(leg.getHandedOverAt())
                    .completedAt(leg.getCompletedAt())
                    .handoverNote(leg.getHandoverNote())
                    .isCurrentLeg(isCurrentLeg)
                    .isFinalLeg(isFinalLeg)
                    .canHandover(canHandover)
                    .canReceiveHandover(canReceiveHandover)
                    .build();
        }).collect(Collectors.toList());

        List<HandoverRecordDto> historyDtos = history.stream().map(h -> HandoverRecordDto.builder()
                .id(h.getId())
                .legNo(h.getLegNo())
                .fromCourierId(h.getFromCourierId())
                .fromCourierName(h.getFromCourierName())
                .toCourierId(h.getToCourierId())
                .toCourierName(h.getToCourierName())
                .handoverPhotoUrl(h.getHandoverPhotoUrl())
                .meetingPointName(h.getMeetingPointName())
                .notes(h.getNotes())
                .latitude(h.getLatitude())
                .longitude(h.getLongitude())
                .handoverTime(h.getHandoverTime())
                .build()
        ).collect(Collectors.toList());

        return RelayOrderDetailResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .productName(order.getProductName())
                .weightKg(order.getWeightKg())
                .declaredValue(order.getDeclaredValue())
                .totalPrice(order.getPriceAgreed() != null ? order.getPriceAgreed() : order.getPriceSuggested())
                .orderStatus(order.getStatus())
                .isRelay(order.getIsRelay())
                .currentLegNo(order.getCurrentLegNo())
                .totalLegs(order.getTotalLegs() != null ? order.getTotalLegs() : legs.size())
                .pickupAddress(order.getPickupAddress())
                .deliveryAddress(order.getDeliveryAddress())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .legs(legResponses)
                .handoverHistory(historyDtos)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderLegResponse> getCourierRelayLegs(User courier) {
        List<OrderLeg> legs = orderLegRepository.findByCourierIdWithOrder(courier.getId());

        return legs.stream().map(leg -> {
            Order order = leg.getOrder();
            boolean isCurrentLeg = Objects.equals(order.getCurrentLegNo(), leg.getLegNo());
            boolean isFinalLeg = order != null && order.getTotalLegs() != null && Objects.equals(leg.getLegNo(), order.getTotalLegs());
            boolean hasNextLeg = !isFinalLeg;
            boolean canHandover = isCurrentLeg && hasNextLeg
                    && (leg.getStatus() == LegStatus.IN_TRANSIT || leg.getStatus() == LegStatus.READY_FOR_HANDOVER || leg.getStatus() == LegStatus.ASSIGNED);

            return OrderLegResponse.builder()
                    .id(leg.getId())
                    .orderId(order.getId())
                    .orderCode(order.getOrderCode())
                    .productName(order.getProductName())
                    .legNo(leg.getLegNo())
                    .courierId(leg.getCourierId())
                    .courierName(leg.getCourierName())
                    .courierPhone(leg.getCourierPhone())
                    .startAddress(leg.getStartAddress())
                    .startLatitude(leg.getStartLatitude())
                    .startLongitude(leg.getStartLongitude())
                    .endAddress(leg.getEndAddress())
                    .endLatitude(leg.getEndLatitude())
                    .endLongitude(leg.getEndLongitude())
                    .meetingPoint(leg.getMeetingPoint() != null ? mapToMeetingPointResponse(leg.getMeetingPoint()) : null)
                    .distanceKm(leg.getDistanceKm())
                    .fee(leg.getFee())
                    .status(leg.getStatus())
                    .startedAt(leg.getStartedAt())
                    .handedOverAt(leg.getHandedOverAt())
                    .completedAt(leg.getCompletedAt())
                    .handoverNote(leg.getHandoverNote())
                    .isCurrentLeg(isCurrentLeg)
                    .isFinalLeg(isFinalLeg)
                    .canHandover(canHandover)
                    .canReceiveHandover(false)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RelayPricingEstimateResponse estimateRelayPricing(RelayPricingEstimateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu ước tính cước phí tiếp sức không hợp lệ");
        }

        List<RelayPointDto> points = request.getRelayPoints() != null ? request.getRelayPoints() : Collections.emptyList();
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một điểm tiếp sức trung gian để tính cước tiếp sức");
        }

        if (points.size() > 2) {
            throw new IllegalArgumentException("Hệ thống chỉ hỗ trợ tối đa 3 chặng tiếp sức (tối đa 2 điểm hẹn trung gian)");
        }

        int totalLegs = points.size() + 1;
        List<BigDecimal> legDistances = new ArrayList<>();
        List<String[]> legAddresses = new ArrayList<>();

        // Chặng 1: Pickup -> Relay Point 0
        RelayPointDto firstPoint = points.get(0);
        BigDecimal dist1 = calculateDistanceBetween(
                request.getPickupLat(), request.getPickupLng(),
                firstPoint.getLatitude(), firstPoint.getLongitude()
        );
        legDistances.add(dist1);
        legAddresses.add(new String[]{
                "Điểm lấy hàng",
                firstPoint.getAddress() != null && !firstPoint.getAddress().isBlank() ? firstPoint.getAddress() : "Điểm tiếp sức 1"
        });

        // Chặng trung gian (nếu có 2 điểm hẹn): Point 0 -> Point 1
        for (int i = 0; i < points.size() - 1; i++) {
            RelayPointDto pA = points.get(i);
            RelayPointDto pB = points.get(i + 1);
            BigDecimal distMid = calculateDistanceBetween(pA.getLatitude(), pA.getLongitude(), pB.getLatitude(), pB.getLongitude());
            legDistances.add(distMid);
            legAddresses.add(new String[]{
                    pA.getAddress() != null && !pA.getAddress().isBlank() ? pA.getAddress() : "Điểm tiếp sức " + (i + 1),
                    pB.getAddress() != null && !pB.getAddress().isBlank() ? pB.getAddress() : "Điểm tiếp sức " + (i + 2)
            });
        }

        // Chặng cuối: Point (last) -> Delivery
        RelayPointDto lastPoint = points.get(points.size() - 1);
        BigDecimal distLast = calculateDistanceBetween(
                lastPoint.getLatitude(), lastPoint.getLongitude(),
                request.getDeliveryLat(), request.getDeliveryLng()
        );
        legDistances.add(distLast);
        legAddresses.add(new String[]{
                lastPoint.getAddress() != null && !lastPoint.getAddress().isBlank() ? lastPoint.getAddress() : "Điểm tiếp sức cuối",
                "Điểm giao hàng"
        });

        BigDecimal totalDistance = legDistances.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Tính tổng giá cước theo toàn bộ lộ trình
        PricingEstimateRequest pricingReq = PricingEstimateRequest.builder()
                .distanceKm(totalDistance)
                .weightKg(request.getWeightKg() != null ? request.getWeightKg() : BigDecimal.ONE)
                .declaredValue(request.getDeclaredValue() != null ? request.getDeclaredValue() : BigDecimal.ZERO)
                .urgency(request.getUrgency() != null ? request.getUrgency() : DeliveryUrgency.STANDARD)
                .build();

        PricingEstimateResponse totalPricing = pricingCalculatorService.estimatePrice(pricingReq);
        BigDecimal totalEstimatedPrice = totalPricing.getTotalEstimatedPrice();

        // Phân chia cước phí theo tỷ lệ quãng đường từng chặng (BR-R-047)
        double totalDistDouble = Math.max(0.1, totalDistance.doubleValue());
        double totalPriceDouble = totalEstimatedPrice.doubleValue();

        List<LegPricingBreakdownDto> legBreakdowns = new ArrayList<>();
        BigDecimal accumulatedFee = BigDecimal.ZERO;
        int accumulatedPercentage = 0;

        for (int i = 0; i < totalLegs; i++) {
            int legNo = i + 1;
            BigDecimal dist = legDistances.get(i);
            String fromAddr = legAddresses.get(i)[0];
            String toAddr = legAddresses.get(i)[1];

            BigDecimal legFee;
            int percentage;

            if (i < totalLegs - 1) {
                double ratio = dist.doubleValue() / totalDistDouble;
                double rawFee = totalPriceDouble * ratio;
                long roundedFee = Math.round(rawFee / 500.0) * 500;
                if (roundedFee < 5000) roundedFee = 5000;
                legFee = BigDecimal.valueOf(roundedFee);
                accumulatedFee = accumulatedFee.add(legFee);

                percentage = (int) Math.round(ratio * 100.0);
                accumulatedPercentage += percentage;
            } else {
                // Chặng cuối nhận phần còn lại để đảm bảo tổng cước các chặng = tổng cước đơn hàng (BR-R-047)
                legFee = totalEstimatedPrice.subtract(accumulatedFee);
                if (legFee.compareTo(BigDecimal.valueOf(5000)) < 0) {
                    legFee = BigDecimal.valueOf(5000);
                }
                percentage = Math.max(1, 100 - accumulatedPercentage);
            }

            legBreakdowns.add(LegPricingBreakdownDto.builder()
                    .legNo(legNo)
                    .fromAddress(fromAddr)
                    .toAddress(toAddr)
                    .distanceKm(dist)
                    .estimatedFee(legFee)
                    .percentage(percentage)
                    .build());
        }

        return RelayPricingEstimateResponse.builder()
                .totalDistanceKm(totalDistance)
                .totalEstimatedPrice(totalEstimatedPrice)
                .urgency(request.getUrgency() != null ? request.getUrgency() : DeliveryUrgency.STANDARD)
                .legs(legBreakdowns)
                .build();
    }

    private BigDecimal calculateDistanceBetween(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 != null && lng1 != null && lat2 != null && lng2 != null) {
            PricingEstimateRequest req = PricingEstimateRequest.builder()
                    .pickupLat(lat1)
                    .pickupLng(lng1)
                    .deliveryLat(lat2)
                    .deliveryLng(lng2)
                    .build();
            PricingEstimateResponse res = pricingCalculatorService.estimatePrice(req);
            if (res != null && res.getDistanceKm() != null && res.getDistanceKm().compareTo(BigDecimal.ZERO) > 0) {
                return res.getDistanceKm();
            }
        }
        return BigDecimal.valueOf(10.0);
    }

    private List<RelayOrderCreateRequest.LegPlanDto> buildLegsFromRelayPoints(RelayOrderCreateRequest request) {
        RelayPricingEstimateRequest estimateReq = RelayPricingEstimateRequest.builder()
                .pickupLat(request.getPickupLatitude())
                .pickupLng(request.getPickupLongitude())
                .deliveryLat(request.getDeliveryLatitude())
                .deliveryLng(request.getDeliveryLongitude())
                .weightKg(request.getWeightKg())
                .declaredValue(request.getDeclaredValue())
                .urgency(request.getUrgency())
                .relayPoints(request.getRelayPoints())
                .build();

        RelayPricingEstimateResponse estimateRes = estimateRelayPricing(estimateReq);
        if (request.getTotalPrice() == null) {
            request.setTotalPrice(estimateRes.getTotalEstimatedPrice());
        }

        List<RelayOrderCreateRequest.LegPlanDto> legs = new ArrayList<>();
        List<RelayPointDto> points = request.getRelayPoints();

        for (int i = 0; i < estimateRes.getLegs().size(); i++) {
            LegPricingBreakdownDto breakdown = estimateRes.getLegs().get(i);
            int legNo = i + 1;

            String startAddress;
            BigDecimal startLat;
            BigDecimal startLng;
            String endAddress;
            BigDecimal endLat;
            BigDecimal endLng;
            Long meetingPointId = null;

            if (i == 0) {
                startAddress = request.getPickupAddress();
                startLat = request.getPickupLatitude();
                startLng = request.getPickupLongitude();
                RelayPointDto p = points.get(0);
                endAddress = p.getAddress();
                endLat = p.getLatitude();
                endLng = p.getLongitude();
                meetingPointId = p.getMeetingPointId();
            } else if (i == estimateRes.getLegs().size() - 1) {
                RelayPointDto prev = points.get(points.size() - 1);
                startAddress = prev.getAddress();
                startLat = prev.getLatitude();
                startLng = prev.getLongitude();
                endAddress = request.getDeliveryAddress();
                endLat = request.getDeliveryLatitude();
                endLng = request.getDeliveryLongitude();
            } else {
                RelayPointDto prev = points.get(i - 1);
                RelayPointDto next = points.get(i);
                startAddress = prev.getAddress();
                startLat = prev.getLatitude();
                startLng = prev.getLongitude();
                endAddress = next.getAddress();
                endLat = next.getLatitude();
                endLng = next.getLongitude();
                meetingPointId = next.getMeetingPointId();
            }

            legs.add(RelayOrderCreateRequest.LegPlanDto.builder()
                    .legNo(legNo)
                    .meetingPointId(meetingPointId)
                    .startAddress(startAddress)
                    .startLatitude(startLat)
                    .startLongitude(startLng)
                    .endAddress(endAddress)
                    .endLatitude(endLat)
                    .endLongitude(endLng)
                    .distanceKm(breakdown.getDistanceKm())
                    .fee(breakdown.getEstimatedFee())
                    .build());
        }

        return legs;
    }

    @Override
    @Transactional
    public RelayOrderDetailResponse createRelayOrder(RelayOrderCreateRequest request, User sender) {
        if (request.getLegs() == null || request.getLegs().isEmpty()) {
            if (request.getRelayPoints() != null && !request.getRelayPoints().isEmpty()) {
                request.setLegs(buildLegsFromRelayPoints(request));
            } else {
                throw new IllegalArgumentException("Đơn hàng tiếp sức phải có ít nhất 2 chặng hoặc điểm tiếp sức trung gian");
            }
        }

        if (request.getLegs().size() > MAX_RELAY_LEGS) {
            throw new IllegalArgumentException("Đơn hàng tiếp sức chỉ hỗ trợ tối đa " + MAX_RELAY_LEGS + " chặng");
        }

        BigDecimal finalPrice = request.getFinalPrice();
        if (finalPrice == null) {
            finalPrice = request.getLegs().stream()
                    .map(RelayOrderCreateRequest.LegPlanDto::getFee)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        String orderCode = "FPT-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + generateOrderSuffix();

        Order order = Order.builder()
                .orderCode(orderCode)
                .senderId(sender.getId())
                .pickupAddress(request.getPickupAddress())
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryLatitude(request.getDeliveryLatitude())
                .deliveryLongitude(request.getDeliveryLongitude())
                .receiverName(request.getReceiverName())
                .receiverPhone(request.getReceiverPhone())
                .productName(request.getProductName())
                .productDescription(request.getProductDescription())
                .quantity(request.getQuantity() != null ? request.getQuantity() : 1)
                .weightKg(request.getWeightKg())
                .declaredValue(request.getDeclaredValue())
                .priceSuggested(finalPrice)
                .priceAgreed(finalPrice)
                .urgency(request.getUrgency() != null ? request.getUrgency() : DeliveryUrgency.STANDARD)
                .deliveryNote(request.getDeliveryNote())
                .status(OrderStatus.FINDING_SHIPPER)
                .isRelay(true)
                .currentLegNo(1)
                .totalLegs(request.getLegs().size())
                .build();

        if (request.getImageUrls() != null) {
            for (String imgUrl : request.getImageUrls()) {
                if (imgUrl != null && !imgUrl.isBlank()) {
                    order.addImage(OrderImage.builder().imageUrl(imgUrl.trim()).build());
                }
            }
        }

        orderRepository.save(order);

        List<OrderLeg> savedLegs = new ArrayList<>();
        for (int i = 0; i < request.getLegs().size(); i++) {
            RelayOrderCreateRequest.LegPlanDto legPlan = request.getLegs().get(i);
            int legNo = i + 1;

            MeetingPoint meetingPoint = null;
            if (legPlan.getMeetingPointId() != null) {
                meetingPoint = meetingPointRepository.findById(legPlan.getMeetingPointId()).orElse(null);
            }

            Long courierId = legPlan.getAssignedCourierId();
            String courierName = null;
            String courierPhone = null;

            if (courierId != null) {
                User cUser = userRepository.findById(courierId).orElse(null);
                if (cUser != null) {
                    courierName = resolveCourierName(cUser);
                    courierPhone = cUser.getPhone();
                }
            }

            LegStatus legStatus = courierId != null ? LegStatus.ASSIGNED : LegStatus.PENDING;

            OrderLeg leg = OrderLeg.builder()
                    .order(order)
                    .legNo(legNo)
                    .courierId(courierId)
                    .courierName(courierName)
                    .courierPhone(courierPhone)
                    .startAddress(legPlan.getStartAddress())
                    .startLatitude(legPlan.getStartLatitude())
                    .startLongitude(legPlan.getStartLongitude())
                    .endAddress(legPlan.getEndAddress())
                    .endLatitude(legPlan.getEndLatitude())
                    .endLongitude(legPlan.getEndLongitude())
                    .meetingPoint(meetingPoint)
                    .distanceKm(legPlan.getDistanceKm())
                    .fee(legPlan.getFee())
                    .status(legStatus)
                    .startedAt(null)
                    .build();

            savedLegs.add(orderLegRepository.save(leg));
        }

        if (!savedLegs.isEmpty() && savedLegs.get(0).getCourierId() != null) {
            order.setCourierId(savedLegs.get(0).getCourierId());
            orderRepository.save(order);
        }

        return getRelayOrderDetails(order.getId(), sender);
    }



    private OrderLegResponse mapToOrderLegResponse(OrderLeg leg) {
        Order order = leg.getOrder();
        boolean isCurrentLeg = order != null && Objects.equals(order.getCurrentLegNo(), leg.getLegNo());
        boolean isFinalLeg = order != null && order.getTotalLegs() != null && Objects.equals(leg.getLegNo(), order.getTotalLegs());
        boolean hasNextLeg = !isFinalLeg;
        boolean canHandover = isCurrentLeg && hasNextLeg
                && (leg.getStatus() == LegStatus.IN_TRANSIT || leg.getStatus() == LegStatus.READY_FOR_HANDOVER || leg.getStatus() == LegStatus.ASSIGNED);

        return OrderLegResponse.builder()
                .id(leg.getId())
                .orderId(order != null ? order.getId() : null)
                .orderCode(order != null ? order.getOrderCode() : null)
                .productName(order != null ? order.getProductName() : null)
                .legNo(leg.getLegNo())
                .courierId(leg.getCourierId())
                .courierName(leg.getCourierName())
                .courierPhone(leg.getCourierPhone())
                .startAddress(leg.getStartAddress())
                .startLatitude(leg.getStartLatitude())
                .startLongitude(leg.getStartLongitude())
                .endAddress(leg.getEndAddress())
                .endLatitude(leg.getEndLatitude())
                .endLongitude(leg.getEndLongitude())
                .meetingPoint(leg.getMeetingPoint() != null ? mapToMeetingPointResponse(leg.getMeetingPoint()) : null)
                .distanceKm(leg.getDistanceKm())
                .fee(leg.getFee())
                .status(leg.getStatus())
                .startedAt(leg.getStartedAt())
                .handedOverAt(leg.getHandedOverAt())
                .completedAt(leg.getCompletedAt())
                .handoverNote(leg.getHandoverNote())
                .isCurrentLeg(isCurrentLeg)
                    .isFinalLeg(isFinalLeg)
                .canHandover(canHandover)
                .canReceiveHandover(false)
                .build();
    }

    private MeetingPointResponse mapToMeetingPointResponse(MeetingPoint point) {
        return MeetingPointResponse.builder()
                .id(point.getId())
                .name(point.getName())
                .address(point.getAddress())
                .latitude(point.getLatitude())
                .longitude(point.getLongitude())
                .campusArea(point.getCampusArea())
                .description(point.getDescription())
                .hasSecurityGuard(point.getHasSecurityGuard())
                .hasSurveillanceCamera(point.getHasSurveillanceCamera())
                .isActive(point.getIsActive())
                .build();
    }

    private String generateRandomTokenCode() {
        StringBuilder sb = new StringBuilder("HO-");
        for (int i = 0; i < 8; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private String generateOrderSuffix() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private String buildQrData(Order order, OrderLeg leg, String tokenCode, Instant expiresAt) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", "RELAY_HANDOVER");
            map.put("orderId", order.getId());
            map.put("orderCode", order.getOrderCode());
            map.put("legNo", leg.getLegNo());
            map.put("tokenCode", tokenCode);
            map.put("expiresAt", expiresAt.toString());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "RELAY:" + order.getId() + ":" + leg.getLegNo() + ":" + tokenCode;
        }
    }

    private String resolveCourierName(User courier) {
        if (courier == null) return "Người giao";
        return userProfileRepository.findById(courier.getId())
                .map(p -> p.getFullName() != null && !p.getFullName().isBlank() ? p.getFullName() : courier.getEmail())
                .orElse(courier.getEmail());
    }
}

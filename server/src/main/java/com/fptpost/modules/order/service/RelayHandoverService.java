package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.HandoverConfirmRequest;
import com.fptpost.modules.order.dto.response.HandoverQrResponse;
import com.fptpost.modules.order.dto.response.OrderLegResponse;
import com.fptpost.modules.order.dto.response.RelayHandoverResultResponse;
import com.fptpost.modules.order.dto.response.RelayMeetingPointDto;
import com.fptpost.modules.order.dto.response.RelayOrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import com.fptpost.modules.order.model.OrderLeg;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderLegRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Dịch vụ xử lý bàn giao giữa các chặng tiếp sức (phân hệ REL, BR-F-060, BR-F-061, BR-R-048, PRM-097).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelayHandoverService {

    private static final long QR_TTL_SECONDS = 600L; // 10 phút theo PRM-097
    private static final String DEFAULT_QR_SECRET = "fptpost-default-secret-key-32bytes-for-relay-handover";

    private final OrderRepository orderRepository;
    private final OrderLegRepository orderLegRepository;
    private final OrderService orderService;
    private final RelayOrderService relayOrderService;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.fptpost.modules.payment.repository.WalletRepository walletRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.fptpost.modules.payment.service.LedgerService ledgerService;

    @Value("${app.pickup.qr-secret:fptpost-default-secret-key-32bytes-for-relay-handover}")
    private String qrSecret;

    private String getEffectiveSecret() {
        if (qrSecret != null && !qrSecret.trim().isEmpty()) {
            return qrSecret.trim();
        }
        return DEFAULT_QR_SECRET;
    }

    /**
     * Sinh mã QR và OTP bàn giao cho chặng legNo (Người giao chặng hiện tại hiển thị để người kế tiếp quét).
     */
    @Transactional(readOnly = true)
    public HandoverQrResponse getHandoverQr(Long orderId, Integer legNo, Long currentCourierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        List<OrderLeg> legs = orderLegRepository.findByOrderIdOrderByLegNoAsc(orderId);
        if (legs.isEmpty()) {
            throw new AppException(OrderErrorCode.LEG_NOT_FOUND, "Đơn hàng không có các chặng tiếp sức.");
        }

        OrderLeg currentLeg = legs.stream()
                .filter(l -> Objects.equals(l.getLegNo(), legNo))
                .findFirst()
                .orElseThrow(() -> new AppException(OrderErrorCode.LEG_NOT_FOUND));

        if (legNo >= legs.size()) {
            throw new AppException(OrderErrorCode.ORDER_INVALID, "Chặng hiện tại là chặng cuối cùng, không cần bàn giao tiếp sức.");
        }

        OrderLeg nextLeg = legs.stream()
                .filter(l -> Objects.equals(l.getLegNo(), legNo + 1))
                .findFirst()
                .orElseThrow(() -> new AppException(OrderErrorCode.LEG_NOT_FOUND, "Không tìm thấy chặng tiếp theo để bàn giao."));

        if (currentLeg.getCourierId() == null) {
            throw new AppException(OrderErrorCode.HANDOVER_ACCESS_DENIED, "Chặng " + legNo + " hiện chưa có tài xế tiếp nhận. Hãy bấm 'Nhận phụ trách chặng' để bắt đầu.");
        }

        boolean isCurrentCourier = Objects.equals(currentLeg.getCourierId(), currentCourierId);
        boolean isAdmin = false;
        if (currentCourierId != null) {
            User caller = userRepository.findById(currentCourierId).orElse(null);
            if (caller != null && caller.getRoles() != null && caller.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()))) {
                isAdmin = true;
            }
        }

        if (!isCurrentCourier && !isAdmin) {
            throw new AppException(OrderErrorCode.HANDOVER_ACCESS_DENIED, "Bạn không phải tài xế phụ trách chặng này. Tài xế phụ trách hiện tại là người có quyền xuất trình mã QR.");
        }

        if (currentLeg.getStatus() == OrderStatus.COMPLETED) {
            throw new AppException(OrderErrorCode.HANDOVER_ALREADY_COMPLETED);
        }

        // Tạo token có chữ ký và thời hạn 10 phút (PRM-097)
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(QR_TTL_SECONDS);
        String salt = UUID.randomUUID().toString().substring(0, 8);

        String payload = String.format("HANDOVER:%d:%s:LEG%d->LEG%d:%d:%d:%s",
                order.getId(),
                order.getOrderCode(),
                currentLeg.getLegNo(),
                nextLeg.getLegNo(),
                currentCourierId,
                expiresAt.getEpochSecond(),
                salt
        );

        String signature = signPayload(payload);
        String qrToken = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (payload + "::" + signature).getBytes(StandardCharsets.UTF_8)
        );

        // Sinh mã OTP 6 số phụ trợ để nhập tay nếu máy ảnh không quét được
        String otpCode = generateOtpCode(order.getId(), currentLeg.getLegNo(), expiresAt.getEpochSecond());

        String meetingPoint = currentLeg.getDeliveryAddress();
        String handoverUrl = String.format("/courier/orders/%d/legs/%d/handover?token=%s&otp=%s",
                order.getId(), currentLeg.getLegNo(), qrToken, otpCode);

        return HandoverQrResponse.builder()
                .qrToken(qrToken)
                .otpCode(otpCode)
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .legNo(currentLeg.getLegNo())
                .nextLegNo(nextLeg.getLegNo())
                .fromCourierId(currentCourierId)
                .toCourierId(nextLeg.getCourierId())
                .meetingPoint(meetingPoint)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .remainingSeconds(Duration.between(now, expiresAt).getSeconds())
                .handoverUrl(handoverUrl)
                .build();
    }

    /**
     * Xác nhận bàn giao chặng giữa hai tài xế bằng quét QR và ảnh chụp bằng chứng (API-083, BR-F-060, BR-R-048).
     */
    @Transactional
    public RelayHandoverResultResponse confirmHandover(
            Long orderId,
            Integer legNo,
            Long receivingCourierId,
            HandoverConfirmRequest request,
            MultipartFile photo) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        List<OrderLeg> legs = orderLegRepository.findByOrderIdOrderByLegNoAsc(orderId);
        if (legs.isEmpty()) {
            throw new AppException(OrderErrorCode.LEG_NOT_FOUND, "Đơn hàng không có các chặng tiếp sức.");
        }

        OrderLeg currentLeg = legs.stream()
                .filter(l -> Objects.equals(l.getLegNo(), legNo))
                .findFirst()
                .orElseThrow(() -> new AppException(OrderErrorCode.LEG_NOT_FOUND));

        if (legNo >= legs.size()) {
            throw new AppException(OrderErrorCode.ORDER_INVALID, "Chặng này là chặng cuối cùng của đơn tiếp sức.");
        }

        OrderLeg nextLeg = legs.stream()
                .filter(l -> Objects.equals(l.getLegNo(), legNo + 1))
                .findFirst()
                .orElseThrow(() -> new AppException(OrderErrorCode.LEG_NOT_FOUND));

        if (currentLeg.getStatus() == OrderStatus.COMPLETED) {
            throw new AppException(OrderErrorCode.HANDOVER_ALREADY_COMPLETED);
        }

        // Quy tắc BR-R-008: Không tự bàn giao cho chính mình
        if (Objects.equals(currentLeg.getCourierId(), receivingCourierId)) {
            throw new AppException(OrderErrorCode.HANDOVER_SELF_FORBIDDEN, "Tài xế giao hàng chặng trước không thể tự bàn giao cho chính mình.");
        }

        // Nếu chặng tiếp theo đã có tài xế chỉ định, người quét phải là tài xế đó
        if (nextLeg.getCourierId() != null && !Objects.equals(nextLeg.getCourierId(), receivingCourierId)) {
            throw new AppException(OrderErrorCode.HANDOVER_ACCESS_DENIED, "Chặng tiếp theo đã được chỉ định cho tài xế khác.");
        }

        // Kiểm tra mã QR / OTP Token
        validateHandoverToken(request.getQrToken(), order.getId(), currentLeg.getLegNo(), nextLeg.getLegNo());

        // Bắt buộc phải có ảnh kiện hàng lúc bàn giao (BR-F-060, BR-R-048)
        if (photo == null || photo.isEmpty()) {
            throw new AppException(OrderErrorCode.INVALID_IMAGE_FILE, "Bắt buộc phải chụp ảnh kiện hàng tại thời điểm bàn giao làm bằng chứng.");
        }

        // Tải ảnh kiện hàng lên lưu trữ
        List<String> uploadedUrls = orderService.uploadOrderImages(List.of(photo));
        String photoUrl = uploadedUrls.get(0);

        Instant now = Instant.now();
        String meetingPoint = request.getMeetingPoint() != null && !request.getMeetingPoint().isBlank()
                ? request.getMeetingPoint().trim()
                : currentLeg.getDeliveryAddress();

        // 1. Cập nhật hoàn tất chặng trước
        currentLeg.setStatus(OrderStatus.COMPLETED);
        currentLeg.setHandoverPhotoUrl(photoUrl);
        currentLeg.setHandoverNote(request.getNote() != null ? request.getNote().trim() : null);
        currentLeg.setHandoverAt(now);
        currentLeg.setHandoverMeetingPoint(meetingPoint);
        currentLeg.setHandoverLatitude(request.getLatitude());
        currentLeg.setHandoverLongitude(request.getLongitude());

        // 2. Chuyển giao trách nhiệm sang chặng tiếp theo (BR-R-048)
        nextLeg.setCourierId(receivingCourierId);
        nextLeg.setStatus(OrderStatus.IN_DELIVERY);

        // 3. Cập nhật người phụ trách hiện tại của đơn hàng
        order.setCourierId(receivingCourierId);
        order.setStatus(OrderStatus.IN_DELIVERY);
        order.addImage(OrderImage.builder().imageUrl(photoUrl).build());

        orderLegRepository.save(currentLeg);
        orderLegRepository.save(nextLeg);
        orderRepository.save(order);

        // 4. Giải ngân tiền cước chặng cho tài xế chặng trước vào Ví tiền khả dụng (BR-F-060, BR-R-048)
        if (walletRepository != null && currentLeg.getCourierId() != null && currentLeg.getLegFee() != null
                && currentLeg.getLegFee().compareTo(BigDecimal.ZERO) > 0) {
            try {
                Long fromCourierId = currentLeg.getCourierId();
                long legFeeAmount = currentLeg.getLegFee().longValue();
                com.fptpost.modules.payment.model.Wallet courierWallet = walletRepository.findByUserIdForUpdate(fromCourierId)
                        .orElseGet(() -> walletRepository.save(com.fptpost.modules.payment.model.Wallet.builder()
                                .userId(fromCourierId)
                                .available(0L)
                                .escrowHeld(0L)
                                .pendingWithdraw(0L)
                                .build()));
                long currentAvail = courierWallet.getAvailable() != null ? courierWallet.getAvailable() : 0L;
                courierWallet.setAvailable(currentAvail + legFeeAmount);
                walletRepository.save(courierWallet);
                log.info("Credited leg fee to courier wallet: courierId={}, legNo={}, amount={}",
                        fromCourierId, currentLeg.getLegNo(), legFeeAmount);

                if (ledgerService != null) {
                    String escrowRef = "RELAY_LEG_" + currentLeg.getLegNo() + "_ORD_" + order.getId();
                    ledgerService.recordEscrowRelease(order.getId(), order.getSenderId(), fromCourierId, legFeeAmount, escrowRef);
                }
            } catch (Exception ex) {
                log.warn("Failed to credit leg fee to courier wallet: {}", ex.getMessage());
            }
        }

        log.info("Relay handover confirmed successfully: orderId={}, orderCode={}, fromLeg={}, toLeg={}, fromCourier={}, toCourier={}",
                order.getId(), order.getOrderCode(), currentLeg.getLegNo(), nextLeg.getLegNo(),
                currentLeg.getCourierId(), receivingCourierId);

        List<OrderLegResponse> updatedLegResponses = enrichLegResponses(legs);

        return RelayHandoverResultResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .completedLegNo(currentLeg.getLegNo())
                .nextLegNo(nextLeg.getLegNo())
                .previousCourierId(currentLeg.getCourierId())
                .nextCourierId(receivingCourierId)
                .handoverAt(now)
                .handoverPhotoUrl(photoUrl)
                .handoverMeetingPoint(meetingPoint)
                .handoverNote(request.getNote())
                .orderStatus(order.getStatus())
                .message("Bàn giao kiện hàng giữa các chặng thành công. Trách nhiệm đã được chuyển sang tài xế tiếp theo.")
                .legs(updatedLegResponses)
                .build();
    }

    /**
     * Tài xế tiếp nhận một chặng tiếp sức đang tìm người giao (Claim leg).
     */
    @Transactional
    public OrderLegResponse acceptLeg(Long orderId, Integer legNo, Long courierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (Objects.equals(order.getSenderId(), courierId)) {
            throw new AppException(OrderErrorCode.ORDER_SELF_ACCEPT_FORBIDDEN);
        }

        List<OrderLeg> legs = orderLegRepository.findByOrderIdOrderByLegNoAsc(orderId);
        OrderLeg leg = legs.stream()
                .filter(l -> Objects.equals(l.getLegNo(), legNo))
                .findFirst()
                .orElseThrow(() -> new AppException(OrderErrorCode.LEG_NOT_FOUND));

        if (leg.getCourierId() != null && !Objects.equals(leg.getCourierId(), courierId)) {
            throw new AppException(OrderErrorCode.ORDER_ALREADY_ACCEPTED, "Chặng này đã được tài xế khác tiếp nhận.");
        }

        // Chặn cùng 1 tài xế nhận 2 chặng liên tiếp (BR-R-048)
        if (legNo > 1) {
            OrderLeg prevLeg = legs.stream().filter(l -> Objects.equals(l.getLegNo(), legNo - 1)).findFirst().orElse(null);
            if (prevLeg != null && Objects.equals(prevLeg.getCourierId(), courierId)) {
                throw new AppException(OrderErrorCode.HANDOVER_SELF_FORBIDDEN, "Một tài xế không nên nhận 2 chặng liên tiếp trong đơn tiếp sức.");
            }
        }

        leg.setCourierId(courierId);
        if (leg.getStatus() == OrderStatus.FINDING_SHIPPER) {
            leg.setStatus(OrderStatus.SHIPPER_ASSIGNED);
        }

        // Nếu nhận chặng 1, cập nhật tài xế hiện tại của đơn hàng
        if (legNo == 1) {
            order.setCourierId(courierId);
            if (order.getStatus() == OrderStatus.FINDING_SHIPPER || order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.SHIPPER_ASSIGNED);
            }
            orderRepository.save(order);
        }

        OrderLeg saved = orderLegRepository.save(leg);
        return enrichLegResponse(saved);
    }

    /**
     * Lấy danh sách các đơn hàng tiếp sức phục vụ Sàn đơn và Quản lý chặng tiếp sức (BR-F-059).
     * Filter: 'available' (chặng đang tìm tài xế), 'my' (chặng tôi phụ trách), 'all' (tất cả).
     */
    public List<RelayOrderResponse> getRelayOrders(String filter, Long currentCourierId) {
        List<Order> orders = orderRepository.findAllRelayOrders();
        List<RelayOrderResponse> results = new ArrayList<>();

        for (Order o : orders) {
            List<OrderLeg> rawLegs = orderLegRepository.findByOrderIdOrderByLegNoAsc(o.getId());
            if (rawLegs.isEmpty()) continue;

            boolean match = true;
            if ("available".equalsIgnoreCase(filter)) {
                // Có ít nhất 1 chặng chưa có người nhận hoặc đang tìm shipper
                match = rawLegs.stream().anyMatch(l -> l.getCourierId() == null || l.getStatus() == OrderStatus.FINDING_SHIPPER);
            } else if ("my".equalsIgnoreCase(filter)) {
                match = currentCourierId != null && rawLegs.stream().anyMatch(l -> Objects.equals(l.getCourierId(), currentCourierId));
            }

            if (match) {
                RelayOrderResponse dto = new RelayOrderResponse();
                dto.setId(o.getId());
                dto.setOrderCode(o.getOrderCode());
                dto.setSenderId(o.getSenderId());
                dto.setCourierId(o.getCourierId());
                dto.setPickupAddress(o.getPickupAddress());
                dto.setDeliveryAddress(o.getDeliveryAddress());
                dto.setReceiverName(o.getReceiverName());
                dto.setReceiverPhone(o.getReceiverPhone());
                dto.setProductName(o.getProductName());
                dto.setProductDescription(o.getProductDescription());
                dto.setQuantity(o.getQuantity());
                dto.setWeightKg(o.getWeightKg());
                dto.setDeclaredValue(o.getDeclaredValue());
                dto.setPriceSuggested(o.getPriceSuggested());
                dto.setPriceAgreed(o.getPriceAgreed());
                dto.setDistanceKm(o.getDistanceKm());
                dto.setUrgency(o.getUrgency());
                dto.setDeliveryNote(o.getDeliveryNote());
                dto.setStatus(o.getStatus());
                dto.setIsRelay(true);
                dto.setCreatedAt(o.getCreatedAt());
                dto.setUpdatedAt(o.getUpdatedAt());
                dto.setLegs(enrichLegResponses(rawLegs));
                results.add(dto);
            }
        }
        return results;
    }

    /**
     * Danh sách điểm hẹn bàn giao an toàn công cộng (BR-F-061: cổng trường, thư viện, quầy dịch vụ...).
     */
    public List<RelayMeetingPointDto> getSafeMeetingPoints() {
        return List.of(
                RelayMeetingPointDto.builder()
                        .id("MP-FPT-01")
                        .name("Cổng chính Đại học FPT")
                        .address("Đường D1, Khu Công nghệ Cao, Long Thạnh Mỹ, TP. Thủ Đức, TP.HCM")
                        .latitude(BigDecimal.valueOf(10.8411276))
                        .longitude(BigDecimal.valueOf(106.8098830))
                        .campus("Campus FPT TP.HCM")
                        .description("Khu vực cổng bảo vệ chính, có mái che và ánh sáng tốt, điểm hẹn xe máy thuận tiện.")
                        .securityFeatures("Bảo vệ 24/7, camera an ninh giám sát liên tục, bãi đỗ xe tạm thời")
                        .build(),
                RelayMeetingPointDto.builder()
                        .id("MP-FPT-02")
                        .name("Thư viện FPT - Tòa nhà Alpha")
                        .address("Tòa nhà Alpha (Tầng 1 sảnh chính), Đại học FPT, TP. Thủ Đức, TP.HCM")
                        .latitude(BigDecimal.valueOf(10.8415500))
                        .longitude(BigDecimal.valueOf(106.8101500))
                        .campus("Campus FPT TP.HCM")
                        .description("Sảnh lễ tân trước lối vào Thư viện trường, có chỗ ngồi chờ và điều hòa mát mẻ.")
                        .securityFeatures("Camera an ninh, nhân viên trực quầy thông tin, không gian công cộng an toàn")
                        .build(),
                RelayMeetingPointDto.builder()
                        .id("MP-FPT-03")
                        .name("Quầy Dịch vụ Sinh viên - Tòa Beta")
                        .address("Tòa nhà Beta, Đại học FPT, TP. Thủ Đức, TP.HCM")
                        .latitude(BigDecimal.valueOf(10.8409000))
                        .longitude(BigDecimal.valueOf(106.8092000))
                        .campus("Campus FPT TP.HCM")
                        .description("Khu vực quầy tiếp nhận và dịch vụ sinh viên một cửa.")
                        .securityFeatures("Camera giám sát quầy, nhân viên hỗ trợ trực giờ hành chính, điểm gặp văn minh")
                        .build(),
                RelayMeetingPointDto.builder()
                        .id("MP-FPT-04")
                        .name("Sảnh Ký túc xá FPT (Dom A & B)")
                        .address("Khu ký túc xá sinh viên, Đại học FPT, TP. Thủ Đức, TP.HCM")
                        .latitude(BigDecimal.valueOf(10.8423000))
                        .longitude(BigDecimal.valueOf(106.8115000))
                        .campus("Campus FPT TP.HCM")
                        .description("Khu vực sảnh chung trước cửa nhà giữ xe Ký túc xá.")
                        .securityFeatures("Chốt kiểm soát bảo vệ KTX, camera an ninh, đèn chiếu sáng ban đêm")
                        .build(),
                RelayMeetingPointDto.builder()
                        .id("MP-FPT-05")
                        .name("Cửa hàng tiện lợi 7-Eleven / Circle K Khu Công nghệ cao")
                        .address("Khu dịch vụ thương mại Lô E2a-7, Khu Công nghệ Cao, TP. Thủ Đức")
                        .latitude(BigDecimal.valueOf(10.8430000))
                        .longitude(BigDecimal.valueOf(106.8080000))
                        .campus("Khu Công nghệ Cao")
                        .description("Cửa hàng tiện lợi mở cửa 24/24, có chỗ ngồi và bãi xe rộng rãi.")
                        .securityFeatures("Mở cửa 24/7, camera cửa hàng, khu vực đông người qua lại an toàn")
                        .build()
        );
    }

    /**
     * Xác thực QR Token hoặc mã bàn giao dự phòng.
     */
    private void validateHandoverToken(String tokenStr, Long expectedOrderId, Integer expectedCurrentLeg, Integer expectedNextLeg) {
        if (tokenStr == null || tokenStr.trim().isEmpty()) {
            throw new AppException(OrderErrorCode.HANDOVER_INVALID_QR, "Mã bàn giao không được để trống.");
        }

        String rawToken = tokenStr.trim();

        // 1. Kiểm tra trường hợp nhập mã OTP 6 số
        if (rawToken.matches("^\\d{6}$")) {
            // Xác thực xem có khớp OTP của 2 chu kỳ gần nhất không (hiện tại và trước đó)
            long nowSec = Instant.now().getEpochSecond();
            boolean matched = false;
            for (int offset = -600; offset <= 600; offset += 300) {
                long targetSec = ((nowSec + offset) / 600) * 600 + 600;
                String expectedOtp = generateOtpCode(expectedOrderId, expectedCurrentLeg, targetSec);
                if (MessageDigest.isEqual(expectedOtp.getBytes(StandardCharsets.UTF_8), rawToken.getBytes(StandardCharsets.UTF_8))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new AppException(OrderErrorCode.HANDOVER_INVALID_QR, "Mã số bàn giao 6 chữ số không chính xác.");
            }
            return;
        }

        // 2. Kiểm tra token QR có chữ ký HMAC
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(rawToken);
            String content = new String(decoded, StandardCharsets.UTF_8);

            String[] parts = content.split("::");
            if (parts.length != 2) {
                throw new AppException(OrderErrorCode.HANDOVER_INVALID_QR, "Định dạng mã QR bàn giao không hợp lệ.");
            }

            String payload = parts[0];
            String signature = parts[1];

            // Verify signature
            String expectedSignature = signPayload(payload);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new AppException(OrderErrorCode.HANDOVER_INVALID_QR, "Chữ ký mã QR bàn giao không chính xác hoặc đã bị chỉnh sửa.");
            }

            // Parse payload: "HANDOVER:orderId:orderCode:LEG1->LEG2:fromCourier:expiresAtSec:salt"
            String[] segments = payload.split(":");
            if (segments.length < 6 || !segments[0].equals("HANDOVER")) {
                throw new AppException(OrderErrorCode.HANDOVER_INVALID_QR, "Nội dung mã QR bàn giao không hợp lệ.");
            }

            Long tokenOrderId = Long.parseLong(segments[1]);
            String legsStr = segments[3]; // "LEG1->LEG2"
            long expiresAtSec = Long.parseLong(segments[5]);

            if (!Objects.equals(tokenOrderId, expectedOrderId)) {
                throw new AppException(OrderErrorCode.HANDOVER_INVALID_QR, "Mã bàn giao này thuộc đơn hàng khác.");
            }

            String expectedLegSegment = "LEG" + expectedCurrentLeg + "->LEG" + expectedNextLeg;
            if (!expectedLegSegment.equals(legsStr)) {
                throw new AppException(OrderErrorCode.HANDOVER_INVALID_QR, "Mã bàn giao không khớp với chặng cần tiếp nhận.");
            }

            // Kiểm tra thời hạn 10 phút (PRM-097)
            if (Instant.now().getEpochSecond() > expiresAtSec) {
                throw new AppException(OrderErrorCode.HANDOVER_EXPIRED_QR, "Mã bàn giao đã hết hạn (sau 10 phút). Vui lòng yêu cầu tài xế trước bấm làm mới mã.");
            }

        } catch (IllegalArgumentException ex) {
            throw new AppException(OrderErrorCode.HANDOVER_INVALID_QR, "Không thể giải mã mã QR bàn giao.");
        }
    }

    private String signPayload(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(getEffectiveSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            log.error("Failed to generate handover HMAC", e);
            throw new IllegalStateException("Lỗi khi ký xác thực mã bàn giao", e);
        }
    }

    private String generateOtpCode(Long orderId, Integer legNo, long epochSecond) {
        String base = orderId + ":" + legNo + ":" + (epochSecond / 600) + ":" + getEffectiveSecret();
        int hash = Math.abs(base.hashCode());
        return String.format("%06d", hash % 1000000);
    }

    public List<OrderLegResponse> enrichLegResponses(List<OrderLeg> legs) {
        return legs.stream().map(this::enrichLegResponse).toList();
    }

    public OrderLegResponse enrichLegResponse(OrderLeg leg) {
        OrderLegResponse res = relayOrderService.toLegResponse(leg);
        if (leg.getCourierId() != null) {
            try {
                User user = userRepository.findById(leg.getCourierId()).orElse(null);
                if (user != null) {
                    if (user.getPublicId() != null) {
                        res.setCourierPublicId(user.getPublicId().toString());
                    }
                    res.setCourierPhone(user.getPhone());
                    res.setCourierEmail(user.getEmail());
                    UserProfile profile = userProfileRepository.findById(leg.getCourierId()).orElse(null);
                    if (profile != null) {
                        res.setCourierName(profile.getFullName());
                    } else {
                        res.setCourierName(user.getEmail());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return res;
    }
}

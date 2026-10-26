package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.OfferCounterRequest;
import com.fptpost.modules.order.dto.request.OfferCreateRequest;
import com.fptpost.modules.order.dto.response.OfferAcceptResponse;
import com.fptpost.modules.order.dto.response.OfferResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Offer;
import com.fptpost.modules.order.model.OfferStatus;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OfferRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service xử lý phân hệ Sàn đơn & Ghép đơn (MKT):
 * - UC-18: Gửi chào giá (COURIER)
 * - UC-19: Xử lý chào giá (Chấp nhận, từ chối, phản giá) (SENDER / COURIER)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferService implements IOfferService {

    private final OrderRepository orderRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final CourierProfileRepository courierProfileRepository;

    private static final BigDecimal MIN_PRICE_FACTOR = new BigDecimal("0.70"); // PRM-039 (70%)
    private static final BigDecimal MAX_PRICE_FACTOR = new BigDecimal("1.50"); // PRM-040 (150%)
    private static final int MAX_COUNTER_ROUNDS = 3;                           // PRM-054 (3 vòng)
    private static final long OFFER_TTL_MINUTES = 120;                         // PRM-041 (120 phút)
    private static final long HOLD_PAYMENT_MINUTES = 15;                       // PRM-023 (15 phút)

    // ==========================================
    // UC-18: Gửi chào giá (API-044)
    // ==========================================
    @Override
    @Transactional
    public OfferResponse createOffer(String orderIdentifier, OfferCreateRequest request, User courierUser) {
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        // BR-R-009: Chỉ tài khoản có vai trò Người giao (COURIER) mới được gửi chào giá
        boolean hasCourierRole = courierUser.getRoles() != null && courierUser.getRoles().stream()
                .anyMatch(r -> "COURIER".equalsIgnoreCase(r.getCode()));
        if (!hasCourierRole) {
            throw new AppException(OrderErrorCode.COURIER_ROLE_REQUIRED);
        }

        if (courierUser.getStatus() != UserStatus.ACTIVE || courierUser.isDeleted()) {
            throw new AppException(OrderErrorCode.COURIER_ACCOUNT_RESTRICTED);
        }

        Order order = findMarketOrderByIdentifier(orderIdentifier);

        // Đơn phải đang ở trạng thái tìm người giao và chưa được gán
        if (order.getStatus() != OrderStatus.FINDING_SHIPPER || order.getCourierId() != null) {
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        // BR-R-008: Không được tự chào giá đơn do chính mình tạo
        if (order.getSenderId() != null && order.getSenderId().equals(courierUser.getId())) {
            throw new AppException(OrderErrorCode.OFFER_SELF_FORBIDDEN);
        }

        // Kiểm tra hồ sơ & hạn mức của bưu tá
        CourierProfile profile = getOrInitCourierProfile(courierUser);
        if (profile.getRestrictedUntil() != null && profile.getRestrictedUntil().isAfter(Instant.now())) {
            throw new AppException(OrderErrorCode.COURIER_ACCOUNT_RESTRICTED);
        }

        CourierTier tier = profile.getTier() != null ? profile.getTier() : CourierTier.SILVER;

        // BR-R-024: Đơn giá trị khai báo > 1.000.000đ yêu cầu từ hạng Bạc trở lên
        BigDecimal highValueThreshold = new BigDecimal("1000000");
        if (order.getDeclaredValue() != null && order.getDeclaredValue().compareTo(highValueThreshold) > 0) {
            if (!tier.isCanAcceptHighValue()) {
                throw new AppException(OrderErrorCode.COURIER_HIGH_VALUE_RESTRICTED);
            }
        }

        // BR-R-028: Hạn mức nhận đơn theo hạng
        checkCourierLimits(courierUser.getId(), tier, order.getDeclaredValue());

        // BR-R-029: Kiểm tra biên giá chào [70% - 150%] của giá gợi ý
        validatePriceBounds(order, request.getPrice());

        // BR-R-030: Một chào giá hiệu lực mỗi người giao - Chào giá cũ chuyển sang WITHDRAWN
        List<Offer> activeOffers = offerRepository.findByOrderIdAndCourierIdAndStatusIn(
                order.getId(),
                courierUser.getId(),
                List.of(OfferStatus.PENDING, OfferStatus.COUNTERED)
        );
        for (Offer oldOffer : activeOffers) {
            oldOffer.setStatus(OfferStatus.WITHDRAWN);
            oldOffer.setUpdatedAt(Instant.now());
            offerRepository.save(oldOffer);
        }

        // Tạo chào giá mới
        Instant now = Instant.now();
        Offer newOffer = Offer.builder()
                .order(order)
                .courier(courierUser)
                .price(request.getPrice())
                .message(request.getMessage() != null ? request.getMessage().trim() : null)
                .etaPickup(request.getEtaPickup())
                .status(OfferStatus.PENDING)
                .roundNo(1)
                .createdBySender(false)
                .expiresAt(now.plusSeconds(OFFER_TTL_MINUTES * 60))
                .createdAt(now)
                .updatedAt(now)
                .build();

        Offer saved = offerRepository.save(newOffer);
        return mapToOfferResponse(saved);
    }

    // ==========================================
    // UC-18: Rút chào giá (API-045)
    // ==========================================
    @Override
    @Transactional
    public OfferResponse withdrawOffer(Long offerId, User courierUser) {
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new AppException(OrderErrorCode.OFFER_NOT_FOUND));

        if (!offer.getCourier().getId().equals(courierUser.getId())) {
            throw new AppException(OrderErrorCode.OFFER_FORBIDDEN);
        }

        if (offer.getStatus() != OfferStatus.PENDING && offer.getStatus() != OfferStatus.COUNTERED) {
            throw new AppException(OrderErrorCode.OFFER_NOT_ACTIVE);
        }

        offer.setStatus(OfferStatus.WITHDRAWN);
        offer.setUpdatedAt(Instant.now());
        Offer saved = offerRepository.save(offer);

        return mapToOfferResponse(saved);
    }

    // ==========================================
    // UC-18: Chào giá của tôi (API-046)
    // ==========================================
    @Override
    public List<OfferResponse> getMyOffers(User courierUser) {
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        return offerRepository.findByCourierIdOrderByCreatedAtDesc(courierUser.getId()).stream()
                .map(this::mapToOfferResponse)
                .toList();
    }

    // ==========================================
    // UC-19: Danh sách chào giá của đơn (API-047)
    // ==========================================
    @Override
    public List<OfferResponse> getOffersForOrder(String orderIdentifier, User currentUser) {
        Order order = findMarketOrderByIdentifier(orderIdentifier);

        // Người gửi xem được tất cả chào giá của đơn hàng;
        // Bưu tá chỉ xem được chào giá của chính mình trên đơn hàng
        boolean isSender = currentUser != null && order.getSenderId().equals(currentUser.getId());
        if (isSender) {
            return offerRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                    .map(this::mapToOfferResponse)
                    .toList();
        } else if (currentUser != null) {
            return offerRepository.findByOrderIdAndCourierId(order.getId(), currentUser.getId()).stream()
                    .map(this::mapToOfferResponse)
                    .toList();
        } else {
            // Khách chưa đăng nhập không được xem danh sách chào giá chi tiết
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    // ==========================================
    // UC-19: Chấp nhận chào giá (API-048)
    // ==========================================
    @Override
    @Transactional
    public OfferAcceptResponse acceptOffer(Long offerId, User senderUser) {
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new AppException(OrderErrorCode.OFFER_NOT_FOUND));

        Order order = findMarketOrderForUpdateByIdentifier(String.valueOf(offer.getOrder().getId()));

        // Kiểm tra quyền: Người gọi phải là Người gửi đã tạo đơn
        if (!order.getSenderId().equals(senderUser.getId())) {
            throw new AppException(OrderErrorCode.OFFER_FORBIDDEN);
        }

        // Đơn phải còn đang ở trạng thái tìm shipper và chưa ai nhận
        if (order.getStatus() != OrderStatus.FINDING_SHIPPER || order.getCourierId() != null) {
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        // Chào giá phải đang ở trạng thái PENDING (hoặc COUNTERED do bưu tá phản giá)
        if (offer.getStatus() != OfferStatus.PENDING && offer.getStatus() != OfferStatus.COUNTERED) {
            throw new AppException(OrderErrorCode.OFFER_NOT_ACTIVE);
        }

        // Kiểm tra thời hạn hiệu lực chào giá
        if (offer.getExpiresAt() != null && offer.getExpiresAt().isBefore(Instant.now())) {
            offer.setStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);
            throw new AppException(OrderErrorCode.OFFER_NOT_ACTIVE);
        }

        User courier = offer.getCourier();
        CourierProfile profile = getOrInitCourierProfile(courier);
        CourierTier tier = profile.getTier() != null ? profile.getTier() : CourierTier.SILVER;

        // BR-R-028: Kiểm tra lại hạn mức bưu tá tại thời điểm chốt
        checkCourierLimits(courier.getId(), tier, order.getDeclaredValue());

        // BR-F-032 & BR-R-031: Chốt người giao, chuyển trạng thái đơn sang Chờ ký quỹ (AWAITING_PAYMENT)
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setUpdatedAt(Instant.now());
        offerRepository.save(offer);

        order.setCourierId(courier.getId());
        order.setPriceAgreed(offer.getPrice());
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        // BR-R-055: Khóa hoa hồng nền tảng theo hạng của người giao tại thời điểm chấp nhận
        order.setFeeRateLocked(tier.getCommissionRate());
        order.setUpdatedAt(Instant.now());
        Order savedOrder = orderRepository.save(order);

        // BR-R-031: Tự động từ chối tất cả các chào giá còn lại của đơn hàng
        List<Offer> otherOffers = offerRepository.findByOrderIdAndStatusInOrderByCreatedAtDesc(
                order.getId(),
                List.of(OfferStatus.PENDING, OfferStatus.COUNTERED)
        );
        for (Offer other : otherOffers) {
            if (!other.getId().equals(offer.getId())) {
                other.setStatus(OfferStatus.REJECTED);
                other.setUpdatedAt(Instant.now());
                offerRepository.save(other);
            }
        }

        String courierName = resolveFullName(courier);
        Instant holdExpiresAt = Instant.now().plusSeconds(HOLD_PAYMENT_MINUTES * 60);

        return OfferAcceptResponse.builder()
                .offerId(offer.getId())
                .orderId(savedOrder.getId())
                .orderCode(savedOrder.getOrderCode())
                .courierId(courier.getId())
                .courierName(courierName)
                .priceAgreed(savedOrder.getPriceAgreed())
                .orderStatus(savedOrder.getStatus())
                .holdExpiresAt(holdExpiresAt)
                .message("Đã chấp nhận chào giá! Vui lòng hoàn tất thanh toán ký quỹ trong vòng 15 phút.")
                .build();
    }

    // ==========================================
    // UC-19: Từ chối chào giá (API-049)
    // ==========================================
    @Override
    @Transactional
    public OfferResponse rejectOffer(Long offerId, User senderUser) {
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new AppException(OrderErrorCode.OFFER_NOT_FOUND));

        Order order = offer.getOrder();
        if (!order.getSenderId().equals(senderUser.getId())) {
            throw new AppException(OrderErrorCode.OFFER_FORBIDDEN);
        }

        if (offer.getStatus() != OfferStatus.PENDING && offer.getStatus() != OfferStatus.COUNTERED) {
            throw new AppException(OrderErrorCode.OFFER_NOT_ACTIVE);
        }

        offer.setStatus(OfferStatus.REJECTED);
        offer.setUpdatedAt(Instant.now());
        Offer saved = offerRepository.save(offer);

        return mapToOfferResponse(saved);
    }

    // ==========================================
    // UC-19: Phản giá (API-050)
    // ==========================================
    @Override
    @Transactional
    public OfferResponse counterOffer(Long offerId, OfferCounterRequest request, User currentUser) {
        if (currentUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        Offer currentOffer = offerRepository.findById(offerId)
                .orElseThrow(() -> new AppException(OrderErrorCode.OFFER_NOT_FOUND));

        Order order = currentOffer.getOrder();

        // Kiểm tra quyền: Người gọi phải là Người gửi của đơn HOẶC Bưu tá của chào giá này
        boolean isSender = order.getSenderId().equals(currentUser.getId());
        boolean isCourier = currentOffer.getCourier().getId().equals(currentUser.getId());

        if (!isSender && !isCourier) {
            throw new AppException(OrderErrorCode.OFFER_FORBIDDEN);
        }

        // Không được tự phản giá lại đề xuất của chính mình khi đối phương chưa phản hồi
        if (currentOffer.isCreatedBySender() == isSender) {
            throw new AppException(OrderErrorCode.OFFER_FORBIDDEN);
        }

        // Đơn phải còn đang ở trạng thái tìm bưu tá
        if (order.getStatus() != OrderStatus.FINDING_SHIPPER || order.getCourierId() != null) {
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        if (currentOffer.getStatus() != OfferStatus.PENDING && currentOffer.getStatus() != OfferStatus.COUNTERED) {
            throw new AppException(OrderErrorCode.OFFER_NOT_ACTIVE);
        }

        // BR-R-032: Thương lượng tối đa 3 vòng cho một chào giá
        if (currentOffer.getRoundNo() >= MAX_COUNTER_ROUNDS) {
            throw new AppException(OrderErrorCode.OFFER_EXCEEDED_ROUNDS);
        }

        // BR-R-029: Kiểm tra biên giá phản đề xuất [70% - 150%]
        validatePriceBounds(order, request.getPrice());

        // Đánh dấu offer hiện tại là COUNTERED
        currentOffer.setStatus(OfferStatus.COUNTERED);
        currentOffer.setUpdatedAt(Instant.now());
        offerRepository.save(currentOffer);

        // Sinh chào giá con cho vòng tiếp theo
        Instant now = Instant.now();
        Offer nextOffer = Offer.builder()
                .order(order)
                .courier(currentOffer.getCourier())
                .price(request.getPrice())
                .message(request.getMessage() != null ? request.getMessage().trim() : null)
                .etaPickup(currentOffer.getEtaPickup())
                .status(OfferStatus.PENDING)
                .parentOfferId(currentOffer.getId())
                .roundNo(currentOffer.getRoundNo() + 1)
                .createdBySender(isSender)
                .expiresAt(now.plusSeconds(OFFER_TTL_MINUTES * 60))
                .createdAt(now)
                .updatedAt(now)
                .build();

        Offer saved = offerRepository.save(nextOffer);
        return mapToOfferResponse(saved);
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private void validatePriceBounds(Order order, BigDecimal price) {
        BigDecimal basePrice = order.getPriceSuggested() != null ? order.getPriceSuggested()
                : (order.getDeclaredValue() != null ? order.getDeclaredValue() : BigDecimal.valueOf(30000));

        BigDecimal minPrice = basePrice.multiply(MIN_PRICE_FACTOR);
        BigDecimal maxPrice = basePrice.multiply(MAX_PRICE_FACTOR);

        if (price.compareTo(minPrice) < 0 || price.compareTo(maxPrice) > 0) {
            throw new AppException(OrderErrorCode.OFFER_PRICE_OUT_OF_RANGE);
        }
    }

    private void checkCourierLimits(Long courierId, CourierTier tier, BigDecimal orderValue) {
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.AWAITING_PAYMENT,
                OrderStatus.SHIPPER_ASSIGNED,
                OrderStatus.IN_DELIVERY
        );

        long activeOrdersCount = orderRepository.countByCourierIdAndStatusIn(courierId, activeStatuses);
        if (activeOrdersCount + 1 > tier.getMaxConcurrentOrders()) {
            throw new AppException(OrderErrorCode.COURIER_MAX_ORDERS_EXCEEDED);
        }

        BigDecimal currentTotalValue = orderRepository.sumDeclaredValueByCourierIdAndStatusIn(courierId, activeStatuses);
        if (currentTotalValue == null) {
            currentTotalValue = BigDecimal.ZERO;
        }
        BigDecimal newOrderValue = orderValue != null ? orderValue : BigDecimal.ZERO;
        if (currentTotalValue.add(newOrderValue).compareTo(tier.getMaxTotalDeclaredValue()) > 0) {
            throw new AppException(OrderErrorCode.COURIER_MAX_VALUE_EXCEEDED);
        }
    }

    private CourierProfile getOrInitCourierProfile(User courierUser) {
        CourierProfile profile = null;
        try {
            profile = courierProfileRepository.findByUserId(courierUser.getId()).orElse(null);
        } catch (Exception e) {
            log.warn("Truy vấn hồ sơ bưu tá thất bại: {}", e.getMessage());
        }

        if (profile == null) {
            try {
                CourierProfile newProfile = CourierProfile.builder()
                        .user(courierUser)
                        .tier(CourierTier.SILVER)
                        .trustScore(80)
                        .completedOrders(10)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                profile = courierProfileRepository.save(newProfile);
            } catch (Exception e) {
                log.warn("Không thể lưu profile bưu tá vào DB: {}", e.getMessage());
                profile = CourierProfile.builder()
                        .user(courierUser)
                        .tier(CourierTier.SILVER)
                        .trustScore(80)
                        .completedOrders(10)
                        .build();
            }
        }
        return profile;
    }

    private Order findMarketOrderByIdentifier(String identifier) {
        try {
            Long orderId = Long.parseLong(identifier);
            return orderRepository.findById(orderId)
                    .orElseGet(() -> orderRepository.findByOrderCode(identifier)
                            .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND)));
        } catch (NumberFormatException e) {
            return orderRepository.findByOrderCode(identifier)
                    .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        }
    }

    private Order findMarketOrderForUpdateByIdentifier(String identifier) {
        try {
            Long orderId = Long.parseLong(identifier);
            return orderRepository.findByIdForUpdate(orderId)
                    .orElseGet(() -> orderRepository.findByOrderCodeForUpdate(identifier)
                            .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND)));
        } catch (NumberFormatException e) {
            return orderRepository.findByOrderCodeForUpdate(identifier)
                    .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        }
    }

    private OfferResponse mapToOfferResponse(Offer offer) {
        User courier = offer.getCourier();
        String courierName = resolveFullName(courier);
        String courierPhone = courier != null ? courier.getPhone() : null;

        CourierProfile courierProfile = null;
        try {
            if (courier != null) {
                courierProfile = courierProfileRepository.findByUserId(courier.getId()).orElse(null);
            }
        } catch (Exception ignored) {}

        CourierTier tier = courierProfile != null && courierProfile.getTier() != null
                ? courierProfile.getTier() : CourierTier.SILVER;
        Integer trustScore = courierProfile != null && courierProfile.getTrustScore() != null
                ? courierProfile.getTrustScore() : 80;
        Integer completedOrders = courierProfile != null && courierProfile.getCompletedOrders() != null
                ? courierProfile.getCompletedOrders() : 10;

        Order order = offer.getOrder();
        BigDecimal priceSuggested = order.getPriceSuggested() != null ? order.getPriceSuggested() : BigDecimal.valueOf(30000);
        Double priceDiffPercent = null;
        if (priceSuggested.compareTo(BigDecimal.ZERO) > 0 && offer.getPrice() != null) {
            priceDiffPercent = offer.getPrice().subtract(priceSuggested)
                    .divide(priceSuggested, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        boolean expired = offer.getExpiresAt() != null && offer.getExpiresAt().isBefore(Instant.now());

        return OfferResponse.builder()
                .id(offer.getId())
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .courierId(courier != null ? courier.getId() : null)
                .courierName(courierName)
                .courierAvatarUrl(null)
                .courierPhone(courierPhone)
                .courierTier(tier)
                .courierTierName(tier.getDisplayName())
                .courierTrustScore(trustScore)
                .courierCompletedOrders(completedOrders)
                .fptBadge(courier != null && courier.isFptBadge())
                .price(offer.getPrice())
                .priceSuggested(priceSuggested)
                .priceDifferencePercentage(priceDiffPercent)
                .message(offer.getMessage())
                .etaPickup(offer.getEtaPickup())
                .status(offer.getStatus())
                .parentOfferId(offer.getParentOfferId())
                .roundNo(offer.getRoundNo())
                .createdBySender(offer.isCreatedBySender())
                .expiresAt(offer.getExpiresAt())
                .expired(expired)
                .createdAt(offer.getCreatedAt())
                .updatedAt(offer.getUpdatedAt())
                .build();
    }

    private String resolveFullName(User user) {
        if (user == null) {
            return "Bưu tá FPTPost";
        }
        try {
            Optional<UserProfile> profile = userProfileRepository.findById(user.getId());
            if (profile.isPresent() && profile.get().getFullName() != null && !profile.get().getFullName().isBlank()) {
                return profile.get().getFullName();
            }
        } catch (Exception ignored) {}
        return user.getEmail() != null ? user.getEmail() : "Bưu tá #" + user.getId();
    }
}

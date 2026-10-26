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

    private static final BigDecimal MIN_PRICE_FACTOR = new BigDecimal("0.70"); // 70% giá gợi ý
    private static final BigDecimal MAX_PRICE_FACTOR = new BigDecimal("1.50"); // 150% giá gợi ý
    private static final int MAX_COUNTER_ROUNDS = 3;                           // Tối đa 3 vòng
    private static final long OFFER_TTL_MINUTES = 120;                         // Hiệu lực 120 phút
    private static final long HOLD_PAYMENT_MINUTES = 15;                       // Giữ chỗ ký quỹ 15 phút

    // ==========================================
    // Gửi chào giá (COURIER)
    // ==========================================
    @Override
    @Transactional
    public OfferResponse createOffer(String orderIdentifier, OfferCreateRequest request, User courierUser) {
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        boolean hasCourierRole = courierUser.getRoles() != null && courierUser.getRoles().stream()
                .anyMatch(r -> "COURIER".equalsIgnoreCase(r.getCode()));
        if (!hasCourierRole) {
            throw new AppException(OrderErrorCode.COURIER_ROLE_REQUIRED);
        }

        if (courierUser.getStatus() != UserStatus.ACTIVE || courierUser.isDeleted()) {
            throw new AppException(OrderErrorCode.COURIER_ACCOUNT_RESTRICTED);
        }

        Order order = findMarketOrderByIdentifier(orderIdentifier);

        if (order.getStatus() != OrderStatus.FINDING_SHIPPER || order.getCourierId() != null) {
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        if (order.getSenderId() != null && order.getSenderId().equals(courierUser.getId())) {
            throw new AppException(OrderErrorCode.OFFER_SELF_FORBIDDEN);
        }

        CourierProfile profile = getOrInitCourierProfile(courierUser);
        if (profile.getRestrictedUntil() != null && profile.getRestrictedUntil().isAfter(Instant.now())) {
            throw new AppException(OrderErrorCode.COURIER_ACCOUNT_RESTRICTED);
        }

        CourierTier tier = profile.getTier() != null ? profile.getTier() : CourierTier.SILVER;

        BigDecimal highValueThreshold = new BigDecimal("1000000");
        if (order.getDeclaredValue() != null && order.getDeclaredValue().compareTo(highValueThreshold) > 0) {
            if (!tier.isCanAcceptHighValue()) {
                throw new AppException(OrderErrorCode.COURIER_HIGH_VALUE_RESTRICTED);
            }
        }

        checkCourierLimits(courierUser.getId(), tier, order.getDeclaredValue());
        validatePriceBounds(order, request.getPrice());

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
    // Rút chào giá (COURIER)
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
    // Chào giá của tôi (COURIER)
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
    // Danh sách chào giá của đơn (SENDER / COURIER)
    // ==========================================
    @Override
    public List<OfferResponse> getOffersForOrder(String orderIdentifier, User currentUser) {
        Order order = findMarketOrderByIdentifier(orderIdentifier);

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
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    // ==========================================
    // Chấp nhận chào giá (SENDER)
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

        if (!order.getSenderId().equals(senderUser.getId())) {
            throw new AppException(OrderErrorCode.OFFER_FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.FINDING_SHIPPER || order.getCourierId() != null) {
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        if (offer.getStatus() != OfferStatus.PENDING && offer.getStatus() != OfferStatus.COUNTERED) {
            throw new AppException(OrderErrorCode.OFFER_NOT_ACTIVE);
        }

        if (offer.getExpiresAt() != null && offer.getExpiresAt().isBefore(Instant.now())) {
            offer.setStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);
            throw new AppException(OrderErrorCode.OFFER_NOT_ACTIVE);
        }

        User courier = offer.getCourier();
        CourierProfile profile = getOrInitCourierProfile(courier);
        CourierTier tier = profile.getTier() != null ? profile.getTier() : CourierTier.SILVER;

        checkCourierLimits(courier.getId(), tier, order.getDeclaredValue());

        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setUpdatedAt(Instant.now());
        offerRepository.save(offer);

        order.setCourierId(courier.getId());
        order.setPriceAgreed(offer.getPrice());
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setFeeRateLocked(tier.getCommissionRate());
        order.setUpdatedAt(Instant.now());
        Order savedOrder = orderRepository.save(order);

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
    // Từ chối chào giá (SENDER)
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
    // Phản giá (SENDER / COURIER)
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

        boolean isSender = order.getSenderId().equals(currentUser.getId());
        boolean isCourier = currentOffer.getCourier().getId().equals(currentUser.getId());

        if (!isSender && !isCourier) {
            throw new AppException(OrderErrorCode.OFFER_FORBIDDEN);
        }

        if (currentOffer.isCreatedBySender() == isSender) {
            throw new AppException(OrderErrorCode.OFFER_FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.FINDING_SHIPPER || order.getCourierId() != null) {
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        if (currentOffer.getStatus() != OfferStatus.PENDING && currentOffer.getStatus() != OfferStatus.COUNTERED) {
            throw new AppException(OrderErrorCode.OFFER_NOT_ACTIVE);
        }

        if (currentOffer.getRoundNo() >= MAX_COUNTER_ROUNDS) {
            throw new AppException(OrderErrorCode.OFFER_EXCEEDED_ROUNDS);
        }

        validatePriceBounds(order, request.getPrice());

        currentOffer.setStatus(OfferStatus.COUNTERED);
        currentOffer.setUpdatedAt(Instant.now());
        offerRepository.save(currentOffer);

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
                        .trustScore(BigDecimal.valueOf(80))
                        .completedOrders(10)
                        .commitmentVersion("1.0")
                        .registeredAt(Instant.now())
                        .build();
                profile = courierProfileRepository.save(newProfile);
            } catch (Exception e) {
                profile = CourierProfile.builder()
                        .user(courierUser)
                        .tier(CourierTier.SILVER)
                        .trustScore(BigDecimal.valueOf(80))
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
                ? courierProfile.getTrustScore().intValue() : 80;
        Integer completedOrders = courierProfile != null
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

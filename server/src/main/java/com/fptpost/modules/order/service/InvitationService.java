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
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.dto.request.InvitationCreateRequest;
import com.fptpost.modules.order.dto.response.CourierSummaryResponse;
import com.fptpost.modules.order.dto.response.FavoriteCourierResponse;
import com.fptpost.modules.order.dto.response.InvitationResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.*;
import com.fptpost.modules.order.repository.FavoriteCourierRepository;
import com.fptpost.modules.order.repository.InvitationRepository;
import com.fptpost.modules.order.repository.OfferRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ UC-20: Mời trực tiếp người giao & Quản lý người giao yêu thích.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService implements IInvitationService {

    private final InvitationRepository invitationRepository;
    private final FavoriteCourierRepository favoriteCourierRepository;
    private final OrderRepository orderRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final CourierProfileRepository courierProfileRepository;

    @Autowired(required = false)
    private NotificationPublisher notificationPublisher;

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000000");

    @Override
    @Transactional
    public InvitationResponse inviteCourier(String orderIdentifier, InvitationCreateRequest request, User senderUser) {
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        Order order = findOrderByIdentifier(orderIdentifier);

        // Kiểm tra quyền sở hữu đơn hàng
        if (order.getSenderId() == null || !order.getSenderId().equals(senderUser.getId())) {
            throw new AppException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        // Đơn hàng phải ở trạng thái mở tìm người giao
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.FINDING_SHIPPER) {
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        // Kiểm tra người giao được mời
        User courier = userRepository.findById(request.getCourierId())
                .orElseThrow(() -> new AppException(OrderErrorCode.COURIER_NOT_FOUND));

        if (courier.getStatus() != UserStatus.ACTIVE || courier.isDeleted()) {
            throw new AppException(OrderErrorCode.COURIER_ACCOUNT_RESTRICTED);
        }

        // BR-R-008: Không tự giao dịch (không tự mời chính mình)
        if (courier.getId().equals(senderUser.getId())) {
            throw new AppException(OrderErrorCode.INVITATION_SELF_FORBIDDEN);
        }

        // Kiểm tra vai trò COURIER
        boolean isCourier = courier.getRoles() != null && courier.getRoles().stream()
                .anyMatch(r -> "COURIER".equalsIgnoreCase(r.getCode()));
        if (!isCourier) {
            throw new AppException(OrderErrorCode.COURIER_ROLE_REQUIRED);
        }

        // Kiểm tra hồ sơ bưu tá
        CourierProfile courierProfile = courierProfileRepository.findByUserId(courier.getId())
                .orElseThrow(() -> new AppException(OrderErrorCode.COURIER_ROLE_REQUIRED));

        // Kiểm tra hạn chế tài khoản bưu tá
        if (courierProfile.getRestrictedUntil() != null && courierProfile.getRestrictedUntil().isAfter(Instant.now())) {
            throw new AppException(OrderErrorCode.COURIER_ACCOUNT_RESTRICTED);
        }

        // BR-R-024: Đơn hàng giá trị cao (> 1.000.000 VNĐ) chỉ tài xế hạng Bạc trở lên mới được nhận
        if (order.getDeclaredValue() != null && order.getDeclaredValue().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            if (courierProfile.getTier() == null || !courierProfile.getTier().isCanAcceptHighValue()) {
                throw new AppException(OrderErrorCode.COURIER_HIGH_VALUE_RESTRICTED);
            }
        }

        // Kiểm tra xem đã có lời mời đang chờ phản hồi cho người giao này chưa
        if (invitationRepository.existsByOrderIdAndCourierIdAndStatus(order.getId(), courier.getId(), InvitationStatus.PENDING)) {
            throw new AppException(OrderErrorCode.INVITATION_ALREADY_EXISTS);
        }

        int expiryHours = (request.getExpiryHours() != null && request.getExpiryHours() > 0 && request.getExpiryHours() <= 72)
                ? request.getExpiryHours() : 24;
        Instant expiresAt = Instant.now().plusSeconds((long) expiryHours * 3600);

        Invitation invitation = Invitation.builder()
                .order(order)
                .sender(senderUser)
                .courier(courier)
                .message(request.getMessage() != null ? request.getMessage().trim() : null)
                .expiresAt(expiresAt)
                .status(InvitationStatus.PENDING)
                .build();

        invitation = invitationRepository.save(invitation);

        // Gửi thông báo cho người giao
        notifyCourierNewInvitation(courier, order, invitation);

        return mapToInvitationResponse(invitation);
    }

    @Override
    @Transactional
    public List<InvitationResponse> getInvitationsForOrder(String orderIdentifier, User senderUser) {
        Order order = findOrderByIdentifier(orderIdentifier);
        if (senderUser != null && order.getSenderId() != null && !order.getSenderId().equals(senderUser.getId())) {
            throw new AppException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        List<Invitation> invitations = invitationRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        checkAndExpireInvitations(invitations);

        return invitations.stream()
                .map(this::mapToInvitationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public InvitationResponse cancelInvitation(Long invitationId, User senderUser) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(OrderErrorCode.INVITATION_NOT_FOUND));

        if (!invitation.getSender().getId().equals(senderUser.getId())) {
            throw new AppException(OrderErrorCode.INVITATION_ACCESS_DENIED);
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(OrderErrorCode.INVITATION_NOT_PENDING);
        }

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitation = invitationRepository.save(invitation);
        return mapToInvitationResponse(invitation);
    }

    @Override
    @Transactional
    public Page<InvitationResponse> getCourierInvitations(User courierUser, String statusStr, Pageable pageable) {
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        Page<Invitation> page;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                InvitationStatus status = InvitationStatus.valueOf(statusStr.toUpperCase());
                page = invitationRepository.findByCourierIdAndStatusOrderByCreatedAtDesc(courierUser.getId(), status, pageable);
            } catch (IllegalArgumentException e) {
                page = invitationRepository.findByCourierIdOrderByCreatedAtDesc(courierUser.getId(), pageable);
            }
        } else {
            page = invitationRepository.findByCourierIdOrderByCreatedAtDesc(courierUser.getId(), pageable);
        }

        checkAndExpireInvitations(page.getContent());

        return page.map(this::mapToInvitationResponse);
    }

    @Override
    @Transactional
    public InvitationResponse acceptInvitation(Long invitationId, User courierUser) {
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(OrderErrorCode.INVITATION_NOT_FOUND));

        if (!invitation.getCourier().getId().equals(courierUser.getId())) {
            throw new AppException(OrderErrorCode.INVITATION_ACCESS_DENIED);
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(OrderErrorCode.INVITATION_NOT_PENDING);
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new AppException(OrderErrorCode.INVITATION_EXPIRED);
        }

        Order order = invitation.getOrder();
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.FINDING_SHIPPER) {
            invitation.setStatus(InvitationStatus.CANCELLED);
            invitationRepository.save(invitation);
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        // Chốt người giao và chuyển trạng thái đơn hàng sang Chờ ký quỹ (AWAITING_PAYMENT)
        order.setCourierId(courierUser.getId());
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        if (order.getPriceAgreed() == null) {
            order.setPriceAgreed(order.getPriceSuggested());
        }
        orderRepository.save(order);

        // Chuyển trạng thái lời mời sang Đã chấp nhận
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation = invitationRepository.save(invitation);

        // Hủy các lời mời đang chờ khác của đơn hàng này
        List<Invitation> otherInvitations = invitationRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        for (Invitation other : otherInvitations) {
            if (!other.getId().equals(invitation.getId()) && other.getStatus() == InvitationStatus.PENDING) {
                other.setStatus(InvitationStatus.CANCELLED);
            }
        }
        invitationRepository.saveAll(otherInvitations);

        // Từ chối tự động các chào giá đang chờ khác (BR-R-031)
        List<Offer> otherOffers = offerRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        for (Offer off : otherOffers) {
            if (off.getStatus() == OfferStatus.PENDING || off.getStatus() == OfferStatus.COUNTERED) {
                off.setStatus(OfferStatus.REJECTED);
            }
        }
        offerRepository.saveAll(otherOffers);

        // Gửi thông báo cho người gửi
        notifySenderInvitationAccepted(invitation.getSender(), order, courierUser);

        return mapToInvitationResponse(invitation);
    }

    @Override
    @Transactional
    public InvitationResponse rejectInvitation(Long invitationId, User courierUser, String reason) {
        if (courierUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(OrderErrorCode.INVITATION_NOT_FOUND));

        if (!invitation.getCourier().getId().equals(courierUser.getId())) {
            throw new AppException(OrderErrorCode.INVITATION_ACCESS_DENIED);
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(OrderErrorCode.INVITATION_NOT_PENDING);
        }

        invitation.setStatus(InvitationStatus.REJECTED);
        invitation = invitationRepository.save(invitation);

        // Gửi thông báo cho người gửi
        notifySenderInvitationRejected(invitation.getSender(), invitation.getOrder(), courierUser);

        return mapToInvitationResponse(invitation);
    }

    @Override
    @Transactional
    public FavoriteCourierResponse toggleFavorite(Long courierId, String note, User senderUser) {
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        User sender = senderUser.getId() != null
                ? userRepository.findById(senderUser.getId()).orElse(senderUser)
                : senderUser;

        User courier = userRepository.findById(courierId)
                .orElseThrow(() -> new AppException(OrderErrorCode.COURIER_NOT_FOUND));

        Optional<FavoriteCourier> existing = favoriteCourierRepository.findBySenderIdAndCourierId(sender.getId(), courierId);
        if (existing.isPresent()) {
            favoriteCourierRepository.delete(existing.get());
            return null; // Đã bỏ yêu thích
        }

        FavoriteCourier favorite = FavoriteCourier.builder()
                .sender(sender)
                .courier(courier)
                .note(note != null ? note.trim() : null)
                .build();
        favorite = favoriteCourierRepository.save(favorite);

        return mapToFavoriteResponse(favorite);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteCourierResponse> getMyFavorites(User senderUser) {
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        List<FavoriteCourier> favorites = favoriteCourierRepository.findBySenderIdOrderByCreatedAtDesc(senderUser.getId());
        return favorites.stream()
                .map(this::mapToFavoriteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeFavorite(Long courierId, User senderUser) {
        if (senderUser == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        favoriteCourierRepository.deleteBySenderIdAndCourierId(senderUser.getId(), courierId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourierSummaryResponse> searchCouriers(String keyword, User senderUser, Pageable pageable) {
        // Lấy danh sách ID bưu tá yêu thích của sender nếu có
        Set<Long> favoriteIds = senderUser != null
                ? new HashSet<>(favoriteCourierRepository.findCourierIdsBySenderId(senderUser.getId()))
                : Collections.emptySet();

        Page<CourierProfile> profilePage;
        if (keyword != null && !keyword.isBlank()) {
            String cleanKw = keyword.trim().toLowerCase();
            // Lấy toàn bộ courier profiles và filter theo tên, sđt, email, khu vực
            List<CourierProfile> allProfiles = courierProfileRepository.findAll();
            List<CourierProfile> filtered = allProfiles.stream()
                    .filter(cp -> {
                        User u = cp.getUser();
                        if (u == null || u.getStatus() != UserStatus.ACTIVE || u.isDeleted()) return false;
                        if (senderUser != null && u.getId().equals(senderUser.getId())) return false; // Không hiện chính mình
                        String fullName = resolveFullName(u).toLowerCase();
                        String phone = u.getPhone() != null ? u.getPhone().toLowerCase() : "";
                        String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";
                        boolean matchesArea = cp.getAreas() != null && cp.getAreas().stream()
                                .anyMatch(a -> a.toLowerCase().contains(cleanKw));
                        return fullName.contains(cleanKw) || phone.contains(cleanKw) || email.contains(cleanKw) || matchesArea;
                    })
                    .collect(Collectors.toList());

            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), filtered.size());
            List<CourierProfile> subList = start > filtered.size() ? Collections.emptyList() : filtered.subList(start, end);
            profilePage = new PageImpl<>(subList, pageable, filtered.size());
        } else {
            profilePage = courierProfileRepository.findAll(pageable);
        }

        return profilePage.map(profile -> {
            User u = profile.getUser();
            String fullName = resolveFullName(u);
            boolean isFav = u != null && favoriteIds.contains(u.getId());
            return CourierSummaryResponse.builder()
                    .courierId(profile.getUserId())
                    .fullName(fullName)
                    .phone(u != null ? u.getPhone() : null)
                    .email(u != null ? u.getEmail() : null)
                    .vehicleType(profile.getVehicleType())
                    .tier(profile.getTier() != null ? profile.getTier() : CourierTier.NEW)
                    .trustScore(profile.getTrustScore() != null ? profile.getTrustScore() : BigDecimal.valueOf(50))
                    .completedOrders(profile.getCompletedOrders())
                    .areas(safeGetAreas(profile))
                    .isFavorite(isFav)
                    .build();
        });
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private Order findOrderByIdentifier(String identifier) {
        try {
            Long id = Long.parseLong(identifier);
            return orderRepository.findById(id)
                    .orElseGet(() -> orderRepository.findByOrderCode(identifier)
                            .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND)));
        } catch (NumberFormatException e) {
            return orderRepository.findByOrderCode(identifier)
                    .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        }
    }

    private void checkAndExpireInvitations(List<Invitation> list) {
        Instant now = Instant.now();
        boolean changed = false;
        for (Invitation inv : list) {
            if (inv.getStatus() == InvitationStatus.PENDING && inv.getExpiresAt().isBefore(now)) {
                inv.setStatus(InvitationStatus.EXPIRED);
                changed = true;
            }
        }
        if (changed) {
            invitationRepository.saveAll(list);
        }
    }

    private InvitationResponse mapToInvitationResponse(Invitation inv) {
        Order order = inv.getOrder();
        User sender = inv.getSender();
        User courier = inv.getCourier();

        CourierProfile cp = null;
        try {
            if (courier != null) {
                cp = courierProfileRepository.findByUserId(courier.getId()).orElse(null);
            }
        } catch (Exception ignored) {}

        return InvitationResponse.builder()
                .id(inv.getId())
                .orderId(order != null ? order.getId() : null)
                .orderCode(order != null ? order.getOrderCode() : null)
                .senderId(sender != null ? sender.getId() : null)
                .senderName(resolveFullName(sender))
                .senderPhone(sender != null ? sender.getPhone() : null)
                .courierId(courier != null ? courier.getId() : null)
                .courierName(resolveFullName(courier))
                .courierPhone(courier != null ? courier.getPhone() : null)
                .courierEmail(courier != null ? courier.getEmail() : null)
                .courierTier(cp != null && cp.getTier() != null ? cp.getTier() : CourierTier.NEW)
                .courierTrustScore(cp != null ? cp.getTrustScore() : BigDecimal.valueOf(50))
                .courierCompletedOrders(cp != null ? cp.getCompletedOrders() : 0)
                .courierVehicleType(cp != null ? cp.getVehicleType() : null)
                .productName(order != null ? order.getProductName() : null)
                .productDescription(order != null ? order.getProductDescription() : null)
                .weightKg(order != null ? order.getWeightKg() : null)
                .pickupAddress(order != null ? order.getPickupAddress() : null)
                .deliveryAddress(order != null ? order.getDeliveryAddress() : null)
                .distanceKm(order != null ? order.getDistanceKm() : null)
                .declaredValue(order != null ? order.getDeclaredValue() : null)
                .priceSuggested(order != null ? order.getPriceSuggested() : null)
                .priceAgreed(order != null ? order.getPriceAgreed() : null)
                .urgency(order != null ? order.getUrgency() : null)
                .orderStatus(order != null ? order.getStatus() : null)
                .message(inv.getMessage())
                .status(inv.getStatus())
                .expiresAt(inv.getExpiresAt())
                .createdAt(inv.getCreatedAt())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }

    private FavoriteCourierResponse mapToFavoriteResponse(FavoriteCourier fav) {
        User courier = fav.getCourier();
        CourierProfile cp = null;
        try {
            if (courier != null) {
                cp = courierProfileRepository.findByUserId(courier.getId()).orElse(null);
            }
        } catch (Exception ignored) {}

        return FavoriteCourierResponse.builder()
                .id(fav.getId())
                .courierId(courier != null ? courier.getId() : null)
                .fullName(resolveFullName(courier))
                .phone(courier != null ? courier.getPhone() : null)
                .email(courier != null ? courier.getEmail() : null)
                .vehicleType(cp != null ? cp.getVehicleType() : null)
                .tier(cp != null && cp.getTier() != null ? cp.getTier() : CourierTier.NEW)
                .trustScore(cp != null ? cp.getTrustScore() : BigDecimal.valueOf(50))
                .completedOrders(cp != null ? cp.getCompletedOrders() : 0)
                .areas(safeGetAreas(cp))
                .note(fav.getNote())
                .createdAt(fav.getCreatedAt())
                .build();
    }

    private List<String> safeGetAreas(CourierProfile cp) {
        if (cp == null) {
            return Collections.emptyList();
        }
        try {
            List<String> rawAreas = cp.getAreas();
            if (rawAreas != null && !rawAreas.isEmpty()) {
                return new ArrayList<>(rawAreas);
            }
        } catch (Exception e) {
            log.warn("Không thể tải danh sách khu vực của courier profile: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private String resolveFullName(User user) {
        if (user == null) {
            return "Người dùng FPTPost";
        }
        try {
            Optional<UserProfile> profile = userProfileRepository.findById(user.getId());
            if (profile.isPresent() && profile.get().getFullName() != null && !profile.get().getFullName().isBlank()) {
                return profile.get().getFullName();
            }
        } catch (Exception ignored) {}
        return user.getEmail() != null ? user.getEmail() : "Người dùng #" + user.getId();
    }

    private void notifyCourierNewInvitation(User courier, Order order, Invitation invitation) {
        if (notificationPublisher == null || courier.getPublicId() == null) return;
        try {
            notificationPublisher.publish(
                    courier.getPublicId(),
                    NotificationCategory.ORDER,
                    "INVITATION_NEW:" + invitation.getId(),
                    "Lời mời giao hàng trực tiếp",
                    "Bạn nhận được lời mời giao hàng cho đơn " + order.getOrderCode() + ". Vui lòng kiểm tra và phản hồi.",
                    "/courier/market"
            );
        } catch (Exception e) {
            log.warn("Lỗi khi gửi thông báo lời mời cho courier: {}", e.getMessage());
        }
    }

    private void notifySenderInvitationAccepted(User sender, Order order, User courier) {
        if (notificationPublisher == null || sender.getPublicId() == null) return;
        try {
            notificationPublisher.publish(
                    sender.getPublicId(),
                    NotificationCategory.ORDER,
                    "INVITATION_ACCEPTED:" + order.getId(),
                    "Tài xế đã nhận lời mời giao hàng",
                    "Tài xế " + resolveFullName(courier) + " đã chấp nhận lời mời giao hàng cho đơn " + order.getOrderCode() + ". Vui lòng ký quỹ để xác nhận chuyến đi.",
                    "/sender/orders/" + order.getId()
            );
        } catch (Exception e) {
            log.warn("Lỗi khi gửi thông báo chấp nhận lời mời cho sender: {}", e.getMessage());
        }
    }

    private void notifySenderInvitationRejected(User sender, Order order, User courier) {
        if (notificationPublisher == null || sender.getPublicId() == null) return;
        try {
            notificationPublisher.publish(
                    sender.getPublicId(),
                    NotificationCategory.ORDER,
                    "INVITATION_REJECTED:" + order.getId(),
                    "Tài xế từ chối lời mời giao hàng",
                    "Tài xế " + resolveFullName(courier) + " không thể nhận đơn hàng " + order.getOrderCode() + ". Đơn hàng vẫn tiếp tục được đăng trên sàn.",
                    "/sender/orders/" + order.getId()
            );
        } catch (Exception e) {
            log.warn("Lỗi khi gửi thông báo từ chối lời mời cho sender: {}", e.getMessage());
        }
    }
}

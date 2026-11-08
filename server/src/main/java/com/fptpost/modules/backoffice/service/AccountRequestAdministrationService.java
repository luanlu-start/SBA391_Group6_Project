package com.fptpost.modules.backoffice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.backoffice.dto.request.AccountRequestRejectRequest;
import com.fptpost.modules.backoffice.dto.request.AccountRequestSearchRequest;
import com.fptpost.modules.backoffice.dto.response.AccountRequestResponse;
import com.fptpost.modules.backoffice.exception.UserAdministrationErrorCode;
import com.fptpost.modules.identity.enums.AccountRequestStatus;
import com.fptpost.modules.identity.enums.AccountRequestType;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.AccountDataRequest;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * UC-57 bước 4 / BR-F-124, BR-N-040: Admin xử lý yêu cầu xuất dữ liệu và yêu cầu xóa/ẩn danh tài khoản.
 * Ẩn danh giữ nguyên đơn hàng và sổ cái vì đây là dữ liệu giao dịch bắt buộc lưu trữ.
 */
@Service
@RequiredArgsConstructor
public class AccountRequestAdministrationService {

    private static final Set<String> CLOSED_ORDER_STATUSES = Set.of("COMPLETED", "CANCELLED", "REJECTED");
    private static final Duration EXPORT_AVAILABILITY = Duration.ofDays(7);

    private final AccountDataRequestRepository requests;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final AddressRepository addresses;
    private final ConsentRepository consents;
    private final AuthSessionRepository sessions;
    private final KycRequestRepository kycRequests;
    private final OrderRepository orders;
    private final WalletRepository wallets;
    private final IdentityUserAccess userAccess;
    private final AccountRequestPresenter presenter;
    private final NotificationPublisher notifications;
    private final AuditOutboxService audit;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom identityRandom;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AccountRequestResponse> list(AccountRequestSearchRequest request) {
        AccountRequestStatus status = parse(AccountRequestStatus.class, request.getStatus(), AccountRequestStatus.PENDING);
        AccountRequestType type = parse(AccountRequestType.class, request.getType(), null);
        int page = request.getPage() == null ? 0 : request.getPage();
        int size = request.getSize() == null ? 20 : request.getSize();
        Instant now = clock.instant();
        return PageResponse.from(requests.search(status, type, PageRequest.of(page, size)).map(item -> presenter.response(item, now)));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountRequestResponse complete(UUID actorPublicId, UUID requestId) {
        User admin = userAccess.read(actorPublicId);
        AccountDataRequest request = lockOpen(requestId);
        User user = users.findByIdForUpdate(request.getUser().getId())
                .orElseThrow(() -> new AppException(UserAdministrationErrorCode.USER_NOT_FOUND));
        if (user.getId().equals(admin.getId())) throw new AppException(UserAdministrationErrorCode.SELF_FORBIDDEN);
        Instant now = clock.instant();
        Map<String, Object> before = Map.of("status", request.getStatus().name());
        request.setStatus(AccountRequestStatus.COMPLETED);
        request.setProcessedBy(admin.getId());
        request.setProcessedAt(now);
        if (request.getType() == AccountRequestType.ACCOUNT_DELETION) {
            anonymize(user, now);
            request.setResolutionNote("Tài khoản đã được ẩn danh; dữ liệu giao dịch được giữ ở dạng ẩn danh.");
        } else {
            request.setResolutionNote("Bản sao dữ liệu sẵn sàng tải trong 7 ngày.");
            notifications.publish(user.getPublicId(), NotificationCategory.SECURITY, "data-export:" + request.getPublicId(),
                    "Bản sao dữ liệu cá nhân đã sẵn sàng",
                    "Bạn có thể tải bản sao dữ liệu tại mục Quyền riêng tư trong hồ sơ trong 7 ngày.", "/me/profile");
        }
        requests.saveAndFlush(request);
        audit.enqueue("ACCOUNT_REQUEST_COMPLETED", "ACCOUNT_DATA_REQUEST", request.getPublicId().toString(), before,
                Map.of("status", request.getStatus().name(), "type", request.getType().name()));
        return presenter.response(request, now);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountRequestResponse reject(UUID actorPublicId, UUID requestId, AccountRequestRejectRequest input) {
        User admin = userAccess.read(actorPublicId);
        AccountDataRequest request = lockOpen(requestId);
        if (request.getUser().getId().equals(admin.getId())) throw new AppException(UserAdministrationErrorCode.SELF_FORBIDDEN);
        Instant now = clock.instant();
        String reason = input.getReason().strip();
        Map<String, Object> before = Map.of("status", request.getStatus().name());
        request.setStatus(AccountRequestStatus.REJECTED);
        request.setProcessedBy(admin.getId());
        request.setProcessedAt(now);
        request.setResolutionNote(reason);
        requests.saveAndFlush(request);
        audit.enqueue("ACCOUNT_REQUEST_REJECTED", "ACCOUNT_DATA_REQUEST", request.getPublicId().toString(), before,
                Map.of("status", request.getStatus().name(), "reason", reason));
        notifications.publish(request.getUser().getPublicId(), NotificationCategory.SECURITY, "data-request:" + request.getPublicId() + ":rejected",
                "Yêu cầu dữ liệu cá nhân chưa được thực hiện", "Lý do: " + reason, "/me/profile");
        return presenter.response(request, now);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public byte[] previewExport(UUID requestId) {
        AccountDataRequest request = requests.findByPublicId(requestId)
                .orElseThrow(() -> new AppException(UserAdministrationErrorCode.ACCOUNT_REQUEST_NOT_FOUND));
        if (request.getType() != AccountRequestType.DATA_EXPORT) throw new AppException(UserAdministrationErrorCode.ACCOUNT_EXPORT_NOT_READY);
        return export(request.getUser());
    }

    @Transactional(readOnly = true)
    public byte[] ownExport(UUID actorPublicId, UUID requestId) {
        User owner = userAccess.read(actorPublicId);
        AccountDataRequest request = requests.findByPublicId(requestId)
                .filter(item -> item.getUser().getId().equals(owner.getId()))
                .orElseThrow(() -> new AppException(UserAdministrationErrorCode.ACCOUNT_REQUEST_NOT_FOUND));
        boolean ready = request.getType() == AccountRequestType.DATA_EXPORT && request.getStatus() == AccountRequestStatus.COMPLETED
                && request.getProcessedAt() != null && clock.instant().isBefore(request.getProcessedAt().plus(EXPORT_AVAILABILITY));
        if (!ready) throw new AppException(UserAdministrationErrorCode.ACCOUNT_EXPORT_NOT_READY);
        return export(owner);
    }

    private AccountDataRequest lockOpen(UUID requestId) {
        AccountDataRequest request = requests.findByPublicIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(UserAdministrationErrorCode.ACCOUNT_REQUEST_NOT_FOUND));
        if (request.getStatus() != AccountRequestStatus.PENDING && request.getStatus() != AccountRequestStatus.PROCESSING) {
            throw new AppException(UserAdministrationErrorCode.ACCOUNT_REQUEST_ALREADY_PROCESSED);
        }
        return request;
    }

    private void anonymize(User user, Instant now) {
        Wallet wallet = wallets.findByUserId(user.getId()).orElse(null);
        boolean hasMoney = wallet != null && (positive(wallet.getAvailable()) || positive(wallet.getEscrowHeld())
                || positive(wallet.getPendingWithdraw()));
        boolean hasActiveOrders = active(orders.findBySenderIdOrderByCreatedAtDesc(user.getId()))
                || active(orders.findByCourierIdOrderByCreatedAtDesc(user.getId()));
        if (hasMoney || hasActiveOrders) throw new AppException(UserAdministrationErrorCode.ACCOUNT_DELETION_BLOCKED);

        String token = user.getPublicId().toString().replace("-", "");
        byte[] secret = new byte[32];
        identityRandom.nextBytes(secret);
        user.setEmail("deleted-" + token + "@deleted.fptpost.invalid");
        user.setPhone("x" + token.substring(0, 15));
        user.setPasswordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)));
        user.setPhoneVerifiedAt(null);
        user.setStatus(UserStatus.DELETED);
        user.setDeleted(true);
        user.setRoles(new HashSet<>());
        user.setUpdatedAt(now);
        users.saveAndFlush(user);
        profiles.findById(user.getId()).ifPresent(profile -> {
            profile.setFullName("Người dùng đã xóa");
            profile.setDob(null);
            profile.setBio(null);
            profile.setEmergencyContact(null);
            profile.setAvatarStorageKey(null);
            profile.setAvatarContentType(null);
            profiles.saveAndFlush(profile);
        });
        addresses.deleteAll(addresses.findByUserIdOrderByIdAsc(user.getId()));
        sessions.revokeAll(user.getId(), now);
        audit.enqueue("ACCOUNT_ANONYMIZED", "USER", user.getPublicId().toString(), Map.of("status", "ACTIVE"),
                Map.of("status", UserStatus.DELETED.name()));
    }

    private byte[] export(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("publicId", user.getPublicId());
        account.put("email", user.getEmail());
        account.put("phone", user.getPhone());
        account.put("status", user.getStatus());
        account.put("createdAt", user.getCreatedAt());
        account.put("roles", user.getRoles().stream().map(role -> role.getCode()).sorted().toList());
        data.put("account", account);
        UserProfile profile = profiles.findById(user.getId()).orElse(null);
        if (profile != null) {
            Map<String, Object> profileData = new LinkedHashMap<>();
            profileData.put("fullName", profile.getFullName());
            profileData.put("dob", profile.getDob());
            profileData.put("bio", profile.getBio());
            profileData.put("emergencyContact", profile.getEmergencyContact());
            data.put("profile", profileData);
        }
        data.put("addresses", addresses.findByUserIdOrderByIdAsc(user.getId()).stream().map(address -> Map.of(
                "label", address.getLabel(), "address", address.getAddressText(), "default", address.isDefaultAddress())).toList());
        data.put("consents", consents.findByUserIdOrderByAcceptedAtAsc(user.getId()).stream().map(consent -> Map.of(
                "policyType", consent.getPolicyType(), "policyVersion", consent.getPolicyVersion(),
                "acceptedAt", consent.getAcceptedAt())).toList());
        kycRequests.findFirstByUserIdOrderBySubmittedAtDescIdDesc(user.getId()).ifPresent(kyc -> {
            Map<String, Object> kycData = new LinkedHashMap<>();
            kycData.put("status", kyc.getStatus());
            kycData.put("submittedAt", kyc.getSubmittedAt());
            data.put("kyc", kycData);
        });
        data.put("orders", orders.findBySenderIdOrderByCreatedAtDesc(user.getId()).stream().map(this::order).toList());
        wallets.findByUserId(user.getId()).ifPresent(wallet -> data.put("wallet", Map.of(
                "available", wallet.getAvailable(), "escrowHeld", wallet.getEscrowHeld(), "pendingWithdraw", wallet.getPendingWithdraw())));
        data.put("exportedAt", clock.instant());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize personal data export", exception);
        }
    }

    private Map<String, Object> order(Order order) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderCode", order.getOrderCode());
        item.put("status", order.getStatus() == null ? null : order.getStatus().name());
        item.put("productName", order.getProductName());
        item.put("pickupAddress", order.getPickupAddress());
        item.put("deliveryAddress", order.getDeliveryAddress());
        item.put("receiverName", order.getReceiverName());
        item.put("createdAt", order.getCreatedAt());
        return item;
    }

    private static boolean active(List<Order> list) {
        return list.stream().anyMatch(order -> order.getStatus() != null && !CLOSED_ORDER_STATUSES.contains(order.getStatus().name()));
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        if ("ALL".equalsIgnoreCase(value.trim())) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AppException(UserAdministrationErrorCode.FILTER_INVALID);
        }
    }
}

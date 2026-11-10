package com.fptpost.modules.backoffice.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.backoffice.dto.request.AdminUserRolesRequest;
import com.fptpost.modules.backoffice.dto.request.AdminUserSearchRequest;
import com.fptpost.modules.backoffice.dto.request.StaffCreationRequest;
import com.fptpost.modules.backoffice.dto.response.*;
import com.fptpost.modules.backoffice.exception.UserAdministrationErrorCode;
import com.fptpost.modules.backoffice.repository.AdminUserSearchRepository;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.identity.util.ContactUtils;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.LedgerTransaction;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** UC-57: tìm kiếm, xem hồ sơ, phân vai trò người dùng và tạo tài khoản nhân sự. */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdministrationService {

    static final Set<String> ROLE_CODES = Set.of("SENDER", "COURIER", "MODERATOR", "ADMIN");
    private static final int RECENT_LIMIT = 5;

    private final AdminUserSearchRepository search;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final RoleRepository roles;
    private final AuthSessionRepository sessions;
    private final KycRequestRepository kycRequests;
    private final CourierProfileRepository couriers;
    private final AccountDataRequestRepository dataRequests;
    private final OrderRepository orders;
    private final WalletRepository wallets;
    private final LedgerTransactionRepository ledger;
    private final IdentityUserAccess userAccess;
    private final AccountRequestPresenter accountRequestPresenter;
    private final NotificationPublisher notifications;
    private final AuditOutboxService audit;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom identityRandom;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> search(AdminUserSearchRequest request) {
        String role = role(request.getRole());
        UserStatus status = status(request.getStatus());
        int page = request.getPage() == null ? 0 : request.getPage();
        int size = request.getSize() == null ? 20 : request.getSize();
        Page<User> result = search.search(pattern(request.getQ()), status, role, PageRequest.of(page, size));
        Map<Long, String> names = names(result.getContent());
        return PageResponse.from(result.map(user -> summary(user, names.get(user.getId()))));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse detail(UUID actorPublicId, UUID publicId) {
        User admin = userAccess.read(actorPublicId);
        User user = users.findByPublicId(publicId).orElseThrow(() -> new AppException(UserAdministrationErrorCode.USER_NOT_FOUND));
        return detail(user, admin);
    }

    @Transactional
    public AdminUserDetailResponse updateRoles(UUID actorPublicId, UUID publicId, AdminUserRolesRequest request) {
        User admin = userAccess.read(actorPublicId);
        Set<String> requested = request.getRoles().stream().map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!ROLE_CODES.containsAll(requested)) throw new AppException(UserAdministrationErrorCode.ROLES_INVALID);
        User user = users.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new AppException(UserAdministrationErrorCode.USER_NOT_FOUND));
        if (user.getId().equals(admin.getId())) throw new AppException(UserAdministrationErrorCode.SELF_FORBIDDEN);
        if (user.isDeleted() || user.getStatus() == UserStatus.DELETED) {
            throw new AppException(UserAdministrationErrorCode.USER_DELETED);
        }
        Set<String> current = codes(user);
        if (current.equals(requested)) return detail(user, admin);
        if (requested.contains("COURIER") && !current.contains("COURIER") && !couriers.existsByUserId(user.getId())) {
            throw new AppException(UserAdministrationErrorCode.COURIER_PROFILE_REQUIRED);
        }
        if (current.contains("ADMIN") && !requested.contains("ADMIN")
                && search.countActiveWithRole("ADMIN", UserStatus.DELETED) <= 1) {
            throw new AppException(UserAdministrationErrorCode.LAST_ADMIN);
        }

        Set<Role> assigned = requested.stream().map(code -> roles.findByCode(code)
                .orElseThrow(() -> new AppException(UserAdministrationErrorCode.ROLES_INVALID))).collect(Collectors.toSet());
        Instant now = clock.instant();
        user.setRoles(new HashSet<>(assigned));
        user.setUpdatedAt(now);
        users.saveAndFlush(user);
        // Tokens carry the role list; ending sessions makes the new permissions apply immediately.
        sessions.revokeAll(user.getId(), now);
        audit.enqueue("USER_ROLES_CHANGED", "USER", user.getPublicId().toString(),
                Map.of("roles", List.copyOf(current)), Map.of("roles", List.copyOf(requested)));
        notifications.publish(user.getPublicId(), NotificationCategory.SECURITY, "user-roles:" + user.getPublicId() + ":" + now.toEpochMilli(),
                "Quyền tài khoản đã thay đổi", "Quản trị viên đã cập nhật vai trò tài khoản của bạn. Vui lòng đăng nhập lại.",
                "/account");
        return detail(user, admin);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> staff(int page, int size) {
        Page<User> result = search.searchStaff(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        Map<Long, String> names = names(result.getContent());
        return PageResponse.from(result.map(user -> summary(user, names.get(user.getId()))));
    }

    @Transactional
    public AdminUserSummaryResponse createStaff(StaffCreationRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String phone = ContactUtils.normalizePhone(request.getPhone().trim());
        if (users.existsByEmail(email)) throw new AppException(UserAdministrationErrorCode.STAFF_EMAIL_TAKEN);
        if (users.existsByPhone(phone)) throw new AppException(UserAdministrationErrorCode.STAFF_PHONE_TAKEN);
        Role role = roles.findByCode(request.getRole()).orElseThrow(() -> new AppException(UserAdministrationErrorCode.ROLES_INVALID));
        Instant now = clock.instant();
        byte[] secret = new byte[32];
        identityRandom.nextBytes(secret);
        // Staff never receive a password: they set one through the password recovery flow.
        User staff = users.saveAndFlush(User.builder()
                .publicId(UUID.randomUUID())
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)))
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .roles(new HashSet<>(Set.of(role)))
                .build());
        profiles.saveAndFlush(UserProfile.builder().user(staff).fullName(request.getFullName().strip()).adultConfirmedAt(now).build());
        audit.enqueue("STAFF_CREATED", "USER", staff.getPublicId().toString(), Map.of(),
                Map.of("role", role.getCode(), "email", email));
        notifications.publish(staff.getPublicId(), NotificationCategory.SECURITY, "staff-created:" + staff.getPublicId(),
                "Tài khoản nhân sự FPTPost đã được tạo",
                "Bạn được cấp quyền " + role.getCode() + ". Hãy dùng chức năng Quên mật khẩu với email này để đặt mật khẩu trước khi đăng nhập.",
                "/forgot");
        return summary(staff, request.getFullName().strip());
    }

    private AdminUserDetailResponse detail(User user, User admin) {
        UserProfile profile = profiles.findById(user.getId()).orElse(null);
        var wallet = wallets.findByUserId(user.getId()).orElse(null);
        var courier = couriers.findByUserId(user.getId()).orElse(null);
        List<Order> sent = orders.findBySenderIdOrderByCreatedAtDesc(user.getId());
        List<Order> carried = orders.findByCourierIdOrderByCreatedAtDesc(user.getId());
        List<AdminUserOrderResponse> recentOrders = Stream.concat(
                        sent.stream().map(order -> order(order, "SENDER")),
                        carried.stream().map(order -> order(order, "COURIER")))
                .sorted(Comparator.comparing(AdminUserOrderResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_LIMIT).toList();
        List<AdminUserTransactionResponse> transactions = ledger.findTransactions(user.getId(), "USER_WALLET_" + user.getId(),
                        null, null, null, PageRequest.of(0, RECENT_LIMIT))
                .map(this::transaction).getContent();
        Instant now = clock.instant();
        return AdminUserDetailResponse.builder()
                .account(summary(user, profile == null ? null : profile.getFullName()))
                .dob(profile == null ? null : profile.getDob())
                .kycStatus(kycRequests.findFirstByUserIdOrderBySubmittedAtDescIdDesc(user.getId()).map(request -> request.getStatus()).orElse(null))
                .courierTier(courier == null || courier.getTier() == null ? null : courier.getTier().name())
                .courierVehicle(courier == null || courier.getVehicleType() == null ? null : courier.getVehicleType().name())
                .courierTrustScore(courier == null ? null : courier.getTrustScore())
                .courierCompletedOrders(courier == null ? null : courier.getCompletedOrders())
                .walletAvailable(wallet == null || wallet.getAvailable() == null ? 0 : wallet.getAvailable())
                .walletEscrowHeld(wallet == null || wallet.getEscrowHeld() == null ? 0 : wallet.getEscrowHeld())
                .walletPendingWithdraw(wallet == null || wallet.getPendingWithdraw() == null ? 0 : wallet.getPendingWithdraw())
                .sentOrderCount(sent.size())
                .courierOrderCount(carried.size())
                .recentOrders(recentOrders)
                .recentTransactions(transactions)
                .activeSessions(sessions.findByUserIdAndRevokedAtIsNullAndReplacedByIsNullAndExpiresAtAfterOrderByCreatedAtAscIdAsc(user.getId(), now).size())
                .dataRequests(dataRequests.findByUserIdOrderByRequestedAtDescIdDesc(user.getId()).stream()
                        .map(request -> accountRequestPresenter.response(request, now)).toList())
                .self(user.getId().equals(admin.getId()))
                .build();
    }

    private AdminUserOrderResponse order(Order order, String participation) {
        return AdminUserOrderResponse.builder().id(order.getId()).orderCode(order.getOrderCode())
                .productName(order.getProductName()).status(order.getStatus() == null ? null : order.getStatus().name())
                .participation(participation).createdAt(order.getCreatedAt()).build();
    }

    private AdminUserTransactionResponse transaction(LedgerTransaction transaction) {
        return AdminUserTransactionResponse.builder().transactionCode(transaction.getTransactionCode())
                .type(transaction.getType() == null ? null : transaction.getType().name())
                .amount(transaction.getAmount() == null ? 0 : transaction.getAmount())
                .description(transaction.getDescription()).createdAt(transaction.getCreatedAt()).build();
    }

    static AdminUserSummaryResponse summary(User user, String fullName) {
        return AdminUserSummaryResponse.builder()
                .publicId(user.getPublicId())
                .fullName(fullName)
                .email(user.getEmail())
                .phone(user.getPhone())
                .roles(codes(user).stream().sorted().toList())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerifiedAt() != null)
                .phoneVerified(user.getPhoneVerifiedAt() != null)
                .fptBadge(user.isFptBadge())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private static Set<String> codes(User user) {
        return user.getRoles().stream().map(Role::getCode).collect(Collectors.toCollection(TreeSet::new));
    }

    private Map<Long, String> names(List<User> list) {
        if (list.isEmpty()) return Map.of();
        return profiles.findAllById(list.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, UserProfile::getFullName, (first, second) -> first));
    }

    private static String role(String value) {
        if (value == null || value.isBlank()) return null;
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_CODES.contains(code)) throw new AppException(UserAdministrationErrorCode.FILTER_INVALID);
        return code;
    }

    private static UserStatus status(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UserStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AppException(UserAdministrationErrorCode.FILTER_INVALID);
        }
    }

    private static String pattern(String query) {
        if (query == null || query.isBlank()) return null;
        String text = query.trim().toLowerCase(Locale.ROOT);
        if (text.matches("0\\d{9}")) text = ContactUtils.normalizePhone(text);
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![") + "%";
    }
}

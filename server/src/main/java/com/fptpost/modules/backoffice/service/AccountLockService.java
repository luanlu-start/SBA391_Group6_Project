package com.fptpost.modules.backoffice.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.backoffice.dto.request.AccountLockRequest;
import com.fptpost.modules.backoffice.dto.request.AccountUnlockRequest;
import com.fptpost.modules.backoffice.dto.request.BlacklistRequest;
import com.fptpost.modules.backoffice.dto.response.AdminUserDetailResponse;
import com.fptpost.modules.backoffice.exception.UserAdministrationErrorCode;
import com.fptpost.modules.backoffice.model.AccountLock;
import com.fptpost.modules.backoffice.repository.AccountLockRepository;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.BlacklistEntry;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.AuthSessionRepository;
import com.fptpost.modules.identity.repository.BlacklistRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.security.KycCrypto;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.identity.util.ContactUtils;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** UC-56: khóa/mở khóa tài khoản kèm lý do, thu hồi phiên ngay và danh sách đen (BR-F-109, BR-R-081, BR-R-082). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private static final String PHONE_PATTERN = "(?:0[35789]\\d{8}|\\+84[35789]\\d{8})";
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final BlacklistRepository blacklist;
    private final AccountLockRepository locks;
    private final OrderRepository orders;
    private final IdentityUserAccess userAccess;
    private final UserAdministrationService administration;
    private final NotificationPublisher notifications;
    private final AuditOutboxService audit;
    private final KycCrypto kycCrypto;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserDetailResponse lock(UUID actorPublicId, UUID publicId, AccountLockRequest input) {
        User admin = userAccess.read(actorPublicId);
        User user = target(publicId, admin);
        if (user.getStatus() == UserStatus.LOCKED) throw new AppException(UserAdministrationErrorCode.ACCOUNT_ALREADY_LOCKED);
        Instant now = clock.instant();
        Instant until = input.getDurationHours() == null ? null : now.plus(Duration.ofHours(input.getDurationHours()));
        String reason = input.getReason().strip();
        UserStatus previous = user.getStatus();

        user.setStatus(UserStatus.LOCKED);
        user.setUpdatedAt(now);
        users.saveAndFlush(user);
        sessions.revokeAll(user.getId(), now);
        locks.save(AccountLock.builder().userId(user.getId()).previousStatus(previous).reason(reason)
                .lockedBy(admin.getId()).lockedAt(now).lockedUntil(until).build());
        List<String> cancelled = cancelOpenOrders(user, now);
        if (input.isBlacklistPhone()) addBlacklist("PHONE", ContactUtils.phoneHash(user.getPhone()), reason, admin, now);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", UserStatus.LOCKED.name());
        after.put("reason", reason);
        after.put("lockedUntil", until == null ? "PERMANENT" : until.toString());
        after.put("cancelledOrders", cancelled);
        audit.enqueue("ACCOUNT_LOCKED", "USER", user.getPublicId().toString(), Map.of("status", previous.name()), after);
        notifications.publish(user.getPublicId(), NotificationCategory.SECURITY, "account-lock:" + user.getPublicId() + ":" + now.toEpochMilli(),
                "Tài khoản đã bị khóa",
                (until == null ? "Tài khoản của bạn bị khóa. " : "Tài khoản của bạn bị khóa đến " + UNTIL.format(until) + ". ")
                        + "Lý do: " + reason + " Liên hệ hỗ trợ nếu bạn cần khiếu nại.", null);
        return administration.detail(user, admin);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserDetailResponse unlock(UUID actorPublicId, UUID publicId, AccountUnlockRequest input) {
        User admin = userAccess.read(actorPublicId);
        User user = target(publicId, admin);
        if (user.getStatus() != UserStatus.LOCKED) throw new AppException(UserAdministrationErrorCode.ACCOUNT_NOT_LOCKED);
        Instant now = clock.instant();
        String reason = input.getReason().strip();
        AccountLock lock = locks.findFirstByUserIdAndUnlockedAtIsNullOrderByLockedAtDesc(user.getId()).orElse(null);
        release(user, lock, admin.getId(), reason, now);
        audit.enqueue("ACCOUNT_UNLOCKED", "USER", user.getPublicId().toString(), Map.of("status", UserStatus.LOCKED.name()),
                Map.of("status", user.getStatus().name(), "reason", reason));
        return administration.detail(user, admin);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void addToBlacklist(UUID actorPublicId, BlacklistRequest input) {
        User admin = userAccess.read(actorPublicId);
        String value = input.getValue().trim();
        String hash = switch (input.getKind()) {
            case "PHONE" -> {
                if (!value.matches(PHONE_PATTERN)) throw new AppException(UserAdministrationErrorCode.BLACKLIST_VALUE_INVALID);
                yield ContactUtils.phoneHash(ContactUtils.normalizePhone(value));
            }
            case "ID_NUMBER" -> {
                if (!value.matches("\\d{12}")) throw new AppException(UserAdministrationErrorCode.BLACKLIST_VALUE_INVALID);
                yield kycCrypto.identityHash(value);
            }
            default -> sha256(value);
        };
        if (blacklist.existsByKindAndValueHash(input.getKind(), hash)) throw new AppException(UserAdministrationErrorCode.BLACKLIST_DUPLICATED);
        addBlacklist(input.getKind(), hash, input.getReason().strip(), admin, clock.instant());
        audit.enqueue("BLACKLIST_ADDED", "BLACKLIST", input.getKind(), Map.of(), Map.of("kind", input.getKind(), "reason", input.getReason().strip()));
    }

    /** Mở khóa các lần khóa tạm thời đã hết hạn; mỗi tài khoản xử lý trong giao dịch riêng. */
    public int releaseExpired() {
        Instant now = clock.instant();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        int released = 0;
        for (Long id : locks.findExpiredIds(now, PageRequest.of(0, 100))) {
            try {
                Boolean done = transaction.execute(status -> {
                    AccountLock lock = locks.findByIdForUpdate(id).orElse(null);
                    if (lock == null || lock.getUnlockedAt() != null) return false;
                    User user = users.findByIdForUpdate(lock.getUserId()).orElse(null);
                    if (user == null || user.getStatus() != UserStatus.LOCKED) {
                        lock.setUnlockedAt(now);
                        lock.setUnlockReason("Tài khoản không còn ở trạng thái khóa");
                        return false;
                    }
                    release(user, lock, null, "Hết thời hạn khóa", now);
                    return true;
                });
                if (Boolean.TRUE.equals(done)) released++;
            } catch (RuntimeException exception) {
                log.warn("Could not release expired account lock {}", id, exception);
            }
        }
        return released;
    }

    private void release(User user, AccountLock lock, Long actorId, String reason, Instant now) {
        UserStatus restored = lock == null || lock.getPreviousStatus() == UserStatus.LOCKED ? UserStatus.ACTIVE : lock.getPreviousStatus();
        user.setStatus(restored);
        user.setUpdatedAt(now);
        users.saveAndFlush(user);
        if (lock != null) {
            lock.setUnlockedBy(actorId);
            lock.setUnlockedAt(now);
            lock.setUnlockReason(reason);
            locks.save(lock);
        }
        notifications.publish(user.getPublicId(), NotificationCategory.SECURITY, "account-unlock:" + user.getPublicId() + ":" + now.toEpochMilli(),
                "Tài khoản đã được mở khóa", "Bạn có thể đăng nhập lại. Ghi chú: " + reason, null);
    }

    private User target(UUID publicId, User admin) {
        User user = users.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new AppException(UserAdministrationErrorCode.USER_NOT_FOUND));
        if (user.getId().equals(admin.getId())) throw new AppException(UserAdministrationErrorCode.SELF_FORBIDDEN);
        if (user.isDeleted() || user.getStatus() == UserStatus.DELETED) throw new AppException(UserAdministrationErrorCode.USER_DELETED);
        return user;
    }

    /** BR-R-081: đơn chưa có người giao và chưa ký quỹ được hủy ngay; đơn còn lại hiển thị để Admin xử lý. */
    private List<String> cancelOpenOrders(User user, Instant now) {
        List<String> cancelled = new ArrayList<>();
        for (Order order : orders.findBySenderIdOrderByCreatedAtDesc(user.getId())) {
            boolean open = order.getCourierId() == null
                    && (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.FINDING_SHIPPER);
            if (!open) continue;
            order.setStatus(OrderStatus.CANCELLED);
            order.setEndedAt(now);
            orders.save(order);
            cancelled.add(order.getOrderCode());
        }
        return cancelled;
    }

    private void addBlacklist(String kind, String hash, String reason, User admin, Instant now) {
        if (blacklist.existsByKindAndValueHash(kind, hash)) return;
        blacklist.save(BlacklistEntry.builder().kind(kind).valueHash(hash).reason(reason).createdBy(admin).createdAt(now).build());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

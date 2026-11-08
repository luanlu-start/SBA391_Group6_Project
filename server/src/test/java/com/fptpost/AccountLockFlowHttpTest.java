package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.backoffice.repository.AccountLockRepository;
import com.fptpost.modules.backoffice.service.AccountLockService;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.BlacklistRepository;
import com.fptpost.modules.identity.util.ContactUtils;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AccountLockFlowHttpTest extends IdentityFeatureHttpTest {
    static final String USERS = "/api/v1/admin/users/";
    @Autowired AccountLockRepository locks;
    @Autowired AccountLockService lockService;
    @Autowired BlacklistRepository blacklist;
    @Autowired OrderRepository orders;
    @Autowired AuditOutboxRepository auditOutbox;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    User admin;
    String adminToken;

    @AfterEach void cleanLocks() {
        locks.deleteAll(); blacklist.deleteAll(); orders.deleteAll();
    }

    @BeforeEach void setupLocks() throws Exception {
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll();
        int number = sequence.incrementAndGet();
        admin = createUser("admin" + number + "@example.invalid", "+845" + String.format(Locale.ROOT, "%08d", number + 41));
        admin.setRoles(new HashSet<>(Set.of(roles.findByCode("ADMIN").orElseThrow())));
        admin = users.saveAndFlush(admin);
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
    }

    @Test void lockingRevokesSessionsCancelsOpenOrdersAndBlocksLogin() throws Exception {
        Order open = orders.saveAndFlush(order("FPT-OPEN-1", OrderStatus.FINDING_SHIPPER, null));
        Order assigned = orders.saveAndFlush(order("FPT-ASSIGNED-1", OrderStatus.SHIPPER_ASSIGNED, 999L));

        var locked = data(write("POST", USERS + user.getPublicId() + "/lock",
                Map.of("reason", "Gian lận thanh toán", "durationHours", 72, "blacklistPhone", true), adminToken, 200));
        assertThat(locked.path("account").path("status").asText()).isEqualTo("LOCKED");
        assertThat(locked.path("lock").path("permanent").asBoolean()).isFalse();
        assertThat(locked.path("lock").path("lockedUntil").asText()).isEqualTo("2026-10-23T12:00:00Z");
        assertThat(locked.path("ordersNeedingAction").findValuesAsText("orderCode")).containsExactly("FPT-ASSIGNED-1");
        assertThat(orders.findById(open.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orders.findById(assigned.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPER_ASSIGNED);
        assertThat(blacklist.existsByKindAndValueHash("PHONE", ContactUtils.phoneHash(user.getPhone()))).isTrue();

        send(get("/api/v1/me").header("Authorization", "Bearer " + token), 401);
        login(user.getEmail(), "OldPassword123!", 403);
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("ACCOUNT_LOCKED"));
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(user.getPublicId())
                && event.getEventKey().startsWith("account-lock:"));
        write("POST", USERS + user.getPublicId() + "/lock", Map.of("reason", "Lần nữa"), adminToken, 409);
    }

    @Test void unlockRestoresAccessAndExpiredTemporaryLocksAreReleased() throws Exception {
        write("POST", USERS + user.getPublicId() + "/lock", Map.of("reason", "Kiểm tra"), adminToken, 200);
        write("POST", USERS + user.getPublicId() + "/unlock", Map.of("reason", " "), adminToken, 400);
        var unlocked = data(write("POST", USERS + user.getPublicId() + "/unlock", Map.of("reason", "Đã xác minh"), adminToken, 200));
        assertThat(unlocked.path("account").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(unlocked.path("lock").isNull()).isTrue();
        login(user.getEmail(), "OldPassword123!", 200);
        write("POST", USERS + user.getPublicId() + "/unlock", Map.of("reason", "Lần nữa"), adminToken, 409);

        write("POST", USERS + user.getPublicId() + "/lock", Map.of("reason", "Tạm khóa", "durationHours", 1), adminToken, 200);
        assertThat(lockService.releaseExpired()).isZero();
        now.set(now.get().plusSeconds(3601));
        assertThat(lockService.releaseExpired()).isEqualTo(1);
        assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test void adminsCannotLockThemselvesAndBlacklistValuesAreValidated() throws Exception {
        assertThat(body(write("POST", USERS + admin.getPublicId() + "/lock", Map.of("reason", "Tự khóa"), adminToken, 403))
                .path("code").asText()).isEqualTo("ADMIN_USER_SELF_FORBIDDEN");
        write("POST", USERS + user.getPublicId() + "/lock", Map.of("reason", "Sai", "durationHours", 0), adminToken, 400);
        write("POST", USERS + user.getPublicId() + "/lock", Map.of("reason", "x"), token, 403);

        write("POST", "/api/v1/admin/blacklist", Map.of("kind", "PHONE", "value", "0912000111", "reason", "Lừa đảo"), adminToken, 201);
        write("POST", "/api/v1/admin/blacklist", Map.of("kind", "PHONE", "value", "+84912000111", "reason", "Lặp"), adminToken, 409);
        write("POST", "/api/v1/admin/blacklist", Map.of("kind", "ID_NUMBER", "value", "12345", "reason", "Sai"), adminToken, 400);
        write("POST", "/api/v1/admin/blacklist", Map.of("kind", "EMAIL", "value", "a@b.c", "reason", "Sai"), adminToken, 400);
        assertThat(blacklist.existsByKindAndValueHash("PHONE", ContactUtils.phoneHash("+84912000111"))).isTrue();
    }

    private Order order(String code, OrderStatus status, Long courierId) {
        return Order.builder().orderCode(code).senderId(user.getId()).courierId(courierId).status(status)
                .pickupAddress("A").deliveryAddress("B").receiverName("Người Nhận").receiverPhone("0901234567")
                .productName("Hàng thử").quantity(1).weightKg(BigDecimal.ONE).build();
    }
}

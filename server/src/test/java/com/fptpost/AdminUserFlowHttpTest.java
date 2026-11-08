package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AdminUserFlowHttpTest extends IdentityFeatureHttpTest {
    static final String USERS = "/api/v1/admin/users";
    @Autowired WalletRepository wallets;
    @Autowired AuditOutboxRepository auditOutbox;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    User admin;
    String adminToken;

    @BeforeEach void setupAdministration() throws Exception {
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll(); wallets.deleteAll();
        admin = staff("ADMIN", 31);
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
    }

    @Test void searchFiltersByTextRoleAndStatusAndRejectsUnknownFilters() throws Exception {
        var byEmail = data(send(get(USERS).header("Authorization", "Bearer " + adminToken).param("q", user.getEmail().substring(0, 8)), 200));
        assertThat(byEmail.path("content").findValuesAsText("email")).contains(user.getEmail());
        var admins = data(send(get(USERS).header("Authorization", "Bearer " + adminToken).param("role", "ADMIN"), 200));
        assertThat(admins.path("content").findValuesAsText("email")).containsExactly(admin.getEmail());
        var localPhone = "0" + user.getPhone().substring(3);
        assertThat(data(send(get(USERS).header("Authorization", "Bearer " + adminToken).param("q", localPhone), 200))
                .path("totalElements").asInt()).isEqualTo(1);
        assertThat(data(send(get(USERS).header("Authorization", "Bearer " + adminToken).param("status", "LOCKED"), 200))
                .path("totalElements").asInt()).isZero();
        assertThat(body(send(get(USERS).header("Authorization", "Bearer " + adminToken).param("role", "ROOT"), 400))
                .path("code").asText()).isEqualTo("ADMIN_USER_FILTER_INVALID");
        send(get(USERS).header("Authorization", "Bearer " + token), 403);
    }

    @Test void detailShowsWalletAndRoleChangesRevokeTheTargetSessions() throws Exception {
        wallets.saveAndFlush(Wallet.builder().userId(user.getId()).available(150_000L).build());
        var detail = data(send(get(USERS + "/" + user.getPublicId()).header("Authorization", "Bearer " + adminToken), 200));
        assertThat(detail.path("account").path("roles").get(0).asText()).isEqualTo("SENDER");
        assertThat(detail.path("walletAvailable").asLong()).isEqualTo(150_000L);
        assertThat(detail.path("activeSessions").asInt()).isEqualTo(1);

        var updated = data(write("PUT", USERS + "/" + user.getPublicId() + "/roles", Map.of("roles", List.of("SENDER", "MODERATOR")), adminToken, 200));
        assertThat(updated.path("account").path("roles").toString()).contains("MODERATOR", "SENDER");
        assertThat(updated.path("activeSessions").asInt()).isZero();
        send(get("/api/v1/me").header("Authorization", "Bearer " + token), 401);
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("USER_ROLES_CHANGED"));

        assertThat(body(write("PUT", USERS + "/" + user.getPublicId() + "/roles", Map.of("roles", List.of("COURIER")), adminToken, 409))
                .path("code").asText()).isEqualTo("ADMIN_USER_COURIER_PROFILE_REQUIRED");
        write("PUT", USERS + "/" + user.getPublicId() + "/roles", Map.of("roles", List.of("ROOT")), adminToken, 400);
        assertThat(body(write("PUT", USERS + "/" + admin.getPublicId() + "/roles", Map.of("roles", List.of("SENDER")), adminToken, 403))
                .path("code").asText()).isEqualTo("ADMIN_USER_SELF_FORBIDDEN");
    }

    @Test void staffAccountsAreCreatedWithoutAPasswordAndListed() throws Exception {
        var created = data(write("POST", "/api/v1/admin/staff", Map.of("fullName", "Kiểm Duyệt Viên", "email", "Mod.New@Example.invalid",
                "phone", "0912345678", "role", "MODERATOR"), adminToken, 201));
        assertThat(created.path("email").asText()).isEqualTo("mod.new@example.invalid");
        assertThat(created.path("roles").get(0).asText()).isEqualTo("MODERATOR");
        UUID staffId = UUID.fromString(created.path("publicId").asText());
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(staffId));
        write("POST", "/api/v1/admin/staff", Map.of("fullName", "Trùng", "email", "mod.new@example.invalid",
                "phone", "0912345679", "role", "MODERATOR"), adminToken, 409);
        write("POST", "/api/v1/admin/staff", Map.of("fullName", "Sai vai trò", "email", "x@example.invalid",
                "phone", "0912345670", "role", "SENDER"), adminToken, 400);
        var staffList = data(send(get("/api/v1/admin/staff").header("Authorization", "Bearer " + adminToken), 200));
        assertThat(staffList.path("content").findValuesAsText("email")).contains("mod.new@example.invalid", admin.getEmail());
    }

    @Test void dataExportIsReleasedToTheOwnerOnlyAfterApproval() throws Exception {
        String id = data(write("POST", "/api/v1/me/data-export", Map.of("reason", "Cần lưu trữ"), token, 200)).path("id").asText();
        send(get("/api/v1/me/data-requests/" + id + "/export").header("Authorization", "Bearer " + token), 409);
        var queue = data(send(get("/api/v1/admin/account-requests").header("Authorization", "Bearer " + adminToken), 200));
        assertThat(queue.path("content").findValuesAsText("id")).contains(id);

        var completed = data(write("POST", "/api/v1/admin/account-requests/" + id + "/complete", Map.of(), adminToken, 200));
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        write("POST", "/api/v1/admin/account-requests/" + id + "/complete", Map.of(), adminToken, 409);
        var file = send(get("/api/v1/me/data-requests/" + id + "/export").header("Authorization", "Bearer " + token), 200);
        String json = file.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains(user.getEmail(), "\"profile\"").doesNotContain("passwordHash");
        send(get("/api/v1/me/data-requests/" + id + "/export").header("Authorization", "Bearer " + adminToken), 404);
    }

    @Test void deletionIsBlockedByWalletBalanceThenAnonymizesTheAccount() throws Exception {
        String id = data(write("POST", "/api/v1/me/delete-request", Map.of("reason", "Không dùng nữa"), token, 200)).path("id").asText();
        Wallet wallet = wallets.saveAndFlush(Wallet.builder().userId(user.getId()).available(50_000L).build());
        assertThat(body(write("POST", "/api/v1/admin/account-requests/" + id + "/complete", Map.of(), adminToken, 409))
                .path("code").asText()).isEqualTo("ACCOUNT_DELETION_BLOCKED");
        wallet.setAvailable(0L);
        wallets.saveAndFlush(wallet);
        write("POST", "/api/v1/admin/account-requests/" + id + "/complete", Map.of(), adminToken, 200);
        User anonymized = users.findById(user.getId()).orElseThrow();
        assertThat(anonymized.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(anonymized.getEmail()).endsWith("@deleted.fptpost.invalid");
        assertThat(profiles.findById(user.getId()).orElseThrow().getFullName()).isEqualTo("Người dùng đã xóa");
        send(get("/api/v1/me").header("Authorization", "Bearer " + token), 401);
    }

    @Test void rejectedRequestsKeepTheReason() throws Exception {
        String id = data(write("POST", "/api/v1/me/delete-request", Map.of(), token, 200)).path("id").asText();
        write("POST", "/api/v1/admin/account-requests/" + id + "/reject", Map.of("reason", ""), adminToken, 400);
        var rejected = data(write("POST", "/api/v1/admin/account-requests/" + id + "/reject",
                Map.of("reason", "Đơn hàng đang khiếu nại"), adminToken, 200));
        assertThat(rejected.path("resolutionNote").asText()).isEqualTo("Đơn hàng đang khiếu nại");
        assertThat(data(send(get("/api/v1/admin/account-requests").header("Authorization", "Bearer " + adminToken)
                .param("status", "REJECTED"), 200)).path("totalElements").asInt()).isEqualTo(1);
        send(post("/api/v1/admin/account-requests/" + id + "/complete").header("Authorization", "Bearer " + token), 403);
    }

    private User staff(String role, int offset) {
        int number = sequence.incrementAndGet();
        User result = createUser(role.toLowerCase(Locale.ROOT) + number + "@example.invalid", "+846" + String.format(Locale.ROOT, "%08d", number + offset));
        result.setRoles(new HashSet<>(Set.of(roles.findByCode(role).orElseThrow())));
        return users.saveAndFlush(result);
    }
}

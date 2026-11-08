package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.model.WithdrawalStatus;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AdminWithdrawalFlowHttpTest extends IdentityFeatureHttpTest {
    static final String URL = "/api/v1/admin/withdrawals";
    @Autowired WalletRepository wallets;
    @Autowired WithdrawalRequestRepository withdrawals;
    @Autowired LedgerTransactionRepository ledger;
    @Autowired AuditOutboxRepository auditOutbox;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired JdbcTemplate jdbc;
    User firstAdmin;
    User secondAdmin;
    String firstAdminToken;
    String secondAdminToken;

    @BeforeEach void setupWithdrawals() throws Exception {
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll();
        withdrawals.deleteAll(); ledger.deleteAll(); wallets.deleteAll();
        firstAdmin = staff("ADMIN", 11); secondAdmin = staff("ADMIN", 12);
        firstAdminToken = access(login(firstAdmin.getEmail(), "OldPassword123!", 200));
        secondAdminToken = access(login(secondAdmin.getEmail(), "OldPassword123!", 200));
        fund(user, 20_000_000L);
    }

    @Test void smallRequestIsApprovedOnceThenSettledWithManualReference() throws Exception {
        long id = request(token, 200_000L);
        var queue = data(send(get(URL).header("Authorization", "Bearer " + firstAdminToken), 200));
        assertThat(queue.path("totalElements").asInt()).isEqualTo(1);
        var item = queue.path("content").get(0);
        assertThat(item.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(item.path("dualApprovalRequired").asBoolean()).isFalse();
        assertThat(item.path("applicant").path("email").asText()).isEqualTo(user.getEmail());
        assertThat(item.path("canApprove").asBoolean()).isTrue();

        var approved = data(write("POST", URL + "/" + id + "/approve", Map.of(), firstAdminToken, 200));
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.path("payoutProvider").asText()).isEqualTo("MANUAL");
        assertThat(approved.path("reconciliation").path("walletPendingWithdraw").asLong()).isEqualTo(200_000L);

        var paid = data(write("POST", URL + "/" + id + "/mark-paid", Map.of("reference", "FT26310001"), firstAdminToken, 200));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("settledBy").path("email").asText()).isEqualTo(firstAdmin.getEmail());
        Wallet wallet = wallets.findByUserId(user.getId()).orElseThrow();
        assertThat(wallet.getAvailable()).isEqualTo(19_800_000L);
        assertThat(wallet.getPendingWithdraw()).isZero();
        assertThat(ledger.findAll()).extracting("type").contains(LedgerTransactionType.WITHDRAWAL_PAYOUT);
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(user.getPublicId())
                && event.getEventKey().endsWith(":paid"));
        assertThat(auditOutbox.findAll()).extracting(event -> event.getPayload())
                .anyMatch(payload -> payload.contains("WITHDRAWAL_APPROVED")).anyMatch(payload -> payload.contains("WITHDRAWAL_PAID"));

        long other = request(token, 300_000L);
        write("POST", URL + "/" + other + "/approve", Map.of(), firstAdminToken, 200);
        write("POST", URL + "/" + other + "/mark-paid", Map.of("reference", "FT26310001"), firstAdminToken, 409);
        write("POST", URL + "/" + id + "/mark-paid", Map.of("reference", "FT26310002"), firstAdminToken, 409);
    }

    @Test void largeRequestNeedsTwoDifferentAdministrators() throws Exception {
        long id = request(token, 6_000_000L);
        var first = data(write("POST", URL + "/" + id + "/approve", Map.of(), firstAdminToken, 200));
        assertThat(first.path("status").asText()).isEqualTo("APPROVED_L1");
        assertThat(first.path("dualApprovalRequired").asBoolean()).isTrue();
        assertThat(first.path("canApprove").asBoolean()).isFalse();
        var problem = body(write("POST", URL + "/" + id + "/approve", Map.of(), firstAdminToken, 409));
        assertThat(problem.path("code").asText()).isEqualTo("WITHDRAWAL_SECOND_APPROVER_REQUIRED");
        var second = data(write("POST", URL + "/" + id + "/approve", Map.of(), secondAdminToken, 200));
        assertThat(second.path("status").asText()).isEqualTo("APPROVED");
        assertThat(second.path("firstApprover").path("email").asText()).isEqualTo(firstAdmin.getEmail());
        assertThat(second.path("approver").path("email").asText()).isEqualTo(secondAdmin.getEmail());
    }

    @Test void rejectionRefundsAvailableBalanceAndAdminsCannotDecideTheirOwnRequest() throws Exception {
        long id = request(token, 500_000L);
        write("POST", URL + "/" + id + "/reject", Map.of("reason", " "), firstAdminToken, 400);
        var rejected = data(write("POST", URL + "/" + id + "/reject", Map.of("reason", "Sai tên chủ tài khoản"), firstAdminToken, 200));
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.path("rejectionReason").asText()).isEqualTo("Sai tên chủ tài khoản");
        Wallet wallet = wallets.findByUserId(user.getId()).orElseThrow();
        assertThat(wallet.getAvailable()).isEqualTo(20_000_000L);
        assertThat(wallet.getPendingWithdraw()).isZero();
        assertThat(ledger.findAll()).filteredOn(transaction -> transaction.getType() == LedgerTransactionType.WITHDRAWAL_REFUND)
                .singleElement().satisfies(transaction -> assertThat(transaction.getCreatedBy()).isNull());
        write("POST", URL + "/" + id + "/approve", Map.of(), secondAdminToken, 409);

        fund(firstAdmin, 1_000_000L);
        long own = request(firstAdminToken, 100_000L);
        var problem = body(write("POST", URL + "/" + own + "/approve", Map.of(), firstAdminToken, 403));
        assertThat(problem.path("code").asText()).isEqualTo("WITHDRAWAL_SELF_REVIEW_FORBIDDEN");
        write("POST", URL + "/" + own + "/approve", Map.of(), secondAdminToken, 200);
    }

    @Test void exportMovesApprovedRequestsToProcessingAndImportSettlesEachRowIndependently() throws Exception {
        long paid = request(token, 200_000L);
        User second = createUser("payee" + sequence.incrementAndGet() + "@example.invalid", "+84866666661");
        fund(second, 1_000_000L);
        String secondToken = access(login(second.getEmail(), "OldPassword123!", 200));
        long failed = request(secondToken, 400_000L);
        write("POST", URL + "/" + paid + "/approve", Map.of(), firstAdminToken, 200);
        write("POST", URL + "/" + failed + "/approve", Map.of(), firstAdminToken, 200);

        var export = send(post(URL + "/export").header("Authorization", "Bearer " + firstAdminToken), 200);
        String csv = export.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(export.getResponse().getContentType()).startsWith("text/csv");
        assertThat(export.getResponse().getHeader("X-Export-Count")).isEqualTo("2");
        assertThat(csv).contains("request_code,bank_name,account_number").contains("FPTPOST WDR-").contains("img.vietqr.io");
        assertThat(withdrawals.findAll()).allMatch(item -> item.getStatus() == WithdrawalStatus.PROCESSING);
        write("POST", URL + "/export", Map.of(), firstAdminToken, 409);

        String paidCode = withdrawals.findById(paid).orElseThrow().getRequestCode();
        String failedCode = withdrawals.findById(failed).orElseThrow().getRequestCode();
        String result = "request_code,result,reference,reason\n"
                + paidCode + ",PAID,FT26310077,\n"
                + failedCode + ",FAILED,,\"Tài khoản đã đóng, cần cập nhật\"\n"
                + "WDR-UNKNOWN,PAID,FT26310078,\n";
        var summary = data(send(multipart(URL + "/import-result")
                .file(new MockMultipartFile("file", "ket-qua.csv", "text/csv", result.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + firstAdminToken), 200));
        assertThat(summary.path("paid").asInt()).isEqualTo(1);
        assertThat(summary.path("failed").asInt()).isEqualTo(1);
        assertThat(summary.path("errors")).hasSize(1);
        assertThat(summary.path("errors").get(0).path("code").asText()).isEqualTo("WITHDRAWAL_REQUEST_NOT_FOUND");
        assertThat(summary.path("errors").get(0).path("line").asInt()).isEqualTo(4);
        assertThat(withdrawals.findById(paid).orElseThrow().getStatus()).isEqualTo(WithdrawalStatus.PAID);
        var failedRequest = withdrawals.findById(failed).orElseThrow();
        assertThat(failedRequest.getStatus()).isEqualTo(WithdrawalStatus.FAILED);
        assertThat(failedRequest.getFailureReason()).isEqualTo("Tài khoản đã đóng, cần cập nhật");
        assertThat(wallets.findByUserId(second.getId()).orElseThrow().getAvailable()).isEqualTo(1_000_000L);
    }

    @Test void queueFiltersFlagOverdueRequestsAndRejectsNonAdministrators() throws Exception {
        long id = request(token, 200_000L);
        jdbc.update("update withdrawal_requests set created_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-10-16T12:00:00Z")), id);
        var stored = withdrawals.findById(id).orElseThrow();
        var item = data(send(get(URL).header("Authorization", "Bearer " + firstAdminToken), 200)).path("content").get(0);
        assertThat(item.path("dueAt").asText()).isEqualTo("2026-10-19T12:00:00Z");
        assertThat(item.path("overdue").asBoolean()).isTrue();
        assertThat(data(send(get(URL).header("Authorization", "Bearer " + firstAdminToken)
                .param("q", stored.getRequestCode().toLowerCase(Locale.ROOT)), 200)).path("totalElements").asInt()).isEqualTo(1);
        assertThat(data(send(get(URL).header("Authorization", "Bearer " + firstAdminToken).param("q", "%"), 200))
                .path("totalElements").asInt()).isZero();
        assertThat(data(send(get(URL).header("Authorization", "Bearer " + firstAdminToken).param("status", "PAID"), 200))
                .path("totalElements").asInt()).isZero();
        assertThat(body(send(get(URL).header("Authorization", "Bearer " + firstAdminToken).param("status", "UNKNOWN"), 400))
                .path("code").asText()).isEqualTo("WITHDRAWAL_REVIEW_FILTER_INVALID");
        send(get(URL).header("Authorization", "Bearer " + token), 403);
        send(get(URL), 401);
    }

    private User staff(String role, int offset) {
        int number = sequence.incrementAndGet();
        User result = createUser(role.toLowerCase(Locale.ROOT) + number + "@example.invalid",
                "+848" + String.format(Locale.ROOT, "%08d", number + offset));
        result.setRoles(new HashSet<>(Set.of(roles.findByCode(role).orElseThrow())));
        return users.saveAndFlush(result);
    }

    private void fund(User owner, long amount) {
        Wallet wallet = wallets.findByUserId(owner.getId()).orElseGet(() -> Wallet.builder().userId(owner.getId()).build());
        wallet.setAvailable(amount);
        wallets.saveAndFlush(wallet);
    }

    private long request(String bearer, long amount) throws Exception {
        var created = data(write("POST", "/api/v1/wallet/withdrawals", Map.of("amount", amount, "bankName", "Vietcombank",
                "accountNumber", "0123456789", "accountHolderName", "Nguyen Van A"), bearer, 201));
        return created.path("id").asLong();
    }
}

package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.dispute.dto.request.OpenComplaintCommand;
import com.fptpost.modules.dispute.enums.ComplaintReason;
import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.dispute.repository.ComplaintEvidenceRepository;
import com.fptpost.modules.dispute.repository.ComplaintMessageRepository;
import com.fptpost.modules.dispute.repository.ComplaintRepository;
import com.fptpost.modules.dispute.service.ComplaintLifecycle;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.*;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class ComplaintFlowHttpTest extends IdentityFeatureHttpTest {
    static final String MOD = "/api/v1/mod/complaints/";
    static final String PARTY = "/api/v1/complaints/";
    @Autowired ComplaintLifecycle lifecycle;
    @Autowired ComplaintRepository complaints;
    @Autowired ComplaintMessageRepository complaintMessages;
    @Autowired ComplaintEvidenceRepository complaintEvidence;
    @Autowired OrderRepository orders;
    @Autowired EscrowRepository escrows;
    @Autowired WalletRepository wallets;
    @Autowired LedgerTransactionRepository ledger;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired AuditOutboxRepository auditOutbox;
    User courier;
    String courierToken;
    User moderator;
    String moderatorToken;
    User admin;
    String adminToken;
    User reviewer;
    String reviewerToken;

    @BeforeEach void setupComplaints() throws Exception {
        clean();
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll();
        courier = person("courier", Set.of("COURIER"), 61);
        courierToken = access(login(courier.getEmail(), "OldPassword123!", 200));
        moderator = person("moderator", Set.of("MODERATOR"), 62);
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        admin = person("admin", Set.of("ADMIN"), 63);
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
        reviewer = person("reviewer", Set.of("ADMIN"), 64);
        reviewerToken = access(login(reviewer.getEmail(), "OldPassword123!", 200));
    }

    @AfterEach void clean() {
        complaintEvidence.deleteAll(); complaintMessages.deleteAll(); complaints.deleteAll();
        ledger.deleteAll(); escrows.deleteAll(); wallets.deleteAll(); orders.deleteAll();
    }

    @Test void moderatorSplitsHeldEscrowAndMoneyMovesWhenTheAppealWindowEnds() throws Exception {
        Order order = order("FPT-KN-01", new BigDecimal("500000"));
        heldEscrow(order, 200_000);
        Complaint opened = open(order, user, ComplaintReason.DAMAGED, "Hộp bị móp, hàng vỡ một góc");
        String id = opened.getPublicId().toString();
        assertThat(opened.getResolveDueAt()).isEqualTo(Instant.parse("2026-10-27T12:00:00Z"));
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DISPUTED);
        assertThat(escrows.findByOrderId(order.getId()).orElseThrow().isFrozen()).isTrue();
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(moderator.getPublicId())
                && event.getEventKey().endsWith(":opened"));

        var queue = data(send(get("/api/v1/mod/complaints").header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(queue.path("totalElements").asInt()).isEqualTo(1);
        assertThat(queue.path("content").get(0).path("openedBy").path("email").asText()).isEqualTo(user.getEmail());
        send(get("/api/v1/mod/complaints").header("Authorization", "Bearer " + token), 403);
        send(get("/api/v1/mod/complaints?status=WRONG").header("Authorization", "Bearer " + moderatorToken), 400);

        User outsider = person("outsider", Set.of("SENDER"), 65);
        String outsiderToken = access(login(outsider.getEmail(), "OldPassword123!", 200));
        send(get(PARTY + id).header("Authorization", "Bearer " + outsiderToken), 404);
        var senderView = data(send(get(PARTY + id).header("Authorization", "Bearer " + token), 200));
        assertThat(senderView.path("viewerRole").asText()).isEqualTo("SENDER");
        assertThat(senderView.path("courier").path("email").isNull()).isTrue();
        assertThat(senderView.path("messages")).hasSize(1);
        String evidenceId = senderView.path("messages").get(0).path("evidenceIds").get(0).asText();
        MvcResult photo = send(get(PARTY + id + "/evidence/" + evidenceId).header("Authorization", "Bearer " + courierToken), 200);
        assertThat(photo.getResponse().getContentType()).isEqualTo("image/png");
        send(get(PARTY + id + "/evidence/" + evidenceId).header("Authorization", "Bearer " + outsiderToken), 404);

        write("POST", MOD + id + "/ruling", ruling("SENDER_FAVOR", null, 0, "Thiếu"), moderatorToken, 409);
        var claimed = data(write("POST", MOD + id + "/claim", Map.of(), moderatorToken, 200));
        assertThat(claimed.path("status").asText()).isEqualTo("INVESTIGATING");
        assertThat(claimed.path("permissions").path("rule").asBoolean()).isTrue();
        write("POST", MOD + id + "/claim", Map.of(), moderatorToken, 409);

        var waiting = data(write("POST", MOD + id + "/request-info", Map.of("message", "Gửi thêm ảnh lúc nhận hàng"), moderatorToken, 200));
        assertThat(waiting.path("status").asText()).isEqualTo("AWAITING_INFO");
        var replied = data(message(courierToken, id, "Ảnh lúc bàn giao", image("files", "png"), 200));
        assertThat(replied.path("status").asText()).isEqualTo("INVESTIGATING");
        message(courierToken, id, "", null, 400);

        write("POST", MOD + id + "/ruling", ruling("SPLIT", null, 0, "Hai bên cùng có lỗi"), moderatorToken, 400);
        write("POST", MOD + id + "/ruling", ruling("SPLIT", 40, 600_000, "Vượt giá trị khai báo"), moderatorToken, 400);
        var ruled = data(write("POST", MOD + id + "/ruling", ruling("SPLIT", 40, 100_000, "Đóng gói chưa kỹ, vận chuyển va đập"), moderatorToken, 200));
        assertThat(ruled.path("status").asText()).isEqualTo("RESOLVED");
        assertThat(ruled.path("ruling").path("refundAmount").asLong()).isEqualTo(80_000);
        assertThat(ruled.path("ruling").path("courierAmount").asLong()).isEqualTo(120_000);
        assertThat(ruled.path("ruling").path("appealDeadline").asText()).isEqualTo("2026-10-22T12:00:00Z");
        assertThat(data(send(get(PARTY + id).header("Authorization", "Bearer " + token), 200))
                .path("permissions").path("appeal").asBoolean()).isTrue();

        assertThat(lifecycle.closeExpiredAppealWindows()).isZero();
        travelTo(Instant.parse("2026-10-22T12:00:00Z"));
        assertThat(lifecycle.closeExpiredAppealWindows()).isEqualTo(1);
        write("POST", PARTY + id + "/appeal", Map.of("reason", "Muộn"), token, 409);

        Escrow escrow = escrows.findByOrderId(order.getId()).orElseThrow();
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.PARTIALLY_REFUNDED);
        assertThat(escrow.isFrozen()).isFalse();
        Wallet senderWallet = wallets.findByUserId(user.getId()).orElseThrow();
        assertThat(senderWallet.getEscrowHeld()).isZero();
        assertThat(senderWallet.getAvailable()).isEqualTo(80_000);
        assertThat(wallets.findByUserId(courier.getId()).orElseThrow().getAvailable()).isEqualTo(120_000);
        assertThat(ledger.findAll()).extracting(LedgerTransaction::getType, LedgerTransaction::getAmount)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(LedgerTransactionType.ESCROW_REFUND, 80_000L),
                        org.assertj.core.groups.Tuple.tuple(LedgerTransactionType.ESCROW_RELEASE, 120_000L));
        Order closedOrder = orders.findById(order.getId()).orElseThrow();
        assertThat(closedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        var closed = data(send(get(PARTY + id).header("Authorization", "Bearer " + token), 200));
        assertThat(closed.path("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.path("ruling").path("compensationAmount").asLong()).isEqualTo(100_000);
        assertThat(closed.path("permissions").path("message").asBoolean()).isFalse();
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(courier.getPublicId())
                && event.getEventKey().endsWith(":closed"));
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("COMPLAINT_RULED"));
    }

    @Test void appealIsReviewedByAnotherAdministratorWhoCanAmendTheRuling() throws Exception {
        Order order = order("FPT-KN-02", BigDecimal.ZERO);
        heldEscrow(order, 150_000);
        String id = open(order, user, ComplaintReason.NOT_DELIVERED, "Không nhận được hàng").getPublicId().toString();
        write("POST", MOD + id + "/claim", Map.of(), adminToken, 200);
        write("POST", MOD + id + "/ruling", ruling("SENDER_FAVOR", null, 50_000, "Không bảo hiểm"), adminToken, 400);
        write("POST", MOD + id + "/ruling", ruling("SENDER_FAVOR", null, 0, "Không có xác nhận giao hàng"), adminToken, 200);

        write("POST", PARTY + id + "/appeal", Map.of("reason", " "), courierToken, 400);
        var appealed = data(write("POST", PARTY + id + "/appeal", Map.of("reason", "Đã giao, có ảnh tại cửa"), courierToken, 200));
        assertThat(appealed.path("status").asText()).isEqualTo("APPEALED");
        write("POST", PARTY + id + "/appeal", Map.of("reason", "Lần hai"), token, 409);
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(reviewer.getPublicId())
                && event.getEventKey().endsWith(":appealed"));

        Map<String, Object> amend = Map.of("decision", "AMEND", "outcome", "COURIER_FAVOR", "note", "Ảnh giao hàng có định vị");
        write("POST", "/api/v1/admin/complaints/" + id + "/appeal-review", amend, adminToken, 403);
        write("POST", "/api/v1/admin/complaints/" + id + "/appeal-review", amend, moderatorToken, 403);
        write("POST", "/api/v1/admin/complaints/" + id + "/appeal-review", Map.of("decision", "AMEND", "note", "Thiếu"), reviewerToken, 400);
        var reviewed = data(write("POST", "/api/v1/admin/complaints/" + id + "/appeal-review", amend, reviewerToken, 200));
        assertThat(reviewed.path("status").asText()).isEqualTo("CLOSED");
        assertThat(reviewed.path("appeal").path("decision").asText()).isEqualTo("AMEND");
        assertThat(reviewed.path("ruling").path("courierAmount").asLong()).isEqualTo(150_000);

        assertThat(escrows.findByOrderId(order.getId()).orElseThrow().getStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(wallets.findByUserId(courier.getId()).orElseThrow().getAvailable()).isEqualTo(150_000);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getAvailable()).isZero();
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("COMPLAINT_APPEAL_REVIEWED"));
    }

    @Test void refundAfterReleaseIsClawedBackFromTheCourierUpToTheAvailableBalance() throws Exception {
        Order order = order("FPT-KN-03", new BigDecimal("300000"));
        Escrow escrow = heldEscrow(order, 100_000);
        escrow.setStatus(EscrowStatus.RELEASED);
        escrows.saveAndFlush(escrow);
        Wallet sender = wallets.findByUserId(user.getId()).orElseThrow();
        sender.setEscrowHeld(0L);
        wallets.saveAndFlush(sender);
        wallets.saveAndFlush(Wallet.builder().userId(courier.getId()).available(30_000L).escrowHeld(0L).pendingWithdraw(0L).build());

        String id = open(order, user, ComplaintReason.WRONG_ITEM, "Giao nhầm hàng").getPublicId().toString();
        assertThat(escrows.findByOrderId(order.getId()).orElseThrow().isFrozen()).isTrue();
        write("POST", MOD + id + "/claim", Map.of(), moderatorToken, 200);
        var ruled = data(write("POST", MOD + id + "/ruling", ruling("SENDER_FAVOR", null, 0, "Giao sai mặt hàng"), moderatorToken, 200));
        assertThat(ruled.path("ruling").path("refundAmount").asLong()).isEqualTo(100_000);
        assertThat(ruled.path("ruling").path("courierAmount").asLong()).isZero();
        travelTo(now.get().plus(Duration.ofHours(49)));
        lifecycle.closeExpiredAppealWindows();

        assertThat(wallets.findByUserId(courier.getId()).orElseThrow().getAvailable()).isZero();
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getAvailable()).isEqualTo(30_000);
        var closed = data(send(get(PARTY + id).header("Authorization", "Bearer " + token), 200));
        assertThat(closed.path("settlement").path("clawbackAmount").asLong()).isEqualTo(30_000);
        assertThat(closed.path("settlement").path("unrecoveredAmount").asLong()).isEqualTo(70_000);
        assertThat(ledger.findAll()).singleElement().satisfies(item -> assertThat(item.getType()).isEqualTo(LedgerTransactionType.COMPLAINT_CLAWBACK));
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(admin.getPublicId())
                && event.getEventKey().endsWith(":unrecovered"));
    }

    @Test void overdueComplaintsAlertStaffOnceAndPartiesCannotBeReviewers() throws Exception {
        Order order = order("FPT-KN-04", BigDecimal.ZERO);
        heldEscrow(order, 50_000);
        String id = open(order, courier, ComplaintReason.PAYMENT, "Người gửi từ chối nhận").getPublicId().toString();

        travelTo(now.get().plus(Duration.ofHours(25)));
        assertThat(lifecycle.alertOverdue()).isEqualTo(1);
        assertThat(lifecycle.alertOverdue()).isZero();
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(admin.getPublicId())
                && event.getEventKey().endsWith(":response-overdue"));
        var queue = data(send(get("/api/v1/mod/complaints").header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(queue.path("content").get(0).path("responseOverdue").asBoolean()).isTrue();

        User senderModerator = users.findById(user.getId()).orElseThrow();
        senderModerator.setRoles(new HashSet<>(Set.of(roles.findByCode("SENDER").orElseThrow(), roles.findByCode("MODERATOR").orElseThrow())));
        users.saveAndFlush(senderModerator);
        String senderModeratorToken = access(login(user.getEmail(), "OldPassword123!", 200));
        write("POST", MOD + id + "/claim", Map.of(), senderModeratorToken, 403);
        var asParty = data(send(get(PARTY + id).header("Authorization", "Bearer " + senderModeratorToken), 200));
        assertThat(asParty.path("viewerRole").asText()).isEqualTo("SENDER");
        assertThat(asParty.path("permissions").path("claim").asBoolean()).isFalse();

        assertThat(data(send(get("/api/v1/complaints").header("Authorization", "Bearer " + courierToken), 200))
                .path("totalElements").asInt()).isEqualTo(1);
    }

    /** Access token hết hạn theo đồng hồ giả lập nên đăng nhập lại sau khi tua thời gian. */
    private void travelTo(Instant instant) throws Exception {
        now.set(instant);
        token = access(login(user.getEmail(), "OldPassword123!", 200));
        courierToken = access(login(courier.getEmail(), "OldPassword123!", 200));
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
        reviewerToken = access(login(reviewer.getEmail(), "OldPassword123!", 200));
    }

    private Complaint open(Order order, User opener, ComplaintReason reason, String description) throws Exception {
        var photo = image("files", "png");
        return lifecycle.open(new OpenComplaintCommand(order.getId(), opener.getId(), reason, description,
                List.of(new com.fptpost.common.file.dto.ValidatedImage(photo.getBytes(), "image/png"))));
    }

    private Map<String, Object> ruling(String outcome, Integer percent, long compensation, String reason) {
        Map<String, Object> body = new HashMap<>(Map.of("outcome", outcome, "compensationAmount", compensation, "reason", reason));
        if (percent != null) body.put("senderPercent", percent);
        return body;
    }

    private MvcResult message(String bearer, String id, String body, MockMultipartFile file, int status) throws Exception {
        var request = multipart(PARTY + id + "/messages")
                .file(new MockMultipartFile("request", "", "application/json", json.writeValueAsBytes(Map.of("body", body))));
        if (file != null) request.file(file);
        return send(request.header("Authorization", "Bearer " + bearer), status);
    }

    private Order order(String code, BigDecimal declaredValue) {
        return orders.saveAndFlush(Order.builder().orderCode(code).senderId(user.getId()).courierId(courier.getId())
                .status(OrderStatus.IN_DELIVERY).pickupAddress("A").deliveryAddress("B").receiverName("Người Nhận")
                .receiverPhone("0901234567").productName("Hàng thử").quantity(1).weightKg(BigDecimal.ONE)
                .declaredValue(declaredValue).priceAgreed(new BigDecimal("40000")).build());
    }

    private Escrow heldEscrow(Order order, long amount) {
        wallets.saveAndFlush(Wallet.builder().userId(user.getId()).available(0L).escrowHeld(amount).pendingWithdraw(0L).build());
        return escrows.saveAndFlush(Escrow.builder().orderId(order.getId()).senderId(user.getId()).amount(amount)
                .status(EscrowStatus.HELD).paymentMethod(EscrowPaymentMethod.WALLET).frozen(false).build());
    }

    private User person(String prefix, Set<String> roleCodes, int offset) {
        int number = sequence.incrementAndGet();
        User result = createUser(prefix + number + "@example.invalid", "+843" + String.format(Locale.ROOT, "%08d", number + offset));
        Set<com.fptpost.modules.identity.model.Role> assigned = new HashSet<>();
        roleCodes.forEach(code -> assigned.add(roles.findByCode(code).orElseThrow()));
        result.setRoles(assigned);
        return users.saveAndFlush(result);
    }
}

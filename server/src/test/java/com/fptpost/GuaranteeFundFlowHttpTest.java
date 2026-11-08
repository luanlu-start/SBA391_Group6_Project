package com.fptpost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.modules.dispute.dto.request.OpenComplaintCommand;
import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import com.fptpost.modules.dispute.enums.ComplaintReason;
import com.fptpost.modules.dispute.enums.ComplaintRole;
import com.fptpost.modules.dispute.enums.ComplaintStatus;
import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.dispute.repository.ComplaintEvidenceRepository;
import com.fptpost.modules.dispute.repository.ComplaintMessageRepository;
import com.fptpost.modules.dispute.repository.ComplaintRepository;
import com.fptpost.modules.dispute.service.ComplaintLifecycle;
import com.fptpost.modules.guarantee.enums.CompensationStatus;
import com.fptpost.modules.guarantee.repository.CompensationRepository;
import com.fptpost.modules.guarantee.repository.GuaranteeFundRepository;
import com.fptpost.modules.guarantee.service.GuaranteeFundService;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class GuaranteeFundFlowHttpTest extends IdentityFeatureHttpTest {
    static final String FUND = "/api/v1/admin/guarantee-fund";
    @Autowired ComplaintLifecycle lifecycle;
    @Autowired GuaranteeFundService guaranteeFund;
    @Autowired ComplaintRepository complaints;
    @Autowired ComplaintMessageRepository complaintMessages;
    @Autowired ComplaintEvidenceRepository complaintEvidence;
    @Autowired CompensationRepository compensations;
    @Autowired GuaranteeFundRepository funds;
    @Autowired OrderRepository orders;
    @Autowired EscrowRepository escrows;
    @Autowired WalletRepository wallets;
    @Autowired LedgerTransactionRepository ledger;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired AuditOutboxRepository auditOutbox;
    User courier;
    User moderator;
    String moderatorToken;
    User admin;
    String adminToken;
    int orderSequence;

    @BeforeEach void setupFund() throws Exception {
        clean();
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll();
        courier = person("courier", Set.of("COURIER"), 71);
        moderator = person("moderator", Set.of("MODERATOR"), 72);
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        admin = person("admin", Set.of("ADMIN"), 73);
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
    }

    @AfterEach void clean() {
        compensations.deleteAll(); funds.deleteAll();
        complaintEvidence.deleteAll(); complaintMessages.deleteAll(); complaints.deleteAll();
        ledger.deleteAll(); escrows.deleteAll(); wallets.deleteAll(); orders.deleteAll();
    }

    @Test void approvedCompensationsWaitForTheFundAndArePaidInRulingOrder() throws Exception {
        Complaint first = closedWithCompensation(100_000);
        travelTo(now.get().plus(Duration.ofMinutes(5)));
        Complaint second = closedWithCompensation(30_000);
        assertThat(compensations.findAll()).hasSize(2).allMatch(item -> item.getStatus() == CompensationStatus.PENDING_REVIEW);
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(admin.getPublicId())
                && event.getEventKey().endsWith(":review"));

        var queued = data(decide(first, "APPROVE", null, null, 200));
        assertThat(queued.path("status").asText()).isEqualTo("QUEUED");
        assertThat(queued.path("queuePosition").asInt()).isEqualTo(1);
        assertThat(data(decide(second, "APPROVE", null, null, 200)).path("queuePosition").asInt()).isEqualTo(2);
        decide(second, "APPROVE", null, null, 409);

        contribute(50_000);
        assertThat(compensations.findAll()).allMatch(item -> item.getStatus() == CompensationStatus.QUEUED);
        contribute(60_000);
        assertThat(status(first)).isEqualTo(CompensationStatus.PAID);
        assertThat(status(second)).isEqualTo(CompensationStatus.QUEUED);
        var overview = data(send(get(FUND).header("Authorization", "Bearer " + adminToken), 200));
        assertThat(overview.path("balance").asLong()).isEqualTo(10_000);
        assertThat(overview.path("queuedCount").asLong()).isEqualTo(1);
        assertThat(overview.path("paidAmount").asLong()).isEqualTo(100_000);
        contribute(20_000);
        assertThat(status(second)).isEqualTo(CompensationStatus.PAID);

        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getAvailable()).isEqualTo(120_000 + 130_000);
        assertThat(funds.ledgerBalance("GUARANTEE_FUND")).isZero();
        assertThat(ledger.findAll()).filteredOn(item -> item.getType() == LedgerTransactionType.GUARANTEE_FUND_PAYOUT)
                .extracting(LedgerTransaction::getAmount).containsExactlyInAnyOrder(100_000L, 30_000L);
        var paid = data(send(get(FUND + "/compensations?status=PAID").header("Authorization", "Bearer " + adminToken), 200));
        assertThat(paid.path("totalElements").asInt()).isEqualTo(2);
        assertThat(paid.path("content").get(0).path("complaintCode").asText()).isEqualTo(first.getCode());
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(user.getPublicId())
                && event.getEventKey().endsWith(":paid"));
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("GUARANTEE_FUND_CONTRIBUTED"));
    }

    @Test void adminsReviewWithinTheRulingAmountAndPayImmediatelyWhenTheFundCovers() throws Exception {
        contribute(500_000);
        Complaint complaint = closedWithCompensation(200_000);
        decide(complaint, "APPROVE", 250_000L, null, 400);
        decide(complaint, "REJECT", null, " ", 400);
        send(get(FUND).header("Authorization", "Bearer " + moderatorToken), 403);
        send(get(FUND + "/compensations?status=WRONG").header("Authorization", "Bearer " + adminToken), 400);

        var paid = data(decide(complaint, "APPROVE", 150_000L, "Khấu hao 25%", 200));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("approvedAmount").asLong()).isEqualTo(150_000);
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getAvailable()).isEqualTo(60_000 + 150_000);
        assertThat(funds.findById(1L).orElseThrow().getBalance()).isEqualTo(350_000);

        Complaint rejected = closedWithCompensation(80_000);
        var outcome = data(decide(rejected, "REJECT", null, "Ảnh hư hỏng có trước khi giao", 200));
        assertThat(outcome.path("status").asText()).isEqualTo("REJECTED");
        assertThat(funds.findById(1L).orElseThrow().getBalance()).isEqualTo(350_000);
    }

    @Test void complaintsClosedBeforeTheFundExistedAreRegisteredOnceAndBeneficiariesCannotApproveThemselves() throws Exception {
        Order order = order(new BigDecimal("400000"));
        Instant ruledAt = now.get().minus(Duration.ofDays(3));
        Complaint legacy = complaints.saveAndFlush(Complaint.builder().publicId(UUID.randomUUID()).code("KN-LEGACY1").orderId(order.getId())
                .openedBy(user.getId()).openerRole(ComplaintRole.SENDER).againstUserId(courier.getId()).reason(ComplaintReason.LOST)
                .status(ComplaintStatus.CLOSED).firstResponseDueAt(ruledAt).resolveDueAt(ruledAt).outcome(ComplaintOutcome.SENDER_FAVOR)
                .refundAmount(0L).courierAmount(0L).compensationAmount(120_000L).ruledAt(ruledAt).closedAt(ruledAt)
                .createdAt(ruledAt).updatedAt(ruledAt).build());
        assertThat(guaranteeFund.registerMissing()).isEqualTo(1);
        assertThat(guaranteeFund.registerMissing()).isZero();

        User senderAdmin = users.findById(user.getId()).orElseThrow();
        senderAdmin.setRoles(new HashSet<>(Set.of(roles.findByCode("SENDER").orElseThrow(), roles.findByCode("ADMIN").orElseThrow())));
        users.saveAndFlush(senderAdmin);
        String senderAdminToken = access(login(user.getEmail(), "OldPassword123!", 200));
        write("POST", "/api/v1/admin/complaints/" + legacy.getPublicId() + "/compensation",
                Map.of("decision", "APPROVE"), senderAdminToken, 403);
        assertThat(data(decide(legacy, "APPROVE", null, null, 200)).path("status").asText()).isEqualTo("QUEUED");
    }

    private Complaint closedWithCompensation(long compensation) throws Exception {
        Order order = order(new BigDecimal("500000"));
        wallets.findByUserId(user.getId()).ifPresentOrElse(wallet -> { wallet.setEscrowHeld(wallet.getEscrowHeld() + 60_000); wallets.saveAndFlush(wallet); },
                () -> wallets.saveAndFlush(Wallet.builder().userId(user.getId()).available(0L).escrowHeld(60_000L).pendingWithdraw(0L).build()));
        escrows.saveAndFlush(Escrow.builder().orderId(order.getId()).senderId(user.getId()).amount(60_000L)
                .status(EscrowStatus.HELD).paymentMethod(EscrowPaymentMethod.WALLET).frozen(false).build());
        Complaint complaint = lifecycle.open(new OpenComplaintCommand(order.getId(), user.getId(), ComplaintReason.DAMAGED,
                "Hàng vỡ", List.of(new ValidatedImage(image("files", "png").getBytes(), "image/png"))));
        String id = complaint.getPublicId().toString();
        write("POST", "/api/v1/mod/complaints/" + id + "/claim", Map.of(), moderatorToken, 200);
        write("POST", "/api/v1/mod/complaints/" + id + "/ruling", Map.of("outcome", "SENDER_FAVOR",
                "compensationAmount", compensation, "reason", "Hàng vỡ do vận chuyển"), moderatorToken, 200);
        Instant back = now.get();
        now.set(back.plus(Duration.ofHours(49)));
        lifecycle.closeExpiredAppealWindows();
        travelTo(back);
        return complaints.findById(complaint.getId()).orElseThrow();
    }

    private CompensationStatus status(Complaint complaint) {
        return compensations.findAll().stream().filter(item -> item.getComplaintId().equals(complaint.getId()))
                .findFirst().orElseThrow().getStatus();
    }

    private org.springframework.test.web.servlet.MvcResult decide(Complaint complaint, String decision, Long amount, String note,
                                                                  int status) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("decision", decision));
        if (amount != null) body.put("amount", amount);
        if (note != null) body.put("note", note);
        return write("POST", "/api/v1/admin/complaints/" + complaint.getPublicId() + "/compensation", body, adminToken, status);
    }

    private JsonNode contribute(long amount) throws Exception {
        return data(write("POST", FUND + "/contributions", Map.of("amount", amount, "note", "Trích phí bảo hiểm tháng 10"), adminToken, 200));
    }

    private void travelTo(Instant instant) throws Exception {
        now.set(instant);
        token = access(login(user.getEmail(), "OldPassword123!", 200));
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
    }

    private Order order(BigDecimal declaredValue) {
        return orders.saveAndFlush(Order.builder().orderCode("FPT-GF-" + (++orderSequence)).senderId(user.getId()).courierId(courier.getId())
                .status(OrderStatus.IN_DELIVERY).pickupAddress("A").deliveryAddress("B").receiverName("Người Nhận")
                .receiverPhone("0901234567").productName("Hàng thử").quantity(1).weightKg(BigDecimal.ONE)
                .declaredValue(declaredValue).priceAgreed(new BigDecimal("40000")).build());
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

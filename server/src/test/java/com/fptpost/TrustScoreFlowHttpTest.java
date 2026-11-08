package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.modules.dispute.dto.request.OpenComplaintCommand;
import com.fptpost.modules.dispute.enums.ComplaintReason;
import com.fptpost.modules.dispute.repository.ComplaintEvidenceRepository;
import com.fptpost.modules.dispute.repository.ComplaintMessageRepository;
import com.fptpost.modules.dispute.repository.ComplaintRepository;
import com.fptpost.modules.dispute.service.ComplaintLifecycle;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.VehicleType;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.reputation.enums.TrustScoreReason;
import com.fptpost.modules.reputation.port.CourierRatingSource;
import com.fptpost.modules.reputation.port.DeliveryPunctualitySource;
import com.fptpost.modules.reputation.repository.CourierCancellationRepository;
import com.fptpost.modules.reputation.repository.TrustScoreHistoryRepository;
import com.fptpost.modules.reputation.repository.TrustScoreRepository;
import com.fptpost.modules.reputation.service.TrustScoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class TrustScoreFlowHttpTest extends IdentityFeatureHttpTest {
    @MockitoBean CourierRatingSource ratings;
    @MockitoBean DeliveryPunctualitySource punctuality;
    @Autowired TrustScoreService trustScores;
    @Autowired TrustScoreRepository scores;
    @Autowired TrustScoreHistoryRepository scoreHistory;
    @Autowired CourierCancellationRepository cancellations;
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
    User admin;
    String adminToken;
    int orderSequence;

    @BeforeEach void setupTrust() throws Exception {
        clean();
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll();
        courier = person("courier", Set.of("COURIER"), 81);
        courierProfile(courier);
        courierToken = access(login(courier.getEmail(), "OldPassword123!", 200));
        admin = person("admin", Set.of("ADMIN"), 82);
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
    }

    @AfterEach void clean() {
        scoreHistory.deleteAll(); scores.deleteAll(); cancellations.deleteAll();
        complaintEvidence.deleteAll(); complaintMessages.deleteAll(); complaints.deleteAll();
        ledger.deleteAll(); escrows.deleteAll(); wallets.deleteAll(); orders.deleteAll(); couriers.deleteAll();
    }

    @Test void newCouriersStartAtTheInitialScoreAndClimbTiersWithDeliveredWork() throws Exception {
        var fresh = data(send(get("/api/v1/me/trust").header("Authorization", "Bearer " + courierToken), 200));
        assertThat(fresh.path("score").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(fresh.path("tier").asText()).isEqualTo("NEW");
        assertThat(fresh.path("nextTier").path("tier").asText()).isEqualTo("BRONZE");
        assertThat(fresh.path("nextTier").path("minOrders").asLong()).isEqualTo(5);
        assertThat(fresh.path("components").get(0).path("hasData").asBoolean()).isFalse();
        send(get("/api/v1/me/trust").header("Authorization", "Bearer " + token), 404);

        completedOrders(25);
        trustScores.recalculate(courier.getId(), TrustScoreReason.ORDER_COMPLETED);
        CourierProfile bronze = couriers.findById(courier.getId()).orElseThrow();
        assertThat(bronze.getTrustScore()).isEqualByComparingTo("64.58");
        assertThat(bronze.getTier()).isEqualTo(CourierTier.BRONZE);
        assertThat(bronze.getCompletedOrders()).isEqualTo(25);

        when(ratings.summarize(any())).thenReturn(Map.of(courier.getId(), new CourierRatingSource.RatingSummary(20, new BigDecimal("4.80"))));
        when(punctuality.summarize(any())).thenReturn(Map.of(courier.getId(), new DeliveryPunctualitySource.Punctuality(25, 24)));
        trustScores.recalculate(courier.getId(), TrustScoreReason.RATING);
        var silver = data(send(get("/api/v1/me/trust").header("Authorization", "Bearer " + courierToken), 200));
        assertThat(silver.path("score").decimalValue()).isEqualByComparingTo("88.80");
        assertThat(silver.path("tier").asText()).isEqualTo("SILVER");
        assertThat(silver.path("benefits").path("canAcceptHighValue").asBoolean()).isTrue();
        assertThat(silver.path("nextTier").path("tier").asText()).isEqualTo("GOLD");
        assertThat(silver.path("components").get(1).path("hasData").asBoolean()).isTrue();
        assertThat(silver.path("history")).hasSize(2);
        assertThat(silver.path("history").get(0).path("previousTier").asText()).isEqualTo("BRONZE");
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(courier.getPublicId())
                && event.getEventKey().startsWith("trust:tier:SILVER"));
    }

    @Test void repeatedCancellationsCostPointsSuspendNewOrdersAndAskAdminsToReview() throws Exception {
        for (long order = 1; order <= 4; order++) trustScores.recordCourierCancellation(courier.getId(), 9_000 + order);
        trustScores.recordCourierCancellation(courier.getId(), 9_004L);
        CourierProfile suspended = couriers.findById(courier.getId()).orElseThrow();
        assertThat(suspended.getRestrictedUntil()).isEqualTo(now.get().plus(Duration.ofDays(3)));
        assertThat(cancellations.count()).isEqualTo(4);
        assertThat(suspended.getTrustScore()).isLessThan(new BigDecimal("30"));
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(courier.getPublicId())
                && event.getEventKey().startsWith("trust:cancel-ban:"));

        for (long order = 5; order <= 6; order++) trustScores.recordCourierCancellation(courier.getId(), 9_000 + order);
        assertThat(couriers.findById(courier.getId()).orElseThrow().getTrustScore()).isLessThan(new BigDecimal("20"));
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(admin.getPublicId())
                && event.getEventKey().contains(":review:"));

        var review = data(send(get("/api/v1/admin/trust").header("Authorization", "Bearer " + adminToken), 200));
        assertThat(review.path("totalElements").asInt()).isEqualTo(1);
        assertThat(review.path("content").get(0).path("flaggedForReview").asBoolean()).isTrue();
        send(get("/api/v1/admin/trust").header("Authorization", "Bearer " + courierToken), 403);
        send(get("/api/v1/admin/trust?filter=WRONG").header("Authorization", "Bearer " + adminToken), 400);

        write("POST", "/api/v1/admin/trust/" + courier.getPublicId() + "/restrict", Map.of("days", 31, "reason", "Quá dài"), adminToken, 400);
        var restricted = data(write("POST", "/api/v1/admin/trust/" + courier.getPublicId() + "/restrict",
                Map.of("days", 7, "reason", "Hủy đơn liên tục"), adminToken, 200));
        assertThat(restricted.path("restrictedUntil").asText()).isEqualTo(now.get().plus(Duration.ofDays(7)).toString());
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("COURIER_RESTRICTED"));
        assertThat(data(write("POST", "/api/v1/admin/trust/recalculate", Map.of(), adminToken, 200)).path("couriers").asInt()).isEqualTo(1);
        assertThat(couriers.findById(courier.getId()).orElseThrow().getRestrictedUntil()).isEqualTo(now.get().plus(Duration.ofDays(7)));
    }

    @Test void complaintsLostByTheCourierLowerTheScoreOnceTheyClose() throws Exception {
        completedOrders(10);
        trustScores.recalculate(courier.getId(), TrustScoreReason.NIGHTLY);
        BigDecimal before = couriers.findById(courier.getId()).orElseThrow().getTrustScore();

        Order disputed = order(OrderStatus.IN_DELIVERY);
        User moderator = person("moderator", Set.of("MODERATOR"), 83);
        String moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        String id = lifecycle.open(new OpenComplaintCommand(disputed.getId(), user.getId(), ComplaintReason.LOST, "Mất hàng",
                List.of(new ValidatedImage(image("files", "png").getBytes(), "image/png")))).getPublicId().toString();
        write("POST", "/api/v1/mod/complaints/" + id + "/claim", Map.of(), moderatorToken, 200);
        write("POST", "/api/v1/mod/complaints/" + id + "/ruling",
                Map.of("outcome", "SENDER_FAVOR", "compensationAmount", 0, "reason", "Không có bằng chứng giao"), moderatorToken, 200);
        now.set(now.get().plus(Duration.ofHours(49)));
        lifecycle.closeExpiredAppealWindows();

        var score = scores.findById(courier.getId()).orElseThrow();
        assertThat(score.getLostComplaints()).isEqualTo(1);
        assertThat(score.getCompletedOrders()).isEqualTo(11);
        assertThat(score.getScore()).isLessThan(before.add(new BigDecimal("1")));
        assertThat(scoreHistory.findTop20ByCourierIdOrderByCalculatedAtDescIdDesc(courier.getId()).getFirst().getReason())
                .isEqualTo(TrustScoreReason.COMPLAINT_CLOSED);
    }

    private void completedOrders(int count) {
        for (int i = 0; i < count; i++) order(OrderStatus.COMPLETED);
    }

    private Order order(OrderStatus status) {
        return orders.saveAndFlush(Order.builder().orderCode("FPT-TR-" + (++orderSequence)).senderId(user.getId()).courierId(courier.getId())
                .status(status).pickupAddress("A").deliveryAddress("B").receiverName("Người Nhận").receiverPhone("0901234567")
                .productName("Hàng thử").quantity(1).weightKg(BigDecimal.ONE).declaredValue(BigDecimal.ZERO).build());
    }

    private void courierProfile(User owner) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> couriers.saveAndFlush(CourierProfile.builder()
                .user(users.findById(owner.getId()).orElseThrow()).vehicleType(VehicleType.MOTORBIKE).areas(new ArrayList<>(List.of("Quận 1")))
                .availableHours(new ArrayList<>()).tier(CourierTier.NEW).trustScore(new BigDecimal("50.00")).completedOrders(0)
                .commitmentVersion("test").registeredAt(now.get()).build()));
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

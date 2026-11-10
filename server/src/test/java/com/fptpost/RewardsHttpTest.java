package com.fptpost;

import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.jobs.enums.JobRunStatus;
import com.fptpost.modules.jobs.enums.JobTrigger;
import com.fptpost.modules.jobs.service.JobRunner;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.Escrow;
import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.service.LedgerService;
import com.fptpost.modules.rewards.model.Referral;
import com.fptpost.modules.rewards.repository.ReferralRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rewards;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "app.jobs.enabled=false"
})
class RewardsHttpTest extends IdentityFeatureHttpTest {
    @Autowired RegistrationProperties registration;
    @Autowired OrderRepository orders;
    @Autowired EscrowRepository escrows;
    @Autowired WalletRepository wallets;
    @Autowired LedgerService ledger;
    @Autowired JobRunner jobs;
    @Autowired SystemParameterRepository parameters;
    @Autowired ReferralRepository referrals;

    @Test
    void creditsBothAccountsOnceAndAwardsWeeklyBadgeAfterConfirmedDelivery() throws Exception {
        send(get("/api/v1/me/referral"), 401);
        send(get("/api/v1/me/badges"), 401);
        var initial = data(send(get("/api/v1/me/referral").header("Authorization", "Bearer " + token), 200));
        String code = initial.path("code").asText();
        assertThat(code).matches("[A-F0-9]{32}");
        assertThat(initial.path("referredBy").isNull()).isTrue();
        assertThat(initial.path("invitees").path("content")).isEmpty();
        send(get("/api/v1/me/referral").param("page", "-1").header("Authorization", "Bearer " + token), 400);
        send(get("/api/v1/me/referral").param("size", "101").header("Authorization", "Bearer " + token), 400);

        Map<String, Object> input = new HashMap<>(Map.of(
                "fullName", "Người được mời", "email", "invited@example.com", "phone", "0912345678",
                "password", "OldPassword123!", "adultConfirmed", true, "termsAccepted", true,
                "privacyAccepted", true, "termsVersion", registration.getTermsVersion(),
                "privacyVersion", registration.getPrivacyVersion()));
        input.put("referralCode", UUID.randomUUID().toString().replace("-", ""));
        write("POST", "/api/v1/auth/register", input, null, 400);
        assertThat(delivered).isEmpty();

        input.put("referralCode", code.toLowerCase(Locale.ROOT));
        var registered = data(write("POST", "/api/v1/auth/register", input, null, 201));
        assertThat(registered.path("referralCode").asText()).isEqualTo(code);
        var invitee = users.findByPublicId(UUID.fromString(registered.path("userId").asText())).orElseThrow();
        assertThat(data(send(get("/api/v1/me/referral").header("Authorization", "Bearer " + token), 200))
                .path("pending").asLong()).isEqualTo(1);
        var waiting = send(get("/api/v1/me/referral").header("Authorization", "Bearer " + token), 200);
        var pendingInvitee = data(waiting).path("invitees").path("content").get(0);
        assertThat(pendingInvitee.path("userId").asText()).isEqualTo(invitee.getPublicId().toString());
        assertThat(pendingInvitee.path("fullName").asText()).isEqualTo("Người được mời");
        assertThat(pendingInvitee.path("maskedEmail").asText()).isEqualTo("i***@example.com");
        assertThat(pendingInvitee.path("status").asText()).isEqualTo("WAITING_VERIFICATION");
        assertThat(waiting.getResponse().getContentAsString()).doesNotContain("invited@example.com", "0912345678");

        write("POST", "/api/v1/auth/otp/verify",
                Map.of("challengeId", registered.path("challengeId").asText(), "otp", otp()), null, 200);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            users.findById(invitee.getId()).orElseThrow().getRoles().add(roles.findByCode("COURIER").orElseThrow());
            parameters.findByKey("WEEKLY_DELIVERY_TARGET").orElseThrow().setValue("1");
            parameters.findByKey("WEEKLY_DELIVERY_REWARD").orElseThrow().setValue("2000");
        });
        String invitedToken = access(login("invited@example.com", "OldPassword123!", 200));
        mvc.perform(get("/api/v1/me/referral").header("Authorization", "Bearer " + invitedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referredBy.userId").value(user.getPublicId().toString()))
                .andExpect(jsonPath("$.data.referredBy.fullName").value("Người Dùng"))
                .andExpect(jsonPath("$.data.referredBy.referralCode").value(code))
                .andExpect(jsonPath("$.data.referredBy.status").value("WAITING_FIRST_ORDER"))
                .andExpect(jsonPath("$.data.invitees.content").isEmpty());

        now.updateAndGet(value -> value.plusSeconds(60));
        saveOrder("REWARD-REFUND", OrderStatus.COMPLETED, EscrowStatus.REFUNDED, invitee.getId());
        saveOrder("REWARD-HELD", OrderStatus.IN_DELIVERY, EscrowStatus.HELD, invitee.getId());
        jobs.ensureLockRows();
        assertThat(runRewards()).isZero();
        assertThat(wallets.findByUserId(invitee.getId())).isEmpty();

        Order deliveredOrder = saveOrder("REWARD-DELIVERED", OrderStatus.IN_DELIVERY, EscrowStatus.RELEASED, invitee.getId());
        ledger.recordEscrowRelease(deliveredOrder.getId(), user.getId(), invitee.getId(), 30_000L,
                "ESCROW_ORD_" + deliveredOrder.getId());
        assertThat(runRewards()).isEqualTo(2);
        assertThat(runRewards()).isZero();
        assertThat(wallets.findByUserId(user.getId()).orElseThrow().getAvailable()).isEqualTo(10_000L);
        assertThat(wallets.findByUserId(invitee.getId()).orElseThrow().getAvailable()).isEqualTo(12_000L);

        mvc.perform(get("/api/v1/me/referral").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invited").value(1))
                .andExpect(jsonPath("$.data.rewarded").value(1))
                .andExpect(jsonPath("$.data.earnedAmount").value(10000))
                .andExpect(jsonPath("$.data.invitees.content[0].status").value("REWARDED"))
                .andExpect(jsonPath("$.data.invitees.content[0].rewardAmount").value(10000));
        mvc.perform(get("/api/v1/me/referral").header("Authorization", "Bearer " + invitedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referredBy.status").value("REWARDED"))
                .andExpect(jsonPath("$.data.referredBy.rewardAmount").value(10000))
                .andExpect(jsonPath("$.data.earnedAmount").value(10000));
        mvc.perform(get("/api/v1/me/badges").header("Authorization", "Bearer " + invitedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyDeliveries").value(1))
                .andExpect(jsonPath("$.data.badges[0].code").value("WEEKLY_DELIVERIES"))
                .andExpect(jsonPath("$.data.badges[0].rewardAmount").value(2000));
        mvc.perform(get("/api/v1/wallet/transactions").param("type", "REFERRAL_REWARD")
                        .header("Authorization", "Bearer " + invitedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].balanceDirection").value("IN"));

        var otherUser = createUser("other@example.com", "+84912345679");
        String otherToken = access(login(otherUser.getEmail(), "OldPassword123!", 200));
        mvc.perform(get("/api/v1/me/referral").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referredBy").doesNotExist())
                .andExpect(jsonPath("$.data.invitees.content").isEmpty());

        // Each invitee can only belong to one inviter, including across concurrent requests.
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                referrals.saveAndFlush(Referral.builder().inviter(otherUser).invitee(invitee)
                        .createdAt(now.get()).build())))
                .isInstanceOf(DataIntegrityViolationException.class);
        // The same invitation code can still be used by multiple different accounts.
        referrals.saveAndFlush(Referral.builder().inviter(user).invitee(otherUser).createdAt(now.get()).build());
        mvc.perform(get("/api/v1/me/referral").param("size", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invitees.totalElements").value(2))
                .andExpect(jsonPath("$.data.invitees.totalPages").value(2))
                .andExpect(jsonPath("$.data.invitees.content.length()").value(1));
        mvc.perform(get("/api/v1/me/referral").header("Authorization", "Bearer " + invitedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referredBy.userId").value(user.getPublicId().toString()));
    }

    private int runRewards() {
        var run = jobs.run(jobs.job("gamification-rewards"), JobTrigger.MANUAL, user.getId());
        assertThat(run.getStatus()).isEqualTo(JobRunStatus.SUCCESS);
        return run.getProcessed();
    }

    private Order saveOrder(String code, OrderStatus status, EscrowStatus escrowStatus, Long courierId) {
        Order order = orders.save(Order.builder().orderCode(code).senderId(user.getId()).courierId(courierId)
                .pickupAddress("Cần Thơ").deliveryAddress("Vĩnh Long").receiverName("Người nhận")
                .receiverPhone("0911111111").productName("Kiện hàng").quantity(1).weightKg(BigDecimal.ONE)
                .status(status).createdAt(now.get()).build());
        escrows.save(Escrow.builder().orderId(order.getId()).senderId(user.getId()).amount(30_000L)
                .paymentMethod(EscrowPaymentMethod.VNPAY).status(escrowStatus)
                .releaseAt(escrowStatus == EscrowStatus.RELEASED ? now.get() : null).build());
        return order;
    }
}

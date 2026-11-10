package com.fptpost;

import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.service.LedgerService;
import com.fptpost.modules.rewards.repository.ReferralRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:referralapplication;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "app.jobs.enabled=false"
})
class ReferralApplicationHttpTest extends IdentityFeatureHttpTest {
    @Autowired ReferralRepository referrals;
    @Autowired OrderRepository orders;
    @Autowired LedgerService ledger;

    @Test
    void acceptsOneReferralBeforeFirstCompletionAndKeepsConcurrentClaimsConsistent() throws Exception {
        var inviter = createUser("referrer@example.com", "+84922345678");
        var secondInviter = createUser("second-referrer@example.com", "+84922345679");
        String code = inviter.getPublicId().toString().replace("-", "").toUpperCase(Locale.ROOT);
        String otherCode = secondInviter.getPublicId().toString().replace("-", "").toUpperCase(Locale.ROOT);

        mvc.perform(get("/api/v1/me/referral").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.eligibility").value("ELIGIBLE"));
        write("POST", "/api/v1/me/referral", Map.of("referralCode", code), null, 401);
        write("POST", "/api/v1/me/referral", Map.of("referralCode", ""), token, 400);
        write("POST", "/api/v1/me/referral", Map.of("referralCode", "invalid"), token, 400);
        write("POST", "/api/v1/me/referral",
                Map.of("referralCode", user.getPublicId().toString().replace("-", "")), token, 400);
        write("POST", "/api/v1/me/referral",
                Map.of("referralCode", UUID.randomUUID().toString().replace("-", "")), token, 400);

        new TransactionTemplate(transactions).executeWithoutResult(transaction ->
                users.findById(secondInviter.getId()).orElseThrow().setStatus(UserStatus.LOCKED));
        write("POST", "/api/v1/me/referral", Map.of("referralCode", otherCode), token, 400);
        new TransactionTemplate(transactions).executeWithoutResult(transaction ->
                users.findById(secondInviter.getId()).orElseThrow().setStatus(UserStatus.ACTIVE));

        var accepted = data(write("POST", "/api/v1/me/referral",
                Map.of("referralCode", "  " + code.toLowerCase(Locale.ROOT) + "  "), token, 200));
        assertThat(accepted.path("userId").asText()).isEqualTo(inviter.getPublicId().toString());
        assertThat(accepted.path("referralCode").asText()).isEqualTo(code);
        write("POST", "/api/v1/me/referral", Map.of("referralCode", code), token, 200);
        var changed = body(write("POST", "/api/v1/me/referral", Map.of("referralCode", otherCode), token, 409));
        assertThat(changed.path("code").asText()).isEqualTo("AUTH_REFERRAL_ALREADY_RECORDED");
        assertThat(referrals.count()).isEqualTo(1);
        mvc.perform(get("/api/v1/me/referral").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligibility").value("ALREADY_RECORDED"))
                .andExpect(jsonPath("$.data.referredBy.referralCode").value(code));

        var completedUser = createUser("completed@example.com", "+84922345680");
        saveOrder("COMPLETED-BEFORE-REFERRAL", completedUser.getId(), null, OrderStatus.COMPLETED);
        String completedToken = access(login(completedUser.getEmail(), "OldPassword123!", 200));
        var tooLate = body(write("POST", "/api/v1/me/referral", Map.of("referralCode", code), completedToken, 409));
        assertThat(tooLate.path("code").asText()).isEqualTo("AUTH_REFERRAL_TOO_LATE");
        mvc.perform(get("/api/v1/me/referral").header("Authorization", "Bearer " + completedToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.eligibility").value("FIRST_ORDER_COMPLETED"));

        var releasedUser = createUser("released@example.com", "+84922345681");
        Order released = saveOrder("RELEASED-BEFORE-REFERRAL", inviter.getId(), releasedUser.getId(), OrderStatus.IN_DELIVERY);
        ledger.recordEscrowRelease(released.getId(), inviter.getId(), releasedUser.getId(), 30_000L,
                "ESCROW_ORD_" + released.getId());
        String releasedToken = access(login(releasedUser.getEmail(), "OldPassword123!", 200));
        write("POST", "/api/v1/me/referral", Map.of("referralCode", code), releasedToken, 409);

        // Finishing a first order must still allow safe retries of the code already recorded.
        saveOrder("COMPLETED-AFTER-REFERRAL", user.getId(), null, OrderStatus.COMPLETED);
        write("POST", "/api/v1/me/referral", Map.of("referralCode", code), token, 200);
        assertThat(referrals.count()).isEqualTo(1);

        var concurrentUser = createUser("concurrent@example.com", "+84922345682");
        String concurrentToken = access(login(concurrentUser.getEmail(), "OldPassword123!", 200));
        assertThat(applyConcurrently(concurrentToken, List.of(code, otherCode))).containsExactlyInAnyOrder(200, 409);
        assertThat(referrals.count()).isEqualTo(2);
        var recorded = data(send(get("/api/v1/me/referral")
                .header("Authorization", "Bearer " + concurrentToken), 200));
        assertThat(recorded.path("eligibility").asText()).isEqualTo("ALREADY_RECORDED");
        assertThat(recorded.path("referredBy").path("referralCode").asText()).isIn(code, otherCode);

        var retryUser = createUser("retry@example.com", "+84922345683");
        String retryToken = access(login(retryUser.getEmail(), "OldPassword123!", 200));
        assertThat(applyConcurrently(retryToken, List.of(code, code))).containsExactly(200, 200);
        assertThat(referrals.count()).isEqualTo(3);
    }

    private List<Integer> applyConcurrently(String bearer, List<String> codes) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = codes.stream().map(candidate -> executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Requests did not start");
                return mvc.perform(post("/api/v1/me/referral")
                                .header("Authorization", "Bearer " + bearer)
                                .contentType("application/json")
                                .content(json.writeValueAsBytes(Map.of("referralCode", candidate))))
                        .andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            return List.of(requests.get(0).get(20, TimeUnit.SECONDS), requests.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    private Order saveOrder(String code, Long senderId, Long courierId, OrderStatus status) {
        return orders.save(Order.builder().orderCode(code).senderId(senderId).courierId(courierId)
                .pickupAddress("Cần Thơ").deliveryAddress("Vĩnh Long").receiverName("Người nhận")
                .receiverPhone("0911111111").productName("Kiện hàng").quantity(1).weightKg(BigDecimal.ONE)
                .status(status).createdAt(now.get()).build());
    }
}

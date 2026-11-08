package com.fptpost;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.jobs.repository.ScheduledJobLockRepository;
import com.fptpost.modules.jobs.repository.ScheduledJobRunRepository;
import com.fptpost.modules.jobs.service.BackgroundJob;
import com.fptpost.modules.jobs.service.JobResult;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ScheduledJobFlowHttpTest extends IdentityFeatureHttpTest {
    static final String JOBS = "/api/v1/admin/jobs";
    @Autowired OrderRepository orders;
    @Autowired ScheduledJobLockRepository locks;
    @Autowired ScheduledJobRunRepository runs;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    User admin;
    String adminToken;

    @TestConfiguration
    static class FailingJobConfiguration {
        @Bean BackgroundJob failingJob() {
            return new BackgroundJob() {
                public String name() { return "always-fails"; }
                public String title() { return "Tác vụ lỗi thử nghiệm"; }
                public String description() { return "Luôn ném lỗi"; }
                public Duration interval() { return Duration.ofHours(1); }
                public JobResult execute(Instant now) { throw new IllegalStateException("Kho dữ liệu không phản hồi"); }
            };
        }
    }

    @AfterEach void cleanJobs() {
        orders.deleteAll(); runs.deleteAll();
        locks.findAll().forEach(lock -> { lock.setLockedUntil(null); lock.setLastStartedAt(null); locks.save(lock); });
    }

    @BeforeEach void setupJobs() throws Exception {
        deliveries.deleteAll(); notifications.deleteAll();
        int number = sequence.incrementAndGet();
        admin = createUser("admin" + number + "@example.invalid", "+842" + String.format(Locale.ROOT, "%08d", number + 61));
        admin.setRoles(new HashSet<>(Set.of(roles.findByCode("ADMIN").orElseThrow())));
        admin = users.saveAndFlush(admin);
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
    }

    @Test void orderExpiryMovesStaleOpenOrdersOnlyOnce() throws Exception {
        Order stale = orders.saveAndFlush(order("FPT-STALE", now.get().minus(Duration.ofHours(36))));
        Order fresh = orders.saveAndFlush(order("FPT-FRESH", now.get().minus(Duration.ofHours(2))));
        var list = data(send(get(JOBS).header("Authorization", "Bearer " + adminToken), 200));
        assertThat(list.findValuesAsText("name")).contains("order-expiry", "offer-expiry", "invitation-expiry", "job-run-cleanup");

        var first = data(write("POST", JOBS + "/order-expiry/run", Map.of(), adminToken, 200));
        assertThat(first.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(first.path("processed").asInt()).isEqualTo(1);
        assertThat(orders.findById(stale.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(orders.findById(fresh.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.FINDING_SHIPPER);
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(user.getPublicId())
                && event.getEventKey().equals("order-expired:" + stale.getId()));

        var second = data(write("POST", JOBS + "/order-expiry/run", Map.of(), adminToken, 200));
        assertThat(second.path("processed").asInt()).isZero();
        var history = data(send(get(JOBS + "/order-expiry/runs").header("Authorization", "Bearer " + adminToken), 200));
        assertThat(history.path("totalElements").asInt()).isEqualTo(2);
        assertThat(history.path("content").get(0).path("trigger").asText()).isEqualTo("MANUAL");
    }

    @Test void runningJobsAreNotStartedTwiceAndFailuresAreRetriedThenReported() throws Exception {
        var lock = locks.findById("offer-expiry").orElseThrow();
        lock.setLockedUntil(now.get().plus(Duration.ofMinutes(5)));
        lock.setLockedBy("other-instance");
        locks.saveAndFlush(lock);
        assertThat(body(write("POST", JOBS + "/offer-expiry/run", Map.of(), adminToken, 409)).path("code").asText()).isEqualTo("JOB_RUNNING");

        var failed = data(write("POST", JOBS + "/always-fails/run", Map.of(), adminToken, 200));
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("attempts").asInt()).isEqualTo(2);
        assertThat(failed.path("message").asText()).contains("Kho dữ liệu không phản hồi");
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(admin.getPublicId())
                && event.getEventKey().startsWith("job-failed:always-fails:"));
        var status = data(send(get(JOBS).header("Authorization", "Bearer " + adminToken), 200));
        for (var job : status) {
            if (job.path("name").asText().equals("always-fails")) assertThat(job.path("consecutiveFailures").asInt()).isEqualTo(1);
            if (job.path("name").asText().equals("offer-expiry")) assertThat(job.path("running").asBoolean()).isTrue();
        }
        write("POST", JOBS + "/unknown/run", Map.of(), adminToken, 404);
        send(get(JOBS).header("Authorization", "Bearer " + token), 403);
    }

    private Order order(String code, Instant createdAt) {
        return Order.builder().orderCode(code).senderId(user.getId()).status(OrderStatus.FINDING_SHIPPER).pickupAddress("A")
                .deliveryAddress("B").receiverName("Người Nhận").receiverPhone("0901234567").productName("Hàng thử")
                .quantity(1).weightKg(BigDecimal.ONE).createdAt(createdAt).updatedAt(createdAt).build();
    }
}

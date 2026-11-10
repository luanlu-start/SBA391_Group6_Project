package com.fptpost;

import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.LedgerTransaction;
import com.fptpost.modules.payment.model.LedgerTransactionStatus;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ReportingFlowHttpTest extends IdentityFeatureHttpTest {
    static final String DASHBOARD = "/api/v1/admin/dashboard";
    static final String REPORTS = "/api/v1/admin/reports";
    @Autowired OrderRepository orders;
    @Autowired LedgerTransactionRepository ledger;
    @Autowired ParameterService parameters;
    final List<Long> createdOrders = new ArrayList<>();
    final List<Long> createdEntries = new ArrayList<>();
    User courier;
    String adminToken;

    @BeforeEach void setupReports() throws Exception {
        int number = sequence.incrementAndGet();
        User admin = createUser("admin" + number + "@example.invalid", "+842" + String.format(Locale.ROOT, "%08d", number + 71));
        admin.setRoles(new HashSet<>(Set.of(roles.findByCode("ADMIN").orElseThrow())));
        users.saveAndFlush(admin);
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
        Instant current = now.get();
        now.set(Instant.parse("2025-03-10T05:00:00Z"));
        courier = createUser("courier" + number + "@example.invalid", "+843" + String.format(Locale.ROOT, "%08d", number + 71));
        now.set(current);
    }

    @AfterEach void cleanReports() {
        ledger.deleteAllById(createdEntries);
        orders.deleteAllById(createdOrders);
    }

    @Test void dashboardAndCourierRevenueUseVietnamDaysAndLockedFeeRates() throws Exception {
        Order locked = order("RPT-LOCKED", OrderStatus.IN_DELIVERY, "Sách", new BigDecimal("0.1000"), "2025-03-05T02:00:00Z");
        Order unlocked = order("RPT-DEFAULT", OrderStatus.CANCELLED, "Áo", null, "2025-03-20T02:00:00Z");
        Order early = order("RPT-EARLY", OrderStatus.IN_DELIVERY, "Bánh", new BigDecimal("0.0500"), "2025-02-28T17:30:00Z");
        order("RPT-OUTSIDE", OrderStatus.IN_DELIVERY, "Trà", null, "2025-03-31T17:30:00Z");
        entry(LedgerTransactionType.ESCROW_RELEASE, locked.getId(), 100_000L, "2025-03-15T03:00:00Z");
        entry(LedgerTransactionType.ESCROW_RELEASE, unlocked.getId(), 50_000L, "2025-03-21T03:00:00Z");
        entry(LedgerTransactionType.ESCROW_RELEASE, early.getId(), 20_000L, "2025-02-28T17:30:00Z");
        entry(LedgerTransactionType.ESCROW_RELEASE, early.getId(), 99_000L, "2025-03-31T18:00:00Z");
        entry(LedgerTransactionType.WALLET_TOPUP, null, 200_000L, "2025-03-02T03:00:00Z");
        entry(LedgerTransactionType.WALLET_TOPUP, LedgerTransactionStatus.PENDING, null, 500_000L, "2025-03-03T03:00:00Z");

        var snapshot = parameters.getSnapshot();
        long commission = floor(100_000, new BigDecimal("0.1000")) + floor(50_000, snapshot.decimal("FEE_NEW"))
                + floor(20_000, new BigDecimal("0.0500"));
        BigDecimal withhold = snapshot.decimal("WITHHOLD");
        long withholding = floor(100_000, withhold) + floor(50_000, withhold) + floor(20_000, withhold);

        var dashboard = data(send(get(DASHBOARD).param("from", "2025-03-01").param("to", "2025-03-31")
                .header("Authorization", "Bearer " + adminToken), 200));
        assertThat(dashboard.path("ordersCreated").asLong()).isEqualTo(3);
        assertThat(dashboard.path("ordersByStatus").path("CANCELLED").asLong()).isEqualTo(1);
        assertThat(dashboard.path("cancellationRate").asDouble()).isEqualTo(0.3333);
        assertThat(dashboard.path("escrowReleasedAmount").asLong()).isEqualTo(170_000);
        assertThat(dashboard.path("escrowReleasedCount").asLong()).isEqualTo(3);
        assertThat(dashboard.path("estimatedCommission").asLong()).isEqualTo(commission);
        assertThat(dashboard.path("topUpAmount").asLong()).isEqualTo(200_000);
        assertThat(dashboard.path("newUsers").asLong()).isEqualTo(1);
        assertThat(dashboard.path("daily")).hasSize(31);
        assertThat(dashboard.path("daily").get(0).path("date").asText()).isEqualTo("2025-03-01");
        assertThat(dashboard.path("daily").get(0).path("ordersCreated").asLong()).isEqualTo(1);
        assertThat(dashboard.path("daily").get(0).path("escrowReleasedAmount").asLong()).isEqualTo(20_000);

        var revenue = data(send(get(REPORTS + "/courier-revenue").param("month", "2025-03")
                .header("Authorization", "Bearer " + adminToken), 200));
        assertThat(revenue).hasSize(1);
        var row = revenue.get(0);
        assertThat(row.path("courierPublicId").asText()).isEqualTo(courier.getPublicId().toString());
        assertThat(row.path("email").asText()).isEqualTo(courier.getEmail());
        assertThat(row.path("releasedOrders").asLong()).isEqualTo(3);
        assertThat(row.path("grossAmount").asLong()).isEqualTo(170_000);
        assertThat(row.path("commission").asLong()).isEqualTo(commission);
        assertThat(row.path("withholding").asLong()).isEqualTo(withholding);
        assertThat(row.path("netIncome").asLong()).isEqualTo(170_000 - commission - withholding);
    }

    @Test void exportsExcelReadyCsvAndRejectsInvalidRequests() throws Exception {
        order("RPT-CSV-1", OrderStatus.FINDING_SHIPPER, "=HYPERLINK(\"http://example.invalid\")", null, "2025-03-05T02:00:00Z");
        order("RPT-CSV-2", OrderStatus.IN_DELIVERY, "Bánh, kẹo", null, "2025-03-06T02:00:00Z");

        var result = send(get(REPORTS + "/export").param("type", "orders").param("from", "2025-03-01").param("to", "2025-03-31")
                .header("Authorization", "Bearer " + adminToken), 200);
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("don-hang-2025-03-01-2025-03-31.csv");
        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿order_code,status,product_name,sender_public_id");
        assertThat(csv).contains("RPT-CSV-1,FINDING_SHIPPER,\"'=HYPERLINK(\"\"http://example.invalid\"\")\"," + user.getPublicId());
        assertThat(csv).contains("RPT-CSV-2,IN_DELIVERY,\"Bánh, kẹo\"," + user.getPublicId() + "," + courier.getPublicId());
        assertThat(csv).contains("2025-03-06 09:00");

        var revenue = send(get(REPORTS + "/export").param("type", "COURIER_REVENUE").param("month", "2025-03")
                .header("Authorization", "Bearer " + adminToken), 200);
        assertThat(new String(revenue.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .startsWith("﻿courier_id,full_name,email,released_orders");

        assertThat(body(send(get(REPORTS + "/export").param("type", "COURIER_REVENUE")
                .header("Authorization", "Bearer " + adminToken), 400)).path("code").asText()).isEqualTo("REPORT_RANGE_INVALID");
        assertThat(body(send(get(REPORTS + "/export").param("type", "SALARY")
                .header("Authorization", "Bearer " + adminToken), 400)).path("code").asText()).isEqualTo("REPORT_TYPE_INVALID");
        assertThat(body(send(get(DASHBOARD).param("from", "2025-03-10").param("to", "2025-03-01")
                .header("Authorization", "Bearer " + adminToken), 400)).path("code").asText()).isEqualTo("REPORT_RANGE_INVALID");
        assertThat(body(send(get(DASHBOARD).param("from", "2023-01-01").param("to", "2025-03-01")
                .header("Authorization", "Bearer " + adminToken), 400)).path("code").asText()).isEqualTo("REPORT_RANGE_INVALID");

        var defaults = data(send(get(DASHBOARD).header("Authorization", "Bearer " + adminToken), 200));
        assertThat(defaults.path("to").asText()).isEqualTo("2026-10-20");
        assertThat(defaults.path("from").asText()).isEqualTo("2026-09-21");

        send(get(DASHBOARD).header("Authorization", "Bearer " + token), 403);
        send(get(REPORTS + "/export").param("type", "ORDERS").header("Authorization", "Bearer " + token), 403);
    }

    private Order order(String code, OrderStatus status, String product, BigDecimal feeRate, String createdAt) {
        Instant time = Instant.parse(createdAt);
        Order saved = orders.saveAndFlush(Order.builder().orderCode(code).senderId(user.getId()).courierId(courier.getId()).status(status)
                .pickupAddress("A").deliveryAddress("B").receiverName("Người Nhận").receiverPhone("0901234567").productName(product)
                .quantity(1).weightKg(BigDecimal.ONE).feeRateLocked(feeRate).createdAt(time).updatedAt(time).build());
        createdOrders.add(saved.getId());
        return saved;
    }

    private void entry(LedgerTransactionType type, Long orderId, long amount, String createdAt) {
        entry(type, LedgerTransactionStatus.COMPLETED, orderId, amount, createdAt);
    }

    private void entry(LedgerTransactionType type, LedgerTransactionStatus status, Long orderId, long amount, String createdAt) {
        String key = "RPT-" + UUID.randomUUID();
        LedgerTransaction saved = ledger.saveAndFlush(LedgerTransaction.builder().transactionCode(key).idempotencyKey(key)
                .type(type).status(status).orderId(orderId).amount(amount)
                .createdBy(type == LedgerTransactionType.ESCROW_RELEASE ? courier.getId() : user.getId())
                .createdAt(Instant.parse(createdAt)).build());
        createdEntries.add(saved.getId());
    }

    private static long floor(long amount, BigDecimal rate) {
        return BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.FLOOR).longValueExact();
    }
}

package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.moderation.repository.ViolationCaseRepository;
import com.fptpost.modules.moderation.repository.ViolationEvidenceRepository;
import com.fptpost.modules.moderation.repository.ViolationReportRepository;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class ViolationReportFlowHttpTest extends IdentityFeatureHttpTest {
    static final String QUEUE = "/api/v1/mod/reports";
    @Autowired ViolationCaseRepository cases;
    @Autowired ViolationReportRepository reports;
    @Autowired ViolationEvidenceRepository evidence;
    @Autowired OrderRepository orders;
    @Autowired AuditOutboxRepository auditOutbox;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    User other;
    String otherToken;
    User moderator;
    String moderatorToken;
    User admin;

    @AfterEach void cleanReports() {
        evidence.deleteAll(); reports.deleteAll(); cases.deleteAll(); orders.deleteAll();
    }

    @BeforeEach void setupReports() throws Exception {
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll();
        other = person("reporter", Set.of("SENDER"), 51);
        otherToken = access(login(other.getEmail(), "OldPassword123!", 200));
        moderator = person("moderator", Set.of("MODERATOR"), 52);
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        admin = person("admin", Set.of("ADMIN"), 53);
    }

    @Test void duplicateReportsOnTheSameUserAreMergedIntoOneCase() throws Exception {
        User offender = person("offender", Set.of("SENDER"), 54);
        var first = data(report(token, "USER", offender.getPublicId().toString(), "HARASSMENT", "Nhắn tin xúc phạm", image("files", "png"), 201));
        assertThat(first.path("merged").asBoolean()).isFalse();
        var second = data(report(otherToken, "USER", offender.getPublicId().toString(), "FRAUD", null, null, 201));
        assertThat(second.path("merged").asBoolean()).isTrue();
        assertThat(second.path("caseId").asText()).isEqualTo(first.path("caseId").asText());
        report(otherToken, "USER", offender.getPublicId().toString(), "FRAUD", null, null, 201);
        assertThat(cases.findAll()).singleElement().satisfies(item -> assertThat(item.getReportCount()).isEqualTo(2));
        report(token, "USER", user.getPublicId().toString(), "SPAM", null, null, 400);

        var queue = data(send(get(QUEUE).header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(queue.path("totalElements").asInt()).isEqualTo(1);
        assertThat(queue.path("content").get(0).path("respondent").path("email").asText()).isEqualTo(offender.getEmail());
        String caseId = first.path("caseId").asText();
        var detail = data(send(get(QUEUE + "/" + caseId).header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(detail.path("reports")).hasSize(2);
        String evidenceId = detail.path("reports").get(0).path("evidenceIds").get(0).asText();
        MvcResult image = send(get(QUEUE + "/" + caseId + "/evidence/" + evidenceId).header("Authorization", "Bearer " + moderatorToken), 200);
        assertThat(image.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(image.getResponse().getContentAsByteArray()).isNotEmpty();
        send(get(QUEUE).header("Authorization", "Bearer " + token), 403);
        assertThat(data(send(get("/api/v1/me/reports").header("Authorization", "Bearer " + otherToken), 200))
                .path("totalElements").asInt()).isEqualTo(1);
    }

    @Test void ordersCanBeReportedWhenVisibleToTheReporter() throws Exception {
        Order open = orders.saveAndFlush(order("FPT-OPEN-55", OrderStatus.FINDING_SHIPPER));
        Order hidden = orders.saveAndFlush(order("FPT-HIDDEN-55", OrderStatus.PENDING));
        var created = data(report(otherToken, "ORDER", open.getId().toString(), "PROHIBITED_GOODS", "Ảnh có pháo", null, 201));
        assertThat(created.path("targetLabel").asText()).startsWith("FPT-OPEN-55");
        report(otherToken, "ORDER", hidden.getId().toString(), "PROHIBITED_GOODS", null, null, 403);
        report(otherToken, "ORDER", "999999", "SPAM", null, null, 404);
        var detail = data(send(get(QUEUE + "/" + created.path("caseId").asText()).header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(detail.path("respondent").path("email").asText()).isEqualTo(user.getEmail());
    }

    @Test void warningNotifiesTheRespondentAndEscalationNotifiesAdministrators() throws Exception {
        User offender = person("warned", Set.of("SENDER"), 55);
        String caseId = data(report(token, "USER", offender.getPublicId().toString(), "FRAUD", null, null, 201)).path("caseId").asText();
        write("POST", QUEUE + "/" + caseId + "/resolve", Map.of("action", "WARN", "note", ""), moderatorToken, 400);
        var resolved = data(write("POST", QUEUE + "/" + caseId + "/resolve",
                Map.of("action", "ESCALATE", "note", "Lừa đảo nhiều lần"), moderatorToken, 200));
        assertThat(resolved.path("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.path("canResolve").asBoolean()).isFalse();
        write("POST", QUEUE + "/" + caseId + "/resolve", Map.of("action", "DISMISS", "note", "Lặp"), moderatorToken, 409);
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(offender.getPublicId())
                && event.getEventKey().endsWith(":warning"));
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(admin.getPublicId())
                && event.getEventKey().contains(":escalated:"));
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(user.getPublicId())
                && event.getEventKey().contains(":outcome:"));
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("VIOLATION_CASE_RESOLVED"));

        String second = data(report(otherToken, "USER", offender.getPublicId().toString(), "SPAM", null, null, 201)).path("caseId").asText();
        assertThat(second).isNotEqualTo(caseId);
        var dismissed = data(write("POST", QUEUE + "/" + second + "/resolve", Map.of("action", "DISMISS", "note", "Không đủ căn cứ"), moderatorToken, 200));
        assertThat(dismissed.path("status").asText()).isEqualTo("DISMISSED");
        assertThat(dismissed.path("respondentPriorViolations").asLong()).isEqualTo(1);
    }

    private MvcResult report(String bearer, String type, String targetId, String reason, String description,
                             MockMultipartFile file, int status) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("targetType", type, "targetId", targetId, "reason", reason));
        if (description != null) body.put("description", description);
        var request = multipart("/api/v1/reports")
                .file(new MockMultipartFile("request", "", "application/json", json.writeValueAsBytes(body)));
        if (file != null) request.file(file);
        return send(request.header("Authorization", "Bearer " + bearer), status);
    }

    private Order order(String code, OrderStatus status) {
        return Order.builder().orderCode(code).senderId(user.getId()).status(status).pickupAddress("A").deliveryAddress("B")
                .receiverName("Người Nhận").receiverPhone("0901234567").productName("Hàng thử").quantity(1).weightKg(BigDecimal.ONE).build();
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

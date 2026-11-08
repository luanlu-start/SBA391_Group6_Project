package com.fptpost;

import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.catalog.model.ItemCategory;
import com.fptpost.modules.catalog.repository.ItemCategoryRepository;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.moderation.repository.OrderModerationRepository;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class OrderModerationFlowHttpTest extends IdentityFeatureHttpTest {
    static final String QUEUE = "/api/v1/mod/orders";
    @Autowired OrderRepository orders;
    @Autowired OrderModerationRepository moderations;
    @Autowired ItemCategoryRepository categories;
    @Autowired AuditOutboxRepository auditOutbox;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    User moderator;
    String moderatorToken;
    String adminToken;

    @BeforeEach void setupModeration() throws Exception {
        deliveries.deleteAll(); notifications.deleteAll(); auditOutbox.deleteAll();
        moderations.deleteAll(); orders.deleteAll();
        moderator = staff(Set.of("MODERATOR", "SENDER"), 21);
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        User admin = staff(Set.of("ADMIN"), 22);
        adminToken = access(login(admin.getEmail(), "OldPassword123!", 200));
    }

    @Test void cleanOrderGoesStraightToTheMarketplace() throws Exception {
        var created = data(write("POST", "/api/v1/orders", order("Áo thun FPT", null, null), token, 201));
        assertThat(created.path("status").asText()).isEqualTo("FINDING_SHIPPER");
        assertThat(data(send(get(QUEUE + "/pending").header("Authorization", "Bearer " + moderatorToken), 200))
                .path("totalElements").asInt()).isZero();
        var outcome = data(send(get("/api/v1/orders/" + created.path("id").asLong() + "/moderation")
                .header("Authorization", "Bearer " + token), 200));
        assertThat(outcome.path("status").asText()).isEqualTo("AUTO_APPROVED");
    }

    @Test void flaggedOrderWaitsForAModeratorWhoApprovesIt() throws Exception {
        var created = data(write("POST", "/api/v1/orders", order("Súng nước đồ chơi", "Hàng mới", null), token, 201));
        long id = created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(user.getPublicId())
                && event.getEventKey().startsWith("order-moderation:" + id + ":pending"));

        var queue = data(send(get(QUEUE + "/pending").header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(queue.path("totalElements").asInt()).isEqualTo(1);
        var item = queue.path("content").get(0);
        assertThat(item.path("matchedTerms").get(0).asText()).isEqualTo("súng");
        assertThat(item.path("sender").path("email").asText()).isEqualTo(user.getEmail());
        assertThat(item.path("canDecide").asBoolean()).isTrue();

        var approved = data(write("POST", QUEUE + "/" + id + "/approve", Map.of(), moderatorToken, 200));
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.FINDING_SHIPPER);
        assertThat(auditOutbox.findAll()).anyMatch(event -> event.getPayload().contains("ORDER_MODERATION_APPROVED"));
        write("POST", QUEUE + "/" + id + "/approve", Map.of(), moderatorToken, 409);

        var unchanged = data(write("PUT", "/api/v1/orders/" + id, order("Súng nước đồ chơi", "Hàng mới", null), token, 200));
        assertThat(unchanged.path("status").asText()).isEqualTo("FINDING_SHIPPER");
        var edited = data(write("PUT", "/api/v1/orders/" + id, order("Súng nước đồ chơi", "Kèm hộp đạn", null), token, 200));
        assertThat(edited.path("status").asText()).isEqualTo("PENDING");
    }

    @Test void rejectionIsShownToTheSenderAndRepeatedRejectionsWarnThem() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            ids.add(data(write("POST", "/api/v1/orders", order("Pháo hoa tết " + index, null, null), token, 201)).path("id").asLong());
        }
        write("POST", QUEUE + "/" + ids.get(0) + "/reject", Map.of("reason", " "), moderatorToken, 400);
        for (Long id : ids) {
            var rejected = data(write("POST", QUEUE + "/" + id + "/reject", Map.of("reason", "Hàng cấm vận chuyển"), moderatorToken, 200));
            assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        }
        assertThat(orders.findById(ids.get(0)).orElseThrow().getStatus()).isEqualTo(OrderStatus.REJECTED);
        var outcome = data(send(get("/api/v1/orders/" + ids.get(0) + "/moderation").header("Authorization", "Bearer " + token), 200));
        assertThat(outcome.path("rejectionReason").asText()).isEqualTo("Hàng cấm vận chuyển");
        assertThat(outcome.has("matchedTerms")).isFalse();
        assertThat(notifications.findAll()).anyMatch(event -> event.getRecipient().equals(user.getPublicId())
                && event.getEventKey().startsWith("order-moderation:repeat:"));
        send(get("/api/v1/orders/" + ids.get(0) + "/moderation").header("Authorization", "Bearer " + moderatorToken), 403);
    }

    @Test void categoryFlagsQueueOrdersAndModeratorsCannotReviewTheirOwnOrders() throws Exception {
        ItemCategory category = categories.saveAndFlush(ItemCategory.builder().code("PIN-" + sequence.incrementAndGet())
                .name("Pin dự phòng").isProhibited(false).requiresReview(true).active(true).build());
        long id = data(write("POST", "/api/v1/orders", order("Sạc dự phòng", null, category.getId()), moderatorToken, 201))
                .path("id").asLong();
        var detail = data(send(get(QUEUE + "/" + id).header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(detail.path("categoryFlag").asText()).isEqualTo("REQUIRES_REVIEW");
        assertThat(detail.path("canDecide").asBoolean()).isFalse();
        assertThat(body(write("POST", QUEUE + "/" + id + "/approve", Map.of(), moderatorToken, 403)).path("code").asText())
                .isEqualTo("ORDER_MODERATION_SELF_FORBIDDEN");
        write("POST", QUEUE + "/" + id + "/approve", Map.of(), adminToken, 200);
        send(get(QUEUE + "/pending").header("Authorization", "Bearer " + token), 403);
        categories.delete(category);
    }

    @Test void administratorsManageTheKeywordCatalogue() throws Exception {
        String url = "/api/v1/admin/moderation-keywords";
        var created = data(write("POST", url, Map.of("term", "  Bình   Oxy ", "level", "SUSPICIOUS"), adminToken, 201));
        assertThat(created.path("term").asText()).isEqualTo("bình oxy");
        write("POST", url, Map.of("term", "bình oxy", "level", "PROHIBITED"), adminToken, 409);
        write("POST", url, Map.of("term", "<script>", "level", "PROHIBITED"), adminToken, 400);
        assertThat(data(write("POST", "/api/v1/orders", order("Bình oxy y tế", null, null), token, 201)).path("status").asText())
                .isEqualTo("PENDING");
        send(get(url).header("Authorization", "Bearer " + moderatorToken), 403);
        send(delete(url + "/" + created.path("id").asLong()).header("Authorization", "Bearer " + adminToken), 204);
        assertThat(data(send(get(url).header("Authorization", "Bearer " + adminToken), 200)).toString()).doesNotContain("bình oxy");
    }

    private Map<String, Object> order(String productName, String description, Long categoryId) {
        Map<String, Object> order = new HashMap<>();
        order.put("pickupAddress", "Số 1 Lê Lợi, Đà Nẵng");
        order.put("deliveryAddress", "Số 2 Nguyễn Huệ, Đà Nẵng");
        order.put("receiverName", "Người Nhận");
        order.put("receiverPhone", "0901234567");
        order.put("productName", productName);
        order.put("quantity", 1);
        order.put("weightKg", 1.5);
        order.put("declaredValue", 200000);
        order.put("distanceKm", 5);
        order.put("priceSuggested", 30000);
        if (description != null) order.put("productDescription", description);
        if (categoryId != null) order.put("categoryId", categoryId);
        return order;
    }

    private User staff(Set<String> roleCodes, int offset) {
        int number = sequence.incrementAndGet();
        User result = createUser("staff" + number + "@example.invalid", "+847" + String.format(Locale.ROOT, "%08d", number + offset));
        Set<com.fptpost.modules.identity.model.Role> assigned = new HashSet<>();
        roleCodes.forEach(code -> assigned.add(roles.findByCode(code).orElseThrow()));
        result.setRoles(assigned);
        return users.saveAndFlush(result);
    }
}

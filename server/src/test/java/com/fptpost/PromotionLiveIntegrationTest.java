package com.fptpost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.audit.AuditOutboxDispatcher;
import com.fptpost.common.audit.AuditOutboxRepository;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.pricing.model.PromotionUsage;
import com.fptpost.modules.pricing.repository.PromotionRepository;
import com.fptpost.modules.pricing.dto.request.PromotionWriteRequest;
import com.fptpost.modules.pricing.service.PromotionService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Disposable SQL Server/Mongo acceptance data; never writes to the application database. */
@EnabledIfEnvironmentVariable(named = "FPTPOST_UC45_LIVE_TEST", matches = "1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=update", "audit.outbox.enabled=false",
        "audit.retention.enabled=false", "audit.indexes.enabled=false", "management.health.mail.enabled=false",
        "app.cors.allowed-origins=http://localhost:15174,http://127.0.0.1:15174"
})
@AutoConfigureMockMvc @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PromotionLiveIntegrationTest {
    private static final String DATABASE = "fptpost_uc45_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SQL_HOST = "localhost:" + System.getenv().getOrDefault("FPTPOST_UC45_SQL_PORT", "31433");
    private static final String MONGO_HOST = "localhost:" + System.getenv().getOrDefault("FPTPOST_UC45_MONGO_PORT", "37017");
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String URL = "/api/v1/admin/promotions";
    private static boolean created;
    private static JsonNode amount;
    private static UUID senderId;

    @DynamicPropertySource
    static void databases(org.springframework.test.context.DynamicPropertyRegistry properties) throws Exception {
        String password = System.getenv("FPTPOST_UC45_SQL_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Live SQL test password is missing");
        if (!DATABASE.matches("fptpost_uc45_it_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test database name");
        if (!created) {
            try (var connection = DriverManager.getConnection(sqlUrl("master"), "sa", password);
                 var statement = connection.createStatement()) { statement.execute("CREATE DATABASE [" + DATABASE + "]"); }
            created = true;
        }
        properties.add("spring.datasource.url", () -> sqlUrl(DATABASE));
        properties.add("spring.datasource.username", () -> "sa");
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.data.mongodb.uri", () -> "mongodb://" + MONGO_HOST + "/" + DATABASE + "?serverSelectionTimeoutMS=3000");
    }

    private static String sqlUrl(String name) {
        return "jdbc:sqlserver://" + SQL_HOST + ";databaseName=" + name + ";encrypt=true;trustServerCertificate=true";
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entities;
    @Autowired PromotionRepository promotions;
    @Autowired PromotionService promotionService;
    @Autowired AuditOutboxRepository outbox;
    @Autowired AuditOutboxDispatcher dispatcher;
    @Autowired MongoTemplate mongo;
    @LocalServerPort int port;
    @MockitoBean JavaMailSender mail;

    private String login(String identifier, String role, String phone, String fullName) throws Exception {
        if (!users.existsByEmail(identifier)) new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Instant now = Instant.now();
            var user = users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email(identifier).phone(phone)
                    .passwordHash(encoder.encode(PASSWORD)).status(UserStatus.ACTIVE).emailVerifiedAt(now)
                    .createdAt(now).updatedAt(now).roles(new HashSet<>(Set.of(roles.findByCode(role).orElseThrow()))).build());
            profiles.saveAndFlush(UserProfile.builder().user(user).fullName(fullName).adultConfirmedAt(now).build());
        });
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        JsonNode csrfData = json.readTree(csrf.getResponse().getContentAsByteArray()).path("data");
        String cookieHeader = csrf.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        var cookie = new Cookie("XSRF-TOKEN", cookieHeader.substring(11).split(";", 2)[0]);
        return json.readTree(mvc.perform(post("/api/v1/auth/login").cookie(cookie)
                        .header(csrfData.path("headerName").asText(), csrfData.path("token").asText())
                        .contentType("application/json").content(json.writeValueAsString(Map.of("identifier", identifier, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data").path("accessToken").asText();
    }

    private String admin() throws Exception {
        return login("uc45-admin@example.invalid", "ADMIN", "+84990000045", "Quản trị khuyến mãi UC-45");
    }
    private String sender() throws Exception {
        return login("uc45-sender@example.invalid", "SENDER", "+84990000046", "Người nhận khuyến mãi UC-45");
    }
    private Map<String, Object> draft(String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code); body.put("type", "FIXED_AMOUNT"); body.put("value", "10000");
        body.put("validFrom", Instant.now().minusSeconds(3600).toString());
        body.put("validTo", Instant.now().plusSeconds(7 * 86400).toString()); body.put("usageLimit", 100);
        body.put("target", "ALL"); body.put("targetUserIds", List.of()); body.put("status", "ACTIVE");
        return body;
    }
    private Map<String, Object> edit(JsonNode promotion) {
        Map<String, Object> body = new HashMap<>();
        for (String key : List.of("code", "type", "value", "validFrom", "validTo", "target", "status")) body.put(key, promotion.path(key).asText());
        body.put("usageLimit", promotion.path("usageLimit").asInt());
        body.put("targetUserIds", json.convertValue(promotion.path("targetUserIds"), List.class));
        body.put("expectedVersion", promotion.path("version").asLong());
        return body;
    }
    private JsonNode data(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        var result = mvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected)).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }
    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, Map<String, Object> body) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsString(body));
    }

    @Test @Order(1)
    void createRealCampaignsValidateCanonicalCodesMoneyDatesAndTargetHydration() throws Exception {
        String token = admin(); sender();
        senderId = users.findIdByIdentifier("uc45-sender@example.invalid").flatMap(users::findById).orElseThrow().getPublicId();
        amount = data(write(post(URL), draft(" uc45_amount ")), token, 201);
        assertThat(amount.path("code").asText()).isEqualTo("UC45_AMOUNT");
        assertThat(amount.path("value").isTextual()).isTrue();
        data(write(post(URL), draft("UC45_AMOUNT")), token, 409);
        var percentage = draft("UC45_PERCENT"); percentage.put("type", "PERCENTAGE"); percentage.put("value", "12.5");
        data(write(post(URL), percentage), token, 201);
        var selected = draft("UC45_SELECTED"); selected.put("target", "SELECTED_USERS"); selected.put("targetUserIds", List.of(senderId));
        var selectedResponse = data(write(post(URL), selected), token, 201);
        assertThat(selectedResponse.path("targetUsers").get(0).path("description").asText()).isEqualTo("uc45-sender@example.invalid");
        var paused = draft("UC45_PAUSED"); paused.put("status", "PAUSED"); data(write(post(URL), paused), token, 201);
        var future = draft("UC45_SCHEDULED"); future.put("validFrom", Instant.now().plusSeconds(86400).toString());
        assertThat(data(write(post(URL), future), token, 201).path("effectiveStatus").asText()).isEqualTo("SCHEDULED");
        var expired = draft("UC45_EXPIRED"); expired.put("validFrom", Instant.now().minusSeconds(86400).toString());
        expired.put("validTo", Instant.now().minusSeconds(1).toString());
        assertThat(data(write(post(URL), expired), token, 201).path("effectiveStatus").asText()).isEqualTo("EXPIRED");
        long count = promotions.count(), audit = outbox.count();
        for (String invalid : List.of("0", "-1", "1.1", "1e3", "10000000000000000")) {
            var body = draft("UC45_INVALID"); body.put("value", invalid); data(write(post(URL), body), token, 400);
        }
        var invalidTarget = draft("UC45_INVALID"); invalidTarget.put("target", "SELECTED_USERS");
        invalidTarget.put("targetUserIds", List.of(UUID.randomUUID())); data(write(post(URL), invalidTarget), token, 400);
        assertThat(promotions.count()).isEqualTo(count); assertThat(outbox.count()).isEqualTo(audit);
        var options = data(get(URL + "/target-options").param("q", "uc45-sender"), token, 200);
        assertThat(options.path("options").get(0).path("value").asText()).isEqualTo(senderId.toString());
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test @Order(2)
    void realUsageCountsPauseAndOptimisticVersionPreserveHistoryAndRollbackFailedWrites() throws Exception {
        String token = admin(); UUID id = UUID.fromString(amount.path("id").asText());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var promotion = promotions.findById(id).orElseThrow();
            for (long order : List.of(900045L, 900046L)) entities.persist(PromotionUsage.builder().id(UUID.randomUUID())
                    .promotion(promotion).userPublicId(senderId).orderId(order).createdAt(Instant.now()).build());
            entities.flush();
        });
        var current = data(get(URL + "/" + id), token, 200); assertThat(current.path("usedCount").asLong()).isEqualTo(2);
        long audit = outbox.count();
        var tooLow = edit(current); tooLow.put("usageLimit", 1); data(write(put(URL + "/" + id), tooLow), token, 400);
        var pause = edit(current); pause.put("status", "PAUSED");
        var updated = data(write(put(URL + "/" + id), pause), token, 200);
        assertThat(updated.path("version").asLong()).isGreaterThan(current.path("version").asLong());
        assertThat(updated.path("usedCount").asLong()).isEqualTo(2);
        assertThat(updated.path("effectiveStatus").asText()).isEqualTo("PAUSED");
        data(write(put(URL + "/" + id), pause), token, 409);
        var renamed = edit(updated); renamed.put("code", "UC45_CHANGED"); data(write(put(URL + "/" + id), renamed), token, 400);
        assertThat(outbox.count()).isEqualTo(audit + 1);
        var restore = edit(updated); restore.put("status", "ACTIVE");
        amount = data(write(put(URL + "/" + id), restore), token, 200);
        assertThat(amount.path("usedCount").asLong()).isEqualTo(2);
    }

    @Test @Order(3)
    void sqlUniqueConstraintHandlesConcurrentCreatesAndLiteralSearchAndPaging() throws Exception {
        String token = admin(); String payload = json.writeValueAsString(draft("UC45_RACE"));
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int n = 0; n < 2; n++) responses.add(workers.submit(() -> {
                start.await();
                return mvc.perform(post(URL).header("Authorization", "Bearer " + token).contentType("application/json").content(payload))
                        .andReturn().getResponse().getStatus();
            }));
            start.countDown();
            assertThat(List.of(responses.get(0).get(30, TimeUnit.SECONDS), responses.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        var first = data(get(URL).param("q", "UC45_").param("size", "2"), token, 200);
        var second = data(get(URL).param("q", "UC45_").param("size", "2").param("page", "1"), token, 200);
        assertThat(first.path("totalElements").asLong()).isEqualTo(7);
        Set<String> firstIds = new HashSet<>(); first.path("content").forEach(item -> firstIds.add(item.path("id").asText()));
        second.path("content").forEach(item -> assertThat(firstIds).doesNotContain(item.path("id").asText()));
        assertThat(data(get(URL).param("q", "%"), token, 200).path("totalElements").asLong()).isZero();
        assertThat(data(get(URL).param("status", "PAUSED"), token, 200).path("totalElements").asLong()).isEqualTo(1);
        assertThat(data(get(URL).param("status", "EXPIRED"), token, 200).path("totalElements").asLong()).isEqualTo(1);
    }

    @Test @Order(4)
    void authorizationAndRealSqlOutboxToMongoAuditHaveNoManagementWriteEscape() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        String denied = sender(); mvc.perform(get(URL).header("Authorization", "Bearer " + denied)).andExpect(status().isForbidden());
        mvc.perform(write(post(URL), draft("UC45_FORBIDDEN")).header("Authorization", "Bearer " + denied)).andExpect(status().isForbidden());
        String token = admin(); mvc.perform(delete(URL + "/" + amount.path("id").asText()).header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed());
        long priorOutbox = outbox.count();
        UUID id = UUID.fromString(amount.path("id").asText());
        var prior = promotions.findById(id).orElseThrow();
        var adminId = users.findIdByIdentifier("uc45-admin@example.invalid").flatMap(users::findById).orElseThrow().getPublicId();
        Instant now = Instant.now();
        var jwt = Jwt.withTokenValue("uc45-rollback").header("alg", "HS256").subject(adminId.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            var request = json.convertValue(edit(amount), PromotionWriteRequest.class); request.setValue("25000");
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                promotionService.update(id, request); tx.setRollbackOnly();
            });
        } finally { SecurityContextHolder.clearContext(); }
        var rolledBack = promotions.findById(id).orElseThrow();
        assertThat(rolledBack.getValue()).isEqualByComparingTo(prior.getValue());
        assertThat(rolledBack.getVersion()).isEqualTo(prior.getVersion());
        assertThat(outbox.count()).isEqualTo(priorOutbox);
        assertThat(dispatcher.dispatchBatch().delivered()).isPositive();
        assertThat(mongo.getCollection("audit_logs").countDocuments(new Document("entityType", "PROMOTION"))).isGreaterThanOrEqualTo(9);
        var audit = data(get("/api/v1/admin/audit-logs").param("action", "PROMOTION_CREATED").param("size", "100"), token, 200);
        assertThat(audit.path("totalElements").asLong()).isEqualTo(7);
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test @Order(5) @EnabledIfEnvironmentVariable(named = "FPTPOST_UC45_BROWSER_TEST", matches = "1")
    void exposeDisposableApiForBrowserAcceptance() throws Exception {
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "fptpost_uc45_browser");
        Files.createDirectories(directory); Files.deleteIfExists(directory.resolve("complete"));
        Files.writeString(directory.resolve("ready.json"), json.writeValueAsString(Map.of("apiPort", port, "database", DATABASE,
                "email", "uc45-admin@example.invalid", "password", PASSWORD)));
        for (int n = 0; n < 600 && !Files.exists(directory.resolve("complete")); n++) Thread.sleep(1000);
        assertThat(directory.resolve("complete")).exists();
    }

    @AfterAll static void cleanup() throws Exception {
        if (!created || "1".equals(System.getenv("FPTPOST_UC45_KEEP_TEST_DATABASE"))) return;
        if (!DATABASE.matches("fptpost_uc45_it_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe cleanup target");
        try (var connection = DriverManager.getConnection(sqlUrl("master"), "sa", System.getenv("FPTPOST_UC45_SQL_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE [" + DATABASE + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            statement.execute("DROP DATABASE [" + DATABASE + "]");
        }
        try (var client = com.mongodb.client.MongoClients.create("mongodb://" + MONGO_HOST)) { client.getDatabase(DATABASE).drop(); }
    }
}

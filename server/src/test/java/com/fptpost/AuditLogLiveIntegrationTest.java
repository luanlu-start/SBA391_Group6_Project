package com.fptpost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.audit.*;
import com.fptpost.modules.catalog.dto.request.ParameterUpdateRequest;
import com.fptpost.modules.catalog.repository.ParameterHistoryRepository;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.audit.retention.AuditRetentionService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated SQL Server/Mongo test. No fixture, retention deletion or email touches the app database. */
@EnabledIfEnvironmentVariable(named = "FPTPOST_UC60_LIVE_TEST", matches = "1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=update", "audit.outbox.enabled=false",
        "audit.retention.enabled=false", "audit.indexes.enabled=false", "management.health.mail.enabled=false",
        "app.cors.allowed-origins=http://localhost:15174,http://127.0.0.1:15174"
})
@AutoConfigureMockMvc @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditLogLiveIntegrationTest {
    private static final String DATABASE = "fptpost_uc60_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SQL_HOST = "localhost:" + System.getenv().getOrDefault("FPTPOST_UC60_SQL_PORT", "31433");
    private static final String MONGO_HOST = "localhost:" + System.getenv().getOrDefault("FPTPOST_UC60_MONGO_PORT", "37017");
    private static final String PASSWORD = "Uc60Integration123!";
    private static boolean created;

    @DynamicPropertySource
    static void databases(org.springframework.test.context.DynamicPropertyRegistry properties) throws Exception {
        String password = System.getenv("FPTPOST_UC60_SQL_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Live SQL test password is missing");
        if (!DATABASE.matches("fptpost_uc60_it_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test database name");
        if (!created) {
            try (var connection = DriverManager.getConnection(sqlUrl("master"), "sa", password);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE [" + DATABASE + "]");
            }
            created = true;
        }
        properties.add("spring.datasource.url", () -> sqlUrl(DATABASE));
        properties.add("spring.datasource.username", () -> "sa");
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.data.mongodb.uri", () -> "mongodb://" + MONGO_HOST + "/" + DATABASE + "?serverSelectionTimeoutMS=3000");
    }

    private static String sqlUrl(String database) {
        return "jdbc:sqlserver://" + SQL_HOST + ";databaseName=" + database + ";encrypt=true;trustServerCertificate=true";
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MongoTemplate mongo;
    @Autowired SystemParameterRepository parameters;
    @Autowired ParameterHistoryRepository history;
    @Autowired ParameterService parameterService;
    @Autowired AuditOutboxRepository outbox;
    @Autowired AuditOutboxDispatcher dispatcher;
    @Autowired AuditOutboxTransactions auditTransactions;
    @Autowired AuditOutboxProperties outboxProperties;
    @Autowired MongoAuditSink auditSink;
    @Autowired MongoAuditIndexInitializer auditIndexes;
    @Autowired AuditRetentionService retention;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    @MockitoBean JavaMailSender mail;

    private String login(String identifier, String role, String phone) throws Exception {
        if (!users.existsByEmail(identifier)) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                Instant now = Instant.now();
                var user = users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email(identifier).phone(phone)
                        .passwordHash(encoder.encode(PASSWORD)).status(UserStatus.ACTIVE).emailVerifiedAt(now)
                        .createdAt(now).updatedAt(now).roles(new HashSet<>(Set.of(roles.findByCode(role).orElseThrow()))).build());
                profiles.saveAndFlush(UserProfile.builder().user(user).fullName("Kiểm thử nhật ký UC-60")
                        .adultConfirmedAt(now).build());
            });
        }
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        JsonNode csrfData = json.readTree(csrf.getResponse().getContentAsByteArray()).path("data");
        String cookieHeader = csrf.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        var cookie = new Cookie("XSRF-TOKEN", cookieHeader.substring(11).split(";", 2)[0]);
        var result = mvc.perform(post("/api/v1/auth/login").cookie(cookie)
                        .header(csrfData.path("headerName").asText(), csrfData.path("token").asText())
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "identifier", identifier, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("accessToken").asText();
    }

    private String admin() throws Exception { return login("uc60-admin@example.invalid", "ADMIN", "+84990000060"); }
    private JsonNode read(String token, String... filter) throws Exception {
        var request = get("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + token);
        for (int i = 0; i < filter.length; i += 2) request.param(filter[i], filter[i + 1]);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    @Test @Order(1)
    void tenRealParameterMutationsProduceTenReadableAuditsWithRequiredFields() throws Exception {
        String token = admin();
        for (int index = 1; index <= 10; index++) {
            long version = parameters.findByKey("BASE_FARE").orElseThrow().getVersion();
            mvc.perform(put("/api/v1/admin/params/BASE_FARE").header("Authorization", "Bearer " + token)
                            .contentType("application/json").content(json.writeValueAsString(Map.of(
                                    "value", String.valueOf(10000 + index * 1000), "expectedVersion", version))))
                    .andExpect(status().isOk());
        }
        assertThat(dispatcher.dispatchBatch().delivered()).isEqualTo(10);
        var page = read(token, "action", "PARAMETER_UPDATED", "entityId", "BASE_FARE", "size", "100");
        assertThat(page.path("totalElements").asLong()).isEqualTo(10);
        for (JsonNode event : page.path("content")) {
            assertThat(event.path("eventId").asText()).isNotBlank();
            assertThat(event.path("actorPublicId").asText()).isNotBlank();
            assertThat(event.path("ip").asText()).isEqualTo("127.0.0.1");
            assertThat(event.path("at").asText()).isNotBlank();
            assertThat(event.path("correlationId").asText()).isNotBlank();
            assertThat(event.path("before").path("value").asText()).isNotEqualTo(event.path("after").path("value").asText());
        }
        assertThat(history.count()).isEqualTo(10);
        assertThat(outbox.findAll()).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(AuditOutboxEntity.Status.DELIVERED));
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test @Order(2)
    void realMongoFiltersStableTiePagingAndDefensiveMasking() throws Exception {
        String token = admin();
        String actor = users.findIdByIdentifier("uc60-admin@example.invalid").flatMap(users::findById).orElseThrow().getPublicId().toString();
        assertThat(read(token, "actor", actor, "action", "PARAMETER_UPDATED").path("totalElements").asLong()).isEqualTo(10);
        assertThat(read(token, "entityId", "UNKNOWN").path("totalElements").asLong()).isZero();
        Instant at = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        for (int index = 1; index <= 3; index++) {
            String eventId = "00000000-0000-4000-8000-00000000000" + index;
            mongo.insert(new MongoAuditDocument(eventId, eventId, "USER", actor, "PRIVACY_TEST_RECORD",
                    "TEST_FIXTURE", "literal.a[*]", Map.of("cccd", "synthetic-id", "password", "synthetic-password",
                    "nested", List.of(Map.of("otp", "000000", "access_token", "synthetic-token"))),
                    Map.of("signedUrl", "https://example.invalid/synthetic", "safe", "<script>synthetic text</script>"),
                    "127.0.0.1", UUID.randomUUID().toString(), at));
        }
        var first = read(token, "action", "PRIVACY_TEST_RECORD", "entityId", "literal.a[*]", "from", at.toString(), "to", at.toString(), "size", "2");
        var second = read(token, "action", "PRIVACY_TEST_RECORD", "entityId", "literal.a[*]", "page", "1", "size", "2");
        assertThat(first.path("totalElements").asInt()).isEqualTo(3);
        assertThat(first.path("content").get(0).path("eventId").asText()).endsWith("003");
        assertThat(first.path("content").get(1).path("eventId").asText()).endsWith("002");
        assertThat(second.path("content").get(0).path("eventId").asText()).endsWith("001");
        assertThat(first.toString()).doesNotContain("synthetic-password", "synthetic-id", "synthetic-token");
        assertThat(first.path("content").get(0).path("before").path("cccd").asText()).isEqualTo("[REDACTED]");
        assertThat(first.path("content").get(0).path("before").path("nested").get(0).path("otp").asText()).isEqualTo("[REDACTED]");
        assertThat(first.path("content").get(0).path("after").path("safe").asText()).contains("<script>");
    }

    @Test @Order(3)
    void failedMongoConnectionRetriesCommittedOutboxThenRealSinkDeduplicates() throws Exception {
        String token = admin();
        long version = parameters.findByKey("INS_MIN").orElseThrow().getVersion();
        mvc.perform(put("/api/v1/admin/params/INS_MIN").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(Map.of("value", "2500", "expectedVersion", version))))
                .andExpect(status().isOk());
        try (var failedClient = com.mongodb.client.MongoClients.create("mongodb://localhost:1/" + DATABASE
                + "?serverSelectionTimeoutMS=500&connectTimeoutMS=500")) {
            var failedSink = new MongoAuditSink(new MongoTemplate(failedClient, DATABASE));
            var retryDispatcher = new AuditOutboxDispatcher(auditTransactions, failedSink, json, outboxProperties);
            assertThat(retryDispatcher.dispatchBatch().retried()).isEqualTo(1);
        }
        assertThat(outbox.findAll()).filteredOn(event -> event.getStatus() == AuditOutboxEntity.Status.PENDING).hasSize(1);
        Thread.sleep(2200);
        assertThat(dispatcher.dispatchBatch().delivered()).isEqualTo(1);
        var eventRow = outbox.findAll().stream().filter(event -> {
            try { return json.readValue(event.getPayload(), AuditEvent.class).entityId().equals("INS_MIN"); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }).findFirst().orElseThrow();
        var event = json.readValue(eventRow.getPayload(), AuditEvent.class);
        auditSink.append(event); auditSink.append(event);
        assertThat(mongo.getCollection("audit_logs").countDocuments(new Document("eventId", event.eventId().toString()))).isEqualTo(1);
    }

    @Test @Order(4)
    void readOnlyAuthorizationAndFailedMutationCreateNoAudit() throws Exception {
        String sender = login("uc60-sender@example.invalid", "SENDER", "+84990000061");
        mvc.perform(get("/api/v1/admin/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + sender)).andExpect(status().isForbidden());
        String token = admin();
        long count = mongo.getCollection("audit_logs").countDocuments();
        long version = parameters.findByKey("BASE_FARE").orElseThrow().getVersion();
        mvc.perform(put("/api/v1/admin/params/BASE_FARE").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(Map.of("value", "21000", "expectedVersion", version - 1))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + token)).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + token)).andExpect(status().isMethodNotAllowed());
        assertThat(mongo.getCollection("audit_logs").countDocuments()).isEqualTo(count);
    }

    @Test @Order(5)
    void rolledBackSqlChangeCreatesNeitherHistoryNorReadableAudit() {
        long priorHistory = history.count(), priorOutbox = outbox.count();
        var parameter = parameters.findByKey("BASE_FARE").orElseThrow();
        String oldValue = parameter.getValue();
        Instant now = Instant.now();
        var actor = users.findIdByIdentifier("uc60-admin@example.invalid").flatMap(users::findById).orElseThrow().getPublicId();
        var jwt = Jwt.withTokenValue("uc60-test").header("alg", "HS256").subject(actor.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                parameterService.update("BASE_FARE", new ParameterUpdateRequest("25000", parameter.getVersion()));
                tx.setRollbackOnly();
            });
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(parameters.findByKey("BASE_FARE").orElseThrow().getValue()).isEqualTo(oldValue);
        assertThat(history.count()).isEqualTo(priorHistory);
        assertThat(outbox.count()).isEqualTo(priorOutbox);
        assertThat(dispatcher.dispatchBatch().claimed()).isZero();
    }

    @Test @Order(6)
    void realMongoQueryIndexesAndCalendarRetentionWorkWithoutTtl() {
        auditIndexes.ensureIndexes();
        var indexes = mongo.getCollection("audit_logs").listIndexes().into(new ArrayList<>());
        assertThat(indexes).allSatisfy(index -> assertThat(index).doesNotContainKey("expireAfterSeconds"));
        assertThat(indexes).anySatisfy(index -> assertThat(index.get("key", Document.class)).containsKey("actor"));
        assertThat(indexes).anySatisfy(index -> assertThat(index.get("key", Document.class)).containsKey("action"));
        String oldId = UUID.randomUUID().toString(), recentId = UUID.randomUUID().toString();
        for (var fixture : Map.of(oldId, Instant.parse("2020-01-01T00:00:00Z"), recentId, Instant.now()).entrySet()) {
            mongo.insert(new MongoAuditDocument(fixture.getKey(), fixture.getKey(), "SYSTEM", "UC60_TEST",
                    "RETENTION_TEST_RECORD", "TEST_FIXTURE", fixture.getKey(), Map.of(), Map.of(),
                    null, "retention-fixture", fixture.getValue()));
        }
        var result = retention.purgeExpiredBatch();
        assertThat(result.removed()).isEqualTo(1);
        assertThat(mongo.findById(oldId, MongoAuditDocument.class)).isNull();
        assertThat(mongo.findById(recentId, MongoAuditDocument.class)).isNotNull();
        assertThat(mongo.getCollection("audit_logs").countDocuments(new Document("action", "PARAMETER_UPDATED"))).isEqualTo(11);
    }

    @Test @Order(7)
    void filterOptionsUseRealDistinctValuesAndIdentityLabelsWithLiteralSearch() throws Exception {
        String token = admin();
        String url = "/api/v1/admin/audit-logs/filter-options";
        var first = mvc.perform(get(url).header("Authorization", "Bearer " + token)
                        .param("field", "action").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        var firstData = json.readTree(first.getResponse().getContentAsByteArray()).path("data");
        assertThat(firstData.path("options").size()).isEqualTo(1);
        assertThat(firstData.path("hasMore").asBoolean()).isTrue();

        var entity = mvc.perform(get(url).header("Authorization", "Bearer " + token)
                        .param("field", "entityId").param("entityType", "TEST_FIXTURE").param("q", "literal.a[*]"))
                .andExpect(status().isOk()).andReturn();
        var options = json.readTree(entity.getResponse().getContentAsByteArray()).path("data").path("options");
        assertThat(options.size()).isEqualTo(1);
        assertThat(options.get(0).path("value").asText()).isEqualTo("literal.a[*]");
        mvc.perform(get(url).header("Authorization", "Bearer " + token).param("field", "entityId")
                        .param("entityType", "SYSTEM_PARAMETER").param("q", "literal.a[*]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.options").isEmpty());

        var actor = mvc.perform(get(url).header("Authorization", "Bearer " + token)
                        .param("field", "actor").param("q", "uc60-admin@example.invalid"))
                .andExpect(status().isOk()).andReturn();
        var actorOptions = json.readTree(actor.getResponse().getContentAsByteArray()).path("data").path("options");
        assertThat(actorOptions.size()).isEqualTo(1);
        assertThat(actorOptions.get(0).path("label").asText()).isEqualTo("Kiểm thử nhật ký UC-60");
        assertThat(actorOptions.get(0).path("description").asText()).isEqualTo("uc60-admin@example.invalid");
        String actorId = actorOptions.get(0).path("value").asText();
        assertThat(UUID.fromString(actorId)).isNotNull();
        assertThat(read(token, "actor", actorId).path("totalElements").asInt()).isPositive();
        assertThat(actor.getResponse().getContentAsString()).doesNotContain("passwordHash", PASSWORD, "phone", "roles");
        mvc.perform(get(url).header("Authorization", "Bearer " + token).param("field", "actor").param("q", actorId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.options[0].label").value("Kiểm thử nhật ký UC-60"));
        for (String literal : List.of("%", "_", "[a]", "!")) {
            mvc.perform(get(url).header("Authorization", "Bearer " + token).param("field", "actor").param("q", literal))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.options").isEmpty());
        }

        String missingActor = UUID.randomUUID().toString(), event = UUID.randomUUID().toString();
        mongo.insert(new MongoAuditDocument(event, event, "USER", missingActor, "FUTURE_AUDIT_ACTION",
                "FUTURE_OBJECT", "future-object", Map.of(), Map.of(), null, "filter-options", Instant.now()));
        mvc.perform(get(url).header("Authorization", "Bearer " + token).param("field", "actor").param("q", missingActor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.options[0].value").value(missingActor));
        mvc.perform(get(url).header("Authorization", "Bearer " + token).param("field", "action").param("q", "future_audit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.options[0].value").value("FUTURE_AUDIT_ACTION"));
        mvc.perform(get(url).header("Authorization", "Bearer " + token).param("field", "actor").param("q", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.options").isEmpty());
        mvc.perform(get(url).param("field", "action")).andExpect(status().isUnauthorized());
        String sender = login("uc60-sender@example.invalid", "SENDER", "+84990000061");
        mvc.perform(get(url).header("Authorization", "Bearer " + sender).param("field", "actor"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test @Order(8)
    @EnabledIfEnvironmentVariable(named = "FPTPOST_UC60_BROWSER_TEST", matches = "1")
    void exposeIsolatedRealApiForBrowserAcceptance() throws Exception {
        String token = admin();
        // Real mutations in the isolated database provide enough events to exercise UI paging.
        for (int index = 1; index <= 20; index++) {
            long version = parameters.findByKey("BASE_FARE").orElseThrow().getVersion();
            mvc.perform(put("/api/v1/admin/params/BASE_FARE").header("Authorization", "Bearer " + token)
                            .contentType("application/json").content(json.writeValueAsString(Map.of(
                                    "value", String.valueOf(20000 + index * 1000), "expectedVersion", version))))
                    .andExpect(status().isOk());
        }
        assertThat(dispatcher.dispatchBatch().delivered()).isEqualTo(20);
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "fptpost_uc60_browser");
        Files.createDirectories(directory);
        Files.deleteIfExists(directory.resolve("complete"));
        Files.writeString(directory.resolve("ready.json"), json.writeValueAsString(Map.of(
                "apiPort", port, "database", DATABASE, "email", "uc60-admin@example.invalid", "password", PASSWORD)));
        for (int attempt = 0; attempt < 600 && !Files.exists(directory.resolve("complete")); attempt++) Thread.sleep(1000);
        assertThat(directory.resolve("complete")).exists();
    }

    @AfterAll static void cleanup() throws Exception {
        if (!created || "1".equals(System.getenv("FPTPOST_UC60_KEEP_TEST_DATABASE"))) return;
        if (!DATABASE.matches("fptpost_uc60_it_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe cleanup target");
        try (var connection = DriverManager.getConnection(sqlUrl("master"), "sa", System.getenv("FPTPOST_UC60_SQL_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE [" + DATABASE + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            statement.execute("DROP DATABASE [" + DATABASE + "]");
        }
        try (var client = com.mongodb.client.MongoClients.create("mongodb://" + MONGO_HOST)) { client.getDatabase(DATABASE).drop(); }
    }
}

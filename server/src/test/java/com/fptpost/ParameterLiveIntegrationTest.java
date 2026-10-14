package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.audit.*;
import com.fptpost.modules.catalog.dto.request.ParameterUpdateRequest;
import com.fptpost.modules.catalog.repository.ParameterHistoryRepository;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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

/** Opt-in SQL Server/MongoDB verification, isolated from the project's application database. */
@EnabledIfEnvironmentVariable(named = "FPTPOST_UC46_LIVE_TEST", matches = "1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=update", "audit.outbox.enabled=false",
        "management.health.mail.enabled=false", "app.registration.requests-per-minute=1000",
        "app.cors.allowed-origins=http://localhost:15174,http://127.0.0.1:15174"
})
@AutoConfigureMockMvc @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParameterLiveIntegrationTest {
    private static final String DATABASE = "fptpost_uc46_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SQL_HOST = "localhost:" + System.getenv().getOrDefault("FPTPOST_UC46_SQL_PORT", "31433");
    private static final String MONGO_HOST = "localhost:" + System.getenv().getOrDefault("FPTPOST_UC46_MONGO_PORT", "37017");
    private static final String PASSWORD = "Uc46Integration123!";
    private static final UUID ACTOR = UUID.randomUUID();
    private static boolean created;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) throws Exception {
        String password = System.getenv("FPTPOST_UC46_SQL_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Live SQL test password is missing");
        if (!created) {
            if (!DATABASE.matches("fptpost_uc46_it_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test database name");
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

    @Autowired ParameterService parameters;
    @Autowired SystemParameterRepository rows;
    @Autowired ParameterHistoryRepository history;
    @Autowired AuditOutboxRepository outbox;
    @Autowired AuditOutboxDispatcher dispatcher;
    @Autowired AuditOutboxTransactions auditTransactions;
    @Autowired MongoAuditSink auditSink;
    @Autowired MongoTemplate mongo;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @LocalServerPort int port;
    @MockitoBean JavaMailSender mail; // No OTP or email is sent by this test.

    @BeforeEach void actor() { authenticate(); }
    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }

    @Test @Order(1)
    void sqlServerCommitsUnicodeHistoryOutboxAndMongoDeliveryTogether() {
        assertThat(rows.count()).isEqualTo(109);
        var previous = parameters.getSnapshot();
        var initial = rows.findByKey("BASE_FARE").orElseThrow();
        parameters.update("BASE_FARE", new ParameterUpdateRequest("12000", initial.getVersion()));
        assertThat(parameters.history("BASE_FARE", 0, 20).content()).singleElement().satisfies(entry -> {
            assertThat(entry.getChangedBy()).isEqualTo(ACTOR);
            assertThat(entry.getOldValue()).isEqualTo("10000");
            assertThat(entry.getNewValue()).isEqualTo("12000");
        });
        assertThat(rows.findByKey("BASE_FARE").orElseThrow().getName()).isEqualTo("Phí giao cơ bản");
        assertThat(previous.value("BASE_FARE")).isEqualTo("10000");
        var result = dispatcher.dispatchBatch();
        assertThat(result.delivered()).isEqualTo(1);
        assertThat(mongo.getCollection("audit_logs").countDocuments()).isEqualTo(1);
        assertThat(outbox.findAll()).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(AuditOutboxEntity.Status.DELIVERED));
    }

    @Test @Order(2)
    void realSqlRollbackAndConcurrentUpdatesPreserveCatalogInvariants() throws Exception {
        long beforeHistory = history.count(), beforeOutbox = outbox.count();
        var initial = rows.findByKey("BASE_FARE").orElseThrow();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            parameters.update("BASE_FARE", new ParameterUpdateRequest("15000", initial.getVersion()));
            tx.setRollbackOnly();
        });
        assertThat(rows.findByKey("BASE_FARE").orElseThrow().getValue()).isEqualTo("12000");
        assertThat(history.count()).isEqualTo(beforeHistory); assertThat(outbox.count()).isEqualTo(beforeOutbox);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> concurrentUpdate(start, "SIZE_S", "49"));
            var second = pool.submit(() -> concurrentUpdate(start, "SIZE_M", "40"));
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("UPDATED", "PARAMETER_CONSTRAINT_VIOLATION");
        }
        var finalSnapshot = parameters.getSnapshot();
        assertThat(finalSnapshot.decimal("SIZE_S")).isLessThan(finalSnapshot.decimal("SIZE_M"));
    }

    @Test @Order(3)
    void mongoInsertAfterWorkerCrashIsDeduplicatedWhenSqlLeaseIsRecovered() throws Exception {
        // Drain another test's committed event before creating this crash/replay scenario.
        dispatcher.dispatchBatch();
        var parameter = rows.findByKey("INS_MIN").orElseThrow();
        parameters.update("INS_MIN", new ParameterUpdateRequest("2500", parameter.getVersion()));
        var claim = auditTransactions.claimBatch().getFirst();
        AuditEvent event = json.readValue(claim.payload(), AuditEvent.class);
        auditSink.append(event);
        auditSink.append(event); // Mongo driver insert-only duplicate path, not a mocked sink.
        assertThat(mongo.getCollection("audit_logs").countDocuments(new org.bson.Document("eventId", claim.eventId().toString())))
                .isEqualTo(1);
        assertThat(auditTransactions.acknowledge(claim)).isTrue();
        assertThat(auditTransactions.acknowledge(claim)).isFalse();
    }

    @Test @Order(4)
    void realAuthenticatedAdminApiSupportsUpdateAndSimulationWithoutSendingEmail() throws Exception {
        SecurityContextHolder.clearContext();
        createAdmin();
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var csrfBody = json.readTree(csrf.getResponse().getContentAsByteArray()).path("data");
        String csrfHeader = csrf.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        var csrfCookie = new Cookie("XSRF-TOKEN", csrfHeader.substring("XSRF-TOKEN=".length()).split(";", 2)[0]);
        var login = mvc.perform(post("/api/v1/auth/login").cookie(csrfCookie)
                .header(csrfBody.path("headerName").asText(), csrfBody.path("token").asText())
                .contentType("application/json").content(json.writeValueAsString(Map.of("identifier", "uc46-admin@example.invalid", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(login.getResponse().getContentAsByteArray()).path("data").path("accessToken").asText();
        mvc.perform(get("/api/v1/admin/params").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(109));
        mvc.perform(post("/api/v1/admin/params/simulate").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"quote\":{\"distanceKm\":8,\"weightKg\":3,\"longestSideCm\":40,\"declaredValue\":500000},\"overrides\":{\"BASE_FARE\":\"14000\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("REFERENCE"))
                .andExpect(jsonPath("$.data.balanceDifference").value(0));
        assertThat(rows.findByKey("BASE_FARE").orElseThrow().getValue()).isEqualTo("12000");
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test @Order(5)
    @EnabledIfEnvironmentVariable(named = "FPTPOST_UC46_BROWSER_TEST", matches = "1")
    void exposeIsolatedRealApiForBrowserAcceptance() throws Exception {
        createAdmin();
        // Synthetic credentials belong only to this isolated, disposable test database.
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "fptpost_uc46_browser");
        Files.createDirectories(directory);
        Files.deleteIfExists(directory.resolve("complete"));
        Files.writeString(directory.resolve("ready.json"), json.writeValueAsString(Map.of(
                "apiPort", port, "database", DATABASE, "email", "uc46-admin@example.invalid", "password", PASSWORD)));
        for (int attempt = 0; attempt < 600 && !Files.exists(directory.resolve("complete")); attempt++) Thread.sleep(1000);
        assertThat(directory.resolve("complete")).exists();
    }

    private void createAdmin() {
        if (users.existsByEmail("uc46-admin@example.invalid")) return;
        Instant now = Instant.now();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var user = users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email("uc46-admin@example.invalid")
                    .phone("+84990000046").passwordHash(encoder.encode(PASSWORD)).status(UserStatus.ACTIVE)
                    .emailVerifiedAt(now).createdAt(now).updatedAt(now)
                    .roles(new HashSet<>(Set.of(roles.findByCode("ADMIN").orElseThrow()))).build());
            profiles.saveAndFlush(UserProfile.builder().user(user).fullName("Quản trị kiểm thử UC-46").adultConfirmedAt(now).build());
        });
    }

    private String concurrentUpdate(CountDownLatch start, String key, String value) throws InterruptedException {
        authenticate();
        try {
            start.await(); parameters.update(key, new ParameterUpdateRequest(value, 0L)); return "UPDATED";
        } catch (com.fptpost.common.exception.AppException exception) { return exception.getErrorCode().getCode(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private static void authenticate() {
        var now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("integration").header("alg", "HS256").subject(ACTOR.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterAll static void cleanupDatabase() throws Exception {
        if (!created || "1".equals(System.getenv("FPTPOST_UC46_KEEP_TEST_DATABASE"))) return;
        if (!DATABASE.matches("fptpost_uc46_it_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe cleanup target");
        try (var connection = DriverManager.getConnection(sqlUrl("master"), "sa", System.getenv("FPTPOST_UC46_SQL_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE [" + DATABASE + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            statement.execute("DROP DATABASE [" + DATABASE + "]");
        }
        try (var mongo = com.mongodb.client.MongoClients.create("mongodb://" + MONGO_HOST)) {
            mongo.getDatabase(DATABASE).drop();
        }
    }
}

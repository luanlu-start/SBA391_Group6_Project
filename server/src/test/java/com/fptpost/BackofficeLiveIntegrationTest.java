package com.fptpost;

import com.fptpost.common.audit.AuditOutboxDispatcher;
import com.fptpost.modules.identity.model.KycRequest;
import com.fptpost.modules.notification.service.NotificationInboxStore;
import com.fptpost.modules.notification.service.NotificationOutboxDispatcher;
import com.fptpost.modules.notification.service.NotificationOutboxTransactions;
import com.fptpost.modules.notification.service.NotificationDeliveryClaim;
import com.fptpost.modules.notification.enums.NotificationChannel;
import jakarta.persistence.Column;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Acceptance against disposable SQL Server/MongoDB stores; never uses the application database. */
@EnabledIfEnvironmentVariable(named = "FPTPOST_BACKOFFICE_LIVE_TEST", matches = "1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test", "spring.jpa.hibernate.ddl-auto=update", "spring.sql.init.mode=never",
        "audit.outbox.enabled=false", "audit.retention.enabled=false", "audit.indexes.enabled=false",
        "notification.outbox.enabled=false", "app.kyc.review-sla-enabled=false", "app.chat.initialize-indexes=false",
        "management.health.mail.enabled=false", "app.registration.requests-per-minute=1000",
        "app.authentication.session-cleanup-cron=-", "app.cors.allowed-origins=http://localhost:15174,http://127.0.0.1:15174"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackofficeLiveIntegrationTest extends KycReviewFlowHttpTest {
    private static final String DATABASE = "fptpost_backoffice_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SQL_HOST = "localhost:" + System.getenv().getOrDefault("FPTPOST_BACKOFFICE_SQL_PORT", "31433");
    private static final String MONGO_HOST = "localhost:" + System.getenv().getOrDefault("FPTPOST_BACKOFFICE_MONGO_PORT", "37017");
    private static final Path PRIVATE_FILES = IdentityFeatureHttpTest.files;
    private static boolean created;
    private static long realtimeLatencyMillis;
    @Autowired MongoTemplate mongo;
    @Autowired NotificationOutboxDispatcher notificationDispatcher;
    @Autowired NotificationOutboxTransactions notificationTransactions;
    @Autowired NotificationInboxStore inbox;
    @Autowired AuditOutboxDispatcher auditDispatcher;
    @Autowired SimpUserRegistry socketUsers;
    @LocalServerPort int port;

    @DynamicPropertySource static void liveDatabases(DynamicPropertyRegistry registry) throws Exception {
        String password = System.getenv("FPTPOST_BACKOFFICE_SQL_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Live SQL credentials are required");
        verifyDatabase();
        if (!created) {
            try (var connection = DriverManager.getConnection(sqlUrl("master"), "sa", password);
                 var statement = connection.createStatement()) { statement.execute("CREATE DATABASE [" + DATABASE + "]"); }
            created = true;
            String evidence = System.getenv("FPTPOST_BACKOFFICE_EVIDENCE_DIR");
            if (evidence != null && !evidence.isBlank()) {
                Path directory = Path.of(evidence).toAbsolutePath().normalize(); Files.createDirectories(directory);
                String ownership = "{\"database\":\"" + DATABASE + "\",\"privateFiles\":\""
                        + PRIVATE_FILES.toString().replace("\\", "/") + "\"}";
                Files.writeString(directory.resolve("owned-database.json"), ownership);
                Files.writeString(directory.resolve("owned-" + DATABASE + ".json"), ownership);
            }
        }
        registry.add("spring.datasource.url", () -> sqlUrl(DATABASE));
        registry.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.data.mongodb.uri", () -> "mongodb://" + MONGO_HOST + "/" + DATABASE + "?serverSelectionTimeoutMS=3000");
        registry.add("app.files.directory", PRIVATE_FILES::toString);
    }

    @Test @Order(1) void realMongoInboxIsPrivateAndReplayPreservesReadState() throws Exception {
        String request = submitted(user, token, "123456789012");
        write("POST", URL + "/" + request + "/approve", Map.of("expectedVersion", 0), moderatorToken, 200);
        assertThat(notificationDispatcher.dispatchInbox().delivered()).isEqualTo(1);
        var page = data(send(get("/api/v1/notifications").header("Authorization", "Bearer " + token), 200));
        assertThat(page.path("totalElements").asInt()).isEqualTo(1);
        UUID notificationId = UUID.fromString(page.path("content").get(0).path("id").asText());
        assertThat(data(send(get("/api/v1/notifications").header("Authorization", "Bearer " + moderatorToken), 200))
                .path("totalElements").asInt()).isZero();
        write("POST", "/api/v1/notifications/" + notificationId + "/read", Map.of(), moderatorToken, 404);
        var read = data(write("POST", "/api/v1/notifications/" + notificationId + "/read", Map.of(), token, 200));
        assertThat(read.path("readAt").asText()).isNotBlank();
        var event = notifications.findById(notificationId).orElseThrow();
        assertThat(inbox.append(event).readAt()).isNotNull();
        assertThat(data(write("POST", "/api/v1/notifications/" + notificationId + "/read", Map.of(), token, 200))
                .path("readAt").asText()).isEqualTo(read.path("readAt").asText());
        assertThat(data(send(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + token), 200))
                .path("count").asInt()).isZero();
        assertThat(auditDispatcher.dispatchBatch().delivered()).isEqualTo(1);
        var audit = data(send(get("/api/v1/admin/audit-logs").param("entityType", "KYC_REQUEST")
                .header("Authorization", "Bearer " + administratorToken), 200));
        assertThat(audit.path("content").toString()).contains("KYC_APPROVED").doesNotContain("123456789012");
        assertThat(delivered).isEmpty();
    }

    @Test @Order(2) void settingsAndReadAllRemainBoundToCurrentUser() throws Exception {
        var optional = Map.of("settings", List.of(Map.of("category", "PROMOTION", "channels",
                Map.of("IN_APP", false, "EMAIL", false, "SMS", false))));
        write("PUT", "/api/v1/me/notification-settings", optional, token, 200);
        var own = data(send(get("/api/v1/me/notification-settings").header("Authorization", "Bearer " + token), 200));
        var other = data(send(get("/api/v1/me/notification-settings").header("Authorization", "Bearer " + moderatorToken), 200));
        assertThat(own.path("settings").toString()).doesNotContain("webPush");
        assertThat(own.path("settings").toString()).isNotEqualTo(other.path("settings").toString());
        var mandatory = Map.of("settings", List.of(Map.of("category", "KYC", "channels",
                Map.of("IN_APP", false, "EMAIL", false, "SMS", false))));
        write("PUT", "/api/v1/me/notification-settings", mandatory, token, 400);
        String request = submitted(user, token, "123456789012");
        write("POST", URL + "/" + request + "/approve", Map.of("expectedVersion", 0), moderatorToken, 200);
        notificationDispatcher.dispatchInbox();
        write("POST", "/api/v1/notifications/read-all", Map.of(), moderatorToken, 200);
        assertThat(inbox.unreadCount(user.getPublicId())).isEqualTo(1);
        write("POST", "/api/v1/notifications/read-all", Map.of(), token, 200);
        assertThat(inbox.unreadCount(user.getPublicId())).isZero();
    }

    @Test @Order(3) void privateStompDeliveryReachesOnlyTheRecipient() throws Exception {
        now.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        token = access(login(user.getEmail(), "OldPassword123!", 200));
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        var recipientMessages = new Frames(); var moderatorMessages = new Frames();
        var http = HttpClient.newHttpClient();
        var recipientSocket = connect(http, token, recipientMessages);
        var moderatorSocket = connect(http, moderatorToken, moderatorMessages);
        try {
            subscribe(recipientSocket); subscribe(moderatorSocket);
            awaitSubscription(user.getPublicId()); awaitSubscription(moderator.getPublicId());
            String request = submitted(user, token, "123456789012");
            write("POST", URL + "/" + request + "/approve", Map.of("expectedVersion", 0), moderatorToken, 200);
            long started = System.nanoTime();
            assertThat(notificationDispatcher.dispatchInbox().delivered()).isEqualTo(1);
            String frame = recipientMessages.await("MESSAGE", Duration.ofSeconds(5));
            realtimeLatencyMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertThat(frame).contains("Hồ sơ xác minh đã được duyệt");
            assertThat(moderatorMessages.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            recipientSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
            moderatorSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }
    }

    @Test @Order(4) void sqlServerBackfillsTheFlagDefaultForLegacyRows() throws Exception {
        String definition = KycRequest.class.getDeclaredField("flagged").getAnnotation(Column.class).columnDefinition();
        assertThat(definition).isEqualTo("bit default 0");
        try (var connection = DriverManager.getConnection(sqlUrl(DATABASE), "sa", System.getenv("FPTPOST_BACKOFFICE_SQL_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE kyc_upgrade_probe (id bigint PRIMARY KEY)");
            statement.execute("INSERT INTO kyc_upgrade_probe (id) VALUES (1)");
            statement.execute("ALTER TABLE kyc_upgrade_probe ADD flagged " + definition + " NOT NULL");
            try (var result = statement.executeQuery("SELECT flagged FROM kyc_upgrade_probe WHERE id=1")) {
                assertThat(result.next()).isTrue(); assertThat(result.getBoolean(1)).isFalse();
            }
            statement.execute("DROP TABLE kyc_upgrade_probe");
        }
    }

    @Test @Order(5) void twoSqlWorkersClaimDisjointDeliveriesAndFenceRepeatedAcknowledgements() throws Exception {
        String request = submitted(user, token, "123456789012");
        write("POST", URL + "/" + request + "/approve", Map.of("expectedVersion", 0), moderatorToken, 200);
        var start = new CountDownLatch(1);
        List<NotificationDeliveryClaim> claimed = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return notificationTransactions.claimBatch(EnumSet.allOf(NotificationChannel.class)); });
            var second = executor.submit(() -> { start.await(); return notificationTransactions.claimBatch(EnumSet.allOf(NotificationChannel.class)); });
            start.countDown();
            var one = first.get(20, TimeUnit.SECONDS); var two = second.get(20, TimeUnit.SECONDS);
            assertThat(Collections.disjoint(one.stream().map(NotificationDeliveryClaim::id).toList(),
                    two.stream().map(NotificationDeliveryClaim::id).toList())).isTrue();
            claimed.addAll(one); claimed.addAll(two);
        }
        assertThat(claimed).hasSize(2);
        assertThat(claimed).extracting(NotificationDeliveryClaim::channel).containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
        for (var claim : claimed) {
            assertThat(notificationTransactions.renew(claim)).isTrue();
            var otherToken = new NotificationDeliveryClaim(claim.id(), UUID.randomUUID(), claim.channel(), claim.attempt(), claim.event());
            assertThat(notificationTransactions.complete(otherToken, false)).isFalse();
            assertThat(notificationTransactions.complete(claim, false)).isTrue();
            assertThat(notificationTransactions.complete(claim, false)).isFalse();
        }
        assertThat(delivered).isEmpty();
    }

    @Test @Order(10) @EnabledIfEnvironmentVariable(named = "FPTPOST_BACKOFFICE_BROWSER_TEST", matches = "1")
    void exposeDisposableServerForBrowserAcceptance() throws Exception {
        now.set(Instant.now());
        user.setEmail("backoffice-member@example.invalid"); user = users.saveAndFlush(user);
        token = access(login(user.getEmail(), "OldPassword123!", 200));
        moderatorToken = access(login(moderator.getEmail(), "OldPassword123!", 200));
        administratorToken = access(login(administrator.getEmail(), "OldPassword123!", 200));
        String first = submitted(user, token, "123456789012");
        var second = createUser("backoffice-second@example.invalid", "+84812345678");
        String secondToken = access(login(second.getEmail(), "OldPassword123!", 200));
        String secondId = submitted(second, secondToken, "123456789013");
        var third = createUser("backoffice-third@example.invalid", "+84812345679");
        String thirdToken = access(login(third.getEmail(), "OldPassword123!", 200));
        String thirdId = submitted(third, thirdToken, "123456789014");
        var overdue = kycRequests.findByPublicId(UUID.fromString(thirdId)).orElseThrow();
        overdue.setSubmittedAt(now.get().minus(Duration.ofHours(25))); kycRequests.saveAndFlush(overdue);
        alerts.alert(new com.fptpost.modules.identity.repository.KycReviewSearchRepository.Candidate(UUID.fromString(thirdId), third.getPublicId()), 24, now.get());
        Path evidence = evidenceDirectory(); Files.createDirectories(evidence);
        Files.deleteIfExists(evidence.resolve("complete"));
        Files.writeString(evidence.resolve("ready.json"), json.writeValueAsString(Map.of(
                "apiPort", port, "database", DATABASE, "password", "OldPassword123!",
                "adminEmail", administrator.getEmail(), "moderatorEmail", moderator.getEmail(),
                "senderEmail", user.getEmail(), "requests", List.of(first, secondId, thirdId),
                "realtimeLatencyMillis", realtimeLatencyMillis)));
        long deadline = System.nanoTime() + Duration.ofMinutes(20).toNanos();
        while (!Files.exists(evidence.resolve("complete")) && System.nanoTime() < deadline) {
            now.set(Instant.now());
            notificationDispatcher.dispatchInbox(); auditDispatcher.dispatchBatch(); Thread.sleep(250);
        }
        assertThat(evidence.resolve("complete")).exists();
    }

    @Override MockMultipartFile image(String field, String format) throws Exception {
        BufferedImage image = new BufferedImage(520, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(244, 246, 250)); graphics.fillRect(0, 0, 520, 320);
            graphics.setColor(new Color(243, 112, 33)); graphics.fillRect(0, 0, 520, 60);
            graphics.setColor(Color.BLACK); graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            graphics.drawString("FPTPost - KYC TEST FIXTURE", 24, 40);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            graphics.drawString("Synthetic image, not an identity document", 24, 110);
            graphics.drawString("Document view: " + field, 24, 155);
            graphics.drawString("Applicant: Test Member", 24, 200);
            graphics.drawString("Identity reference: ********9012", 24, 245);
        } finally { graphics.dispose(); }
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, format, bytes);
        return new MockMultipartFile(field, "fixture." + format, "image/" + format, bytes.toByteArray());
    }

    private WebSocket connect(HttpClient client, String bearer, Frames frames) throws Exception {
        var socket = client.newWebSocketBuilder().header("Origin", "http://localhost:15174")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/chat"), frames).get(10, TimeUnit.SECONDS);
        socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer " + bearer + "\n\n\u0000", true).get(5, TimeUnit.SECONDS);
        frames.await("CONNECTED", Duration.ofSeconds(5));
        return socket;
    }
    private void subscribe(WebSocket socket) throws Exception {
        socket.sendText("SUBSCRIBE\nid:notifications\ndestination:/user/queue/notifications\nack:auto\n\n\u0000", true).get(5, TimeUnit.SECONDS);
    }
    private void awaitSubscription(UUID recipient) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var connected = socketUsers.getUser(recipient.toString());
            if (connected != null && connected.getSessions().stream().flatMap(session -> session.getSubscriptions().stream())
                    .anyMatch(subscription -> "/user/queue/notifications".equals(subscription.getDestination()))) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Private notification subscription was not registered");
    }
    private static final class Frames implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder buffer = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence value, boolean last) {
            buffer.append(value);
            if (last) { messages.offer(buffer.toString()); buffer.setLength(0); }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket socket, Throwable error) { messages.offer("ERROR: " + error.getClass().getSimpleName()); }
        String await(String command, Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                String frame = messages.poll(100, TimeUnit.MILLISECONDS);
                if (frame == null || frame.isBlank()) continue;
                if (frame.startsWith(command)) return frame;
                throw new AssertionError("Unexpected STOMP command: " + frame.split("\\n", 2)[0]);
            }
            throw new AssertionError("Timed out awaiting STOMP " + command);
        }
    }
    private static Path evidenceDirectory() {
        String configured = System.getenv("FPTPOST_BACKOFFICE_EVIDENCE_DIR");
        if (configured == null || configured.isBlank()) throw new IllegalStateException("External browser evidence directory is required");
        return Path.of(configured).toAbsolutePath().normalize();
    }
    private static String sqlUrl(String database) {
        return "jdbc:sqlserver://" + SQL_HOST + ";databaseName=" + database + ";encrypt=true;trustServerCertificate=true";
    }
    private static void verifyDatabase() {
        if (!DATABASE.matches("fptpost_backoffice_it_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe disposable database name");
    }
    @AfterAll static void cleanup(@Autowired DataSource source) throws Exception {
        if (!created) return;
        verifyDatabase();
        if (source instanceof com.zaxxer.hikari.HikariDataSource hikari) hikari.close();
        try (var client = com.mongodb.client.MongoClients.create("mongodb://" + MONGO_HOST + "/?serverSelectionTimeoutMS=3000")) {
            client.getDatabase(DATABASE).drop();
        }
        try (var connection = DriverManager.getConnection(sqlUrl("master"), "sa", System.getenv("FPTPOST_BACKOFFICE_SQL_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE [" + DATABASE + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            statement.execute("DROP DATABASE [" + DATABASE + "]");
        }
        Path resolved = PRIVATE_FILES.toRealPath();
        Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        if (!resolved.startsWith(temporaryRoot) || !resolved.getFileName().toString().startsWith("fpost-identity-tests-"))
            throw new IllegalStateException("Unsafe disposable file cleanup target");
        try (var files = Files.walk(resolved)) {
            for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}

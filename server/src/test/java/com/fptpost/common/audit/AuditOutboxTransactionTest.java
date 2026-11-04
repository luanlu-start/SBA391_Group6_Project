package com.fptpost.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fptpost.common.request.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:auditoutbox;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "audit.outbox.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditConfiguration.class, AuditOutboxService.class, AuditPayloadMasker.class,
        AuditOutboxTransactions.class, AuditOutboxDispatcher.class, AuditOutboxTransactionTest.Support.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditOutboxTransactionTest {
    @Autowired AuditOutboxService producer;
    @Autowired AuditOutboxRepository repository;
    @Autowired AuditOutboxTransactions claims;
    @Autowired AuditOutboxDispatcher dispatcher;
    @Autowired AuditOutboxProperties properties;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MongoAuditSink sink;
    UUID actor;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        reset(sink);
        clock.now.set(Instant.parse("2026-10-14T00:00:00Z"));
        properties.setMaxPayloadBytes(262144);
        properties.setBatchSize(25);
        jdbc.execute("create table if not exists audit_test_business (id bigint primary key, business_note nvarchar(100))");
        jdbc.update("delete from audit_test_business");
        actor = UUID.randomUUID();
        var jwt = Jwt.withTokenValue("trusted-test-token").header("alg", "HS256")
                .subject(actor.toString()).issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requiresExistingTransactionAndTrustedJwtActor() {
        assertThatThrownBy(this::enqueue).isInstanceOf(IllegalTransactionStateException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> enqueue()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT actor");
        assertThat(repository.count()).isZero();
    }

    @Test
    void businessAndOutboxCommitTogetherAndWorkerRunsAfterCommit() {
        transaction().executeWithoutResult(status -> {
            jdbc.update("insert into audit_test_business (id, business_note) values (1, ?)", "Tham số tiếng Việt");
            enqueue();
            verifyNoInteractions(sink);
        });
        assertThat(jdbc.queryForObject("select count(*) from audit_test_business", Long.class)).isEqualTo(1);
        assertThat(repository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo(AuditOutboxEntity.Status.PENDING);
            assertThat(row.getPayload()).contains("Tham số tiếng Việt");
        });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(sink).append(any());
        assertThat(dispatcher.dispatchBatch()).isEqualTo(new AuditOutboxDispatcher.DispatchResult(1, 1, 0));
        verify(sink).append(any());
        assertThat(repository.findAll().getFirst().getStatus()).isEqualTo(AuditOutboxEntity.Status.DELIVERED);
    }

    @Test
    void rollbackLeavesNeitherBusinessMutationNorAuditEvent() {
        transaction().executeWithoutResult(status -> {
            jdbc.update("insert into audit_test_business (id, business_note) values (1, 'rollback')");
            enqueue();
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from audit_test_business", Long.class)).isZero();
        assertThat(repository.count()).isZero();
        assertThat(dispatcher.dispatchBatch().claimed()).isZero();
        verifyNoInteractions(sink);
    }

    @Test
    void captureUsesTrustedActorDirectIpCorrelationAndMasksNestedSecrets() throws Exception {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");
        request.addHeader("X-Forwarded-For", "untrusted-client");
        String correlation = UUID.randomUUID().toString();
        request.addHeader(CorrelationIdFilter.HEADER, correlation);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        Map<String, Object> before = Map.of("value", 5, "profile", Map.of("CCCD", "secret-national-id"),
                "attempts", List.of(Map.of("otp_code", "secret-otp")));
        Map<String, Object> after = Map.of("value", 10, "refreshToken", "secret-refresh-token");
        transaction().executeWithoutResult(status -> producer.enqueue("PARAMETER_UPDATED", "SYSTEM_PARAMETER",
                "PRM-001", before, after));
        String payload = repository.findAll().getFirst().getPayload();
        assertThat(payload).doesNotContain("secret-national-id", "secret-otp", "secret-refresh-token", "untrusted-client");
        var event = json.readValue(payload, AuditEvent.class);
        assertThat(event.actorPublicId()).isEqualTo(actor);
        assertThat(event.ip()).isEqualTo("2001:db8::1");
        assertThat(event.correlationId()).isEqualTo(correlation);
        assertThat(event.before().get("value")).isEqualTo(5);
        assertThat(event.after()).containsEntry("value", 10).containsEntry("refreshToken", "[REDACTED]");
        assertThat(event.at()).isEqualTo(clock.instant());
    }

    @Test
    void oversizedAuditRollsBackTheBusinessMutation() {
        properties.setMaxPayloadBytes(1024);
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            jdbc.update("insert into audit_test_business (id, business_note) values (1, 'oversized')");
            producer.enqueue("PARAMETER_UPDATED", "SYSTEM_PARAMETER", "PRM-001", Map.of(),
                    Map.of("value", "nội dung".repeat(1000)));
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("payload limit");
        assertThat(repository.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_test_business", Long.class)).isZero();
    }

    @Test
    void mongoOutageRetainsEventAndRetriesSameEventAfterBackoff() {
        transaction().executeWithoutResult(status -> enqueue());
        UUID eventId = repository.findAll().getFirst().getEventId();
        doThrow(new DataAccessResourceFailureException("secret connection info")).doNothing().when(sink).append(any());
        assertThat(dispatcher.dispatchBatch()).isEqualTo(new AuditOutboxDispatcher.DispatchResult(1, 0, 1));
        var pending = repository.findAll().getFirst();
        assertThat(pending.getStatus()).isEqualTo(AuditOutboxEntity.Status.PENDING);
        assertThat(pending.getAttempts()).isEqualTo(1);
        assertThat(pending.getLastError()).isEqualTo("DataAccessResourceFailureException");
        assertThat(pending.getCompletedAt()).isNull();
        assertThat(dispatcher.dispatchBatch().claimed()).isZero();
        clock.advance(properties.getRetryBaseDelay());
        assertThat(dispatcher.dispatchBatch()).isEqualTo(new AuditOutboxDispatcher.DispatchResult(1, 1, 0));
        assertThat(repository.findAll().getFirst().getEventId()).isEqualTo(eventId);
        assertThat(repository.findAll().getFirst().getAttempts()).isEqualTo(2);
        verify(sink, times(2)).append(argThat(event -> event.eventId().equals(eventId)));
    }

    @Test
    void crashedWorkerLeaseExpiresAndStaleWorkerCannotChangeRecoveredClaim() {
        transaction().executeWithoutResult(status -> enqueue());
        var abandoned = claims.claimBatch().getFirst();
        assertThat(claims.claimBatch()).isEmpty();
        clock.advance(properties.getLeaseDuration());
        var recovered = claims.claimBatch().getFirst();
        assertThat(recovered.eventId()).isEqualTo(abandoned.eventId());
        assertThat(recovered.claimToken()).isNotEqualTo(abandoned.claimToken());
        assertThat(claims.acknowledge(abandoned)).isFalse();
        assertThat(claims.retry(abandoned, new RuntimeException())).isFalse();
        assertThat(claims.acknowledge(recovered)).isTrue();
        assertThat(repository.findAll().getFirst().getStatus()).isEqualTo(AuditOutboxEntity.Status.DELIVERED);
    }

    @Test
    void simultaneousWorkersClaimEachEventOnlyOnce() throws Exception {
        transaction().executeWithoutResult(status -> enqueue());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> workers.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Worker start timed out");
                return claims.claimBatch();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = tasks.getFirst().get(10, TimeUnit.SECONDS);
            var second = tasks.getLast().get(10, TimeUnit.SECONDS);
            assertThat(first.size() + second.size()).isEqualTo(1);
            assertThat(repository.findAll().getFirst().getAttempts()).isEqualTo(1);
        }
    }

    @Test
    void retryDelayGrowsAndIsCappedWithoutDroppingEvent() {
        transaction().executeWithoutResult(status -> enqueue());
        doThrow(new DataAccessResourceFailureException("down")).when(sink).append(any());
        for (int attempt = 1; attempt <= 11; attempt++) {
            assertThat(dispatcher.dispatchBatch().retried()).isEqualTo(1);
            var row = repository.findAll().getFirst();
            Duration expected = Duration.ofSeconds(Math.min(300, 2L << (attempt - 1)));
            assertThat(row.getNextAttemptAt()).isEqualTo(clock.instant().plus(expected));
            assertThat(row.getStatus()).isEqualTo(AuditOutboxEntity.Status.PENDING);
            clock.advance(expected);
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    private void enqueue() {
        producer.enqueue("PARAMETER_UPDATED", "SYSTEM_PARAMETER", "PRM-001", Map.of("value", 5),
                Map.of("value", 10, "note", "Tham số tiếng Việt"));
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }

    @TestConfiguration
    static class Support {
        @Bean MutableClock auditTestClock() { return new MutableClock(); }
        @Bean ObjectMapper auditTestObjectMapper() { return JsonMapper.builder().findAndAddModules().build(); }
    }

    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-14T00:00:00Z"));
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}

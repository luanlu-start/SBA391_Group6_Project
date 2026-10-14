package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.audit.*;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.config.ParameterDataInitializer;
import com.fptpost.modules.catalog.dto.request.ParameterBatchUpdateRequest;
import com.fptpost.modules.catalog.dto.request.ParameterUpdateRequest;
import com.fptpost.modules.catalog.mapper.ParameterMapper;
import com.fptpost.modules.catalog.repository.ParameterHistoryRepository;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import com.fptpost.modules.catalog.service.ParameterCatalog;
import com.fptpost.modules.catalog.service.ParameterService;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:parametertx;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ParameterService.class, ParameterCatalog.class, ParameterDataInitializer.class,
        AuditOutboxService.class, AuditPayloadMasker.class, ParameterTransactionTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParameterTransactionTest {
    static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");
    static final UUID ACTOR = UUID.fromString("a914bf06-62cd-43a3-9672-982d40a32f6a");
    @Autowired ParameterService service;
    @Autowired SystemParameterRepository parameters;
    @Autowired ParameterHistoryRepository history;
    @Autowired AuditOutboxRepository outbox;
    @Autowired ParameterDataInitializer initializer;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AuditOutboxService audit;
    AuditOutboxService auditTarget;

    @BeforeEach void initialize() {
        auditTarget = AopTestUtils.getUltimateTargetObject(audit);
        history.deleteAll(); outbox.deleteAll(); parameters.deleteAll(); initializer.run();
        authenticate("ADMIN");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void commitsParameterHistoryAndAuditWithTrustedActorAndVersionInOneTransaction() throws Exception {
        var oldOrder = service.getSnapshot();
        var changed = service.update("BASE_FARE", request("12000", 0));
        assertThat(changed.getValue()).isEqualTo("12000");
        assertThat(changed.getVersion()).isEqualTo(1);
        assertThat(changed.getEffectiveFrom()).isEqualTo(NOW);
        assertThat(changed.getUpdatedBy()).isEqualTo(ACTOR);
        assertThat(history.count()).isEqualTo(1);
        assertThat(service.history("BASE_FARE", 0, 20).content()).singleElement().satisfies(row -> {
            assertThat(row.getOldValue()).isEqualTo("10000"); assertThat(row.getNewValue()).isEqualTo("12000");
            assertThat(row.getChangedBy()).isEqualTo(ACTOR); assertThat(row.getVersion()).isEqualTo(1);
        });
        var event = new ObjectMapper().findAndRegisterModules().readTree(outbox.findAll().getFirst().getPayload());
        assertThat(event.path("actorPublicId").asText()).isEqualTo(ACTOR.toString());
        assertThat(event.path("before").path("value").asText()).isEqualTo("10000");
        assertThat(event.path("after").path("version").asLong()).isEqualTo(1);
        assertThat(oldOrder.value("BASE_FARE")).isEqualTo("10000");
        initializer.run();
        assertThat(parameters.findByKey("BASE_FARE").orElseThrow().getValue()).isEqualTo("12000");
    }

    @Test void previewAndNoOpAreSideEffectFreeAndStaleVersionCannotOverwrite() {
        assertThat(service.proposedSnapshot(Map.of("BASE_FARE", "15000")).value("BASE_FARE")).isEqualTo("15000");
        assertThat(parameters.findByKey("BASE_FARE").orElseThrow().getValue()).isEqualTo("10000");
        assertThat(service.update("BASE_FARE", request("10000.0", 0)).getVersion()).isZero();
        assertThat(history.count()).isZero(); assertThat(outbox.count()).isZero();
        service.update("BASE_FARE", request("12000", 0));
        assertThatThrownBy(() -> service.update("BASE_FARE", request("13000", 0)))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode().getCode())
                .isEqualTo("PARAMETER_VERSION_CONFLICT");
        assertThat(history.count()).isEqualTo(1); assertThat(outbox.count()).isEqualTo(1);
    }

    @Test void outerRollbackAndAuditFailureLeaveNoStateHistoryOrOutboxChange() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            service.update("BASE_FARE", request("12000", 0));
            assertThat(outbox.count()).isEqualTo(1);
            transaction.setRollbackOnly();
        });
        assertUnchanged();
        doThrow(new IllegalStateException("Capture failed")).when(auditTarget).enqueue(anyString(), anyString(), anyString(), anyMap(), anyMap());
        assertThatThrownBy(() -> service.update("BASE_FARE", request("13000", 0))).isInstanceOf(IllegalStateException.class);
        assertUnchanged();
    }

    @Test void batchKeepsWeightTotalValidAndFailureAfterFirstChangeRollsBackEntireBatch() {
        var response = service.updateBatch(new ParameterBatchUpdateRequest(Map.of(
                "W_ONTIME", request("0.40", 0), "W_RATING", request("0.25", 0))));
        assertThat(response).hasSize(2);
        assertThat(history.count()).isEqualTo(2); assertThat(outbox.count()).isEqualTo(2);
        assertThat(service.getSnapshot().decimal("W_ONTIME")).isEqualByComparingTo("0.4");

        doAnswer(invocation -> {
            invocation.callRealMethod();
            if ("PER_KM".equals(invocation.getArgument(2))) throw new IllegalStateException("Second capture failed");
            return null;
        }).when(auditTarget).enqueue(anyString(), anyString(), anyString(), anyMap(), anyMap());
        assertThatThrownBy(() -> service.updateBatch(new ParameterBatchUpdateRequest(Map.of(
                "BASE_FARE", request("12000", 0), "PER_KM", request("4000", 0))))).isInstanceOf(IllegalStateException.class);
        assertThat(parameters.findByKey("BASE_FARE").orElseThrow().getValue()).isEqualTo("10000");
        assertThat(parameters.findByKey("PER_KM").orElseThrow().getValue()).isEqualTo("3500");
        assertThat(history.count()).isEqualTo(2); assertThat(outbox.count()).isEqualTo(2);
    }

    @Test void serviceRejectsNonAdminReadsAndWrites() {
        authenticate("SENDER");
        assertThatThrownBy(service::list).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.history("BASE_FARE", 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update("BASE_FARE", request("12000", 0))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.updateBatch(new ParameterBatchUpdateRequest(Map.of("BASE_FARE", request("12000", 0)))))
                .isInstanceOf(AccessDeniedException.class);
        assertUnchanged();
    }

    @Test void concurrentDifferentKeysCannotCommitAnInvalidSizeRelationship() throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrentUpdate(start, "SIZE_S", "49"));
            Future<String> second = executor.submit(() -> concurrentUpdate(start, "SIZE_M", "40"));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("UPDATED", "PARAMETER_CONSTRAINT_VIOLATION");
            var finalValues = service.getSnapshot();
            assertThat(finalValues.decimal("SIZE_S")).isLessThan(finalValues.decimal("SIZE_M"));
            assertThat(history.count()).isEqualTo(1); assertThat(outbox.count()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private String concurrentUpdate(CountDownLatch start, String key, String value) throws InterruptedException {
        authenticate("ADMIN");
        try {
            start.await(); service.update(key, request(value, 0)); return "UPDATED";
        } catch (AppException exception) { return exception.getErrorCode().getCode(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void assertUnchanged() {
        var parameter = parameters.findByKey("BASE_FARE").orElseThrow();
        assertThat(parameter.getValue()).isEqualTo("10000"); assertThat(parameter.getVersion()).isZero();
        assertThat(history.count()).isZero(); assertThat(outbox.count()).isZero();
    }
    private static ParameterUpdateRequest request(String value, long version) { return new ParameterUpdateRequest(value, version); }
    private static void authenticate(String role) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject(ACTOR.toString())
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @TestConfiguration @EnableMethodSecurity
    static class Configuration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean ParameterMapper parameterMapper() { return Mappers.getMapper(ParameterMapper.class); }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean AuditOutboxProperties auditProperties() { return new AuditOutboxProperties(); }
    }
}

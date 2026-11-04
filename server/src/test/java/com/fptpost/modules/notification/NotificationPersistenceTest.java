package com.fptpost.modules.notification;

import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import com.fptpost.modules.notification.config.NotificationOutboxProperties;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.repository.*;
import com.fptpost.modules.notification.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.datasource.url=jdbc:h2:mem:notificationpersistence;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "notification.outbox.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationPublisher.class, NotificationRecipientLookup.class, NotificationOutboxTransactions.class, NotificationPersistenceTest.Configuration.class})
class NotificationPersistenceTest {
    @Autowired NotificationPublisher publisher;
    @Autowired NotificationEventRepository events;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired UserRepository users;
    @Autowired NotificationOutboxTransactions transactions;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean NotificationSettingsService settings;

    @BeforeEach void setup() { when(settings.channels(any(), eq(NotificationCategory.KYC))).thenReturn(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)); }

    @Test void durableRecipientEventKeyDeduplicatesAndBothChannelsReferenceTheSameEvent() {
        var owner = user();
        publisher.publish(owner.getPublicId(), NotificationCategory.KYC, "kyc:dedupe", "Kết quả", "Hồ sơ đã duyệt", "/me/kyc");
        publisher.publish(owner.getPublicId(), NotificationCategory.KYC, "kyc:dedupe", "Kết quả", "Hồ sơ đã duyệt", "/me/kyc");
        assertThat(events.count()).isEqualTo(1); assertThat(deliveries.count()).isEqualTo(2);
        assertThat(deliveries.findAll()).extracting(value -> value.getEvent().getId()).containsOnly(events.findAll().getFirst().getId());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void businessRollbackRemovesBothEventAndDeliveryRecords() {
        var owner = user();
        try {
            assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> {
                publisher.publish(owner.getPublicId(), NotificationCategory.KYC, "kyc:rollback", "Kết quả", "Hồ sơ đã duyệt", "/me/kyc");
                throw new IllegalStateException("business operation rejected");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(events.count()).isZero(); assertThat(deliveries.count()).isZero();
        } finally { users.deleteById(owner.getId()); }
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void claimQueryLocksCommittedRowsAndLoadsPayloadOutsideTheTransaction() {
        var owner = user();
        try {
            publisher.publish(owner.getPublicId(), NotificationCategory.KYC, "kyc:claim", "Kết quả", "Hồ sơ đã duyệt", "/me/kyc");
            var claims = transactions.claimBatch(Set.of(NotificationChannel.IN_APP));
            assertThat(claims).hasSize(1);
            assertThat(claims.getFirst().event().getBody()).isEqualTo("Hồ sơ đã duyệt");
            assertThat(transactions.claimBatch(Set.of(NotificationChannel.IN_APP))).isEmpty();
            assertThat(transactions.complete(claims.getFirst(), false)).isTrue();
            assertThat(transactions.complete(claims.getFirst(), false)).isFalse();
            assertThat(deliveries.findById(claims.getFirst().id()).orElseThrow().getStatus()).isEqualTo(NotificationDeliveryStatus.DELIVERED);
        } finally { deliveries.deleteAll(); events.deleteAll(); users.deleteById(owner.getId()); }
    }

    private User user() {
        var now = Instant.parse("2026-10-29T00:00:00Z");
        var suffix = UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email("notification-" + suffix + "@example.invalid")
                .phone("09" + String.format(Locale.ROOT, "%08d", Math.abs(suffix.hashCode() % 100000000)))
                .passwordHash("x".repeat(60)).status(UserStatus.ACTIVE).emailVerifiedAt(now).createdAt(now).updatedAt(now).build());
    }

    @TestConfiguration
    static class Configuration {
        @Bean Clock notificationTestClock() { return Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC); }
        @Bean NotificationOutboxProperties notificationTestProperties() { return new NotificationOutboxProperties(); }
    }
}

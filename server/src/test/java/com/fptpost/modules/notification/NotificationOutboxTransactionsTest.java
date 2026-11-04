package com.fptpost.modules.notification;

import com.fptpost.modules.notification.config.NotificationOutboxProperties;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.model.*;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.service.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationOutboxTransactionsTest {
    private final Instant now = Instant.parse("2026-10-29T00:00:00Z");
    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final NotificationOutboxTransactions transactions = new NotificationOutboxTransactions(repository, new NotificationOutboxProperties(), Clock.fixed(now, ZoneOffset.UTC));
    private NotificationDelivery row;

    @BeforeEach void setup() {
        row = NotificationDelivery.builder().id(UUID.randomUUID()).event(NotificationEvent.builder().id(UUID.randomUUID()).build())
                .channel(NotificationChannel.EMAIL).status(NotificationDeliveryStatus.PROCESSING).attempts(1).claimedAt(now)
                .claimToken(UUID.randomUUID()).nextAttemptAt(now).build();
        when(repository.lockById(row.getId())).thenReturn(Optional.of(row));
    }

    @Test void recoversExpiredLeaseButRechecksRowsAlreadyClaimedByAnotherInstance() {
        row.setClaimedAt(now.minusSeconds(121));
        var recent = NotificationDelivery.builder().id(UUID.randomUUID()).status(NotificationDeliveryStatus.PROCESSING).claimedAt(now).nextAttemptAt(now).build();
        when(repository.lockEligible(any(), any(), any(), any(), any(), any())).thenReturn(List.of(row, recent));
        var claims = transactions.claimBatch(Set.of(NotificationChannel.EMAIL));
        assertThat(claims).hasSize(1); assertThat(claims.getFirst().attempt()).isEqualTo(2);
        assertThat(row.getClaimedAt()).isEqualTo(now); assertThat(claims.getFirst().token()).isEqualTo(row.getClaimToken());
        assertThat(recent.getClaimToken()).isNull();
    }

    @Test void staleClaimCannotCompleteOrRetryAnotherWorkersDelivery() {
        var stale = new NotificationDeliveryClaim(row.getId(), UUID.randomUUID(), row.getChannel(), 1, row.getEvent());
        assertThat(transactions.complete(stale, false)).isFalse();
        assertThat(transactions.renew(stale)).isFalse();
        assertThat(transactions.retry(stale, 3, new IllegalStateException())).isEmpty();
        assertThat(row.getStatus()).isEqualTo(NotificationDeliveryStatus.PROCESSING);
    }

    @Test void retriesExactlyThreeTimesAfterInitialAttemptAndThenFailsDurably() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            row.setAttempts(attempt); row.setStatus(NotificationDeliveryStatus.PROCESSING); row.setClaimToken(UUID.randomUUID());
            var result = transactions.retry(claim(), 3, new IllegalStateException("secret contact details must not be stored"));
            assertThat(result).contains(attempt == 4);
            assertThat(row.getLastFailure()).isEqualTo("IllegalStateException");
            assertThat(row.getStatus()).isEqualTo(attempt == 4 ? NotificationDeliveryStatus.FAILED : NotificationDeliveryStatus.PENDING);
            assertThat(row.getClaimToken()).isNull();
            assertThat(row.getNextAttemptAt()).isEqualTo(now.plusSeconds(5L << (attempt - 1)));
        }
        assertThat(row.getCompletedAt()).isEqualTo(now);
    }

    @Test void retryZeroFailsAtFirstFailureAndBackoffIsCapped() {
        assertThat(transactions.retry(claim(), 0, new IllegalStateException())).contains(true);
        row.setStatus(NotificationDeliveryStatus.PROCESSING); row.setClaimToken(UUID.randomUUID()); row.setAttempts(15);
        transactions.retry(claim(), 20, new IllegalStateException());
        assertThat(row.getNextAttemptAt()).isEqualTo(now.plusSeconds(300));
    }

    @Test void circuitDeferralDoesNotConsumeAnAttempt() {
        transactions.defer(claim());
        assertThat(row.getAttempts()).isZero(); assertThat(row.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(row.getNextAttemptAt()).isEqualTo(now.plusSeconds(30));
    }

    private NotificationDeliveryClaim claim() { return new NotificationDeliveryClaim(row.getId(), row.getClaimToken(), row.getChannel(), row.getAttempts(), row.getEvent()); }
}

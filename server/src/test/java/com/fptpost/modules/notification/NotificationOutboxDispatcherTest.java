package com.fptpost.modules.notification;

import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.notification.config.NotificationOutboxProperties;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.model.NotificationEvent;
import com.fptpost.modules.notification.service.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationOutboxDispatcherTest {
    private final NotificationOutboxTransactions transactions = mock(NotificationOutboxTransactions.class);
    private final NotificationDeliveryAdapter adapter = mock(NotificationDeliveryAdapter.class);
    private final NotificationOutboxProperties properties = new NotificationOutboxProperties();
    private final ParameterService parameters = mock(ParameterService.class);
    private final NotificationCircuitBreaker circuit = new NotificationCircuitBreaker(Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC));
    private final NotificationOutboxDispatcher dispatcher = new NotificationOutboxDispatcher(transactions, adapter, properties, circuit, parameters);

    @BeforeEach void setup() {
        when(parameters.getSnapshot()).thenReturn(new ParameterSnapshot(Map.of("NOTIF_RETRY", "3"), Map.of("NOTIF_RETRY", 1L), Instant.now()));
        when(transactions.renew(any())).thenReturn(true);
    }

    @Test void scheduledJobsCanBeDisabledWithoutTouchingDatabase() {
        properties.setEnabled(false); dispatcher.scheduledInbox(); dispatcher.scheduledExternal();
        verifyNoInteractions(transactions, adapter, parameters);
    }

    @Test void inboxDispatchIsIndependentOfSlowOrOpenEmailChannel() {
        var claim = claim(NotificationChannel.IN_APP, 1);
        for (int i = 0; i < 3; i++) circuit.failure(NotificationChannel.EMAIL);
        when(transactions.claimBatch(Set.of(NotificationChannel.IN_APP))).thenReturn(List.of(claim));
        when(adapter.deliver(claim)).thenReturn(true); when(transactions.complete(claim, false)).thenReturn(true);
        assertThat(dispatcher.dispatchInbox().delivered()).isEqualTo(1);
        verify(transactions).claimBatch(Set.of(NotificationChannel.IN_APP));
    }

    @Test void dynamicRetryLimitAndFailuresRemainDurable() {
        var claim = claim(NotificationChannel.EMAIL, 4);
        when(transactions.claimBatch(any())).thenReturn(List.of(claim));
        when(adapter.deliver(claim)).thenThrow(new IllegalStateException("private recipient"));
        when(transactions.retry(eq(claim), eq(3), any())).thenReturn(Optional.of(true));
        var result = dispatcher.dispatchBatch();
        assertThat(result.failed()).isEqualTo(1); assertThat(result.delivered()).isZero();
        verify(transactions).retry(eq(claim), eq(3), any(IllegalStateException.class));
    }

    @Test void leaseRecoveryCannotExceedDeliveryAttemptBudget() {
        var claim = claim(NotificationChannel.EMAIL, 5);
        when(transactions.claimBatch(any())).thenReturn(List.of(claim));
        when(transactions.retry(eq(claim), eq(3), any())).thenReturn(Optional.of(true));
        assertThat(dispatcher.dispatchBatch().failed()).isEqualTo(1); verifyNoInteractions(adapter);
    }

    private NotificationDeliveryClaim claim(NotificationChannel channel, int attempt) {
        return new NotificationDeliveryClaim(UUID.randomUUID(), UUID.randomUUID(), channel, attempt,
                NotificationEvent.builder().id(UUID.randomUUID()).recipient(UUID.randomUUID()).build());
    }
}

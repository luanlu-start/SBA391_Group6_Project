package com.fptpost.modules.notification;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.model.*;
import com.fptpost.modules.notification.repository.*;
import com.fptpost.modules.notification.service.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationPublisherTest {
    private final NotificationEventRepository events = mock(NotificationEventRepository.class);
    private final NotificationDeliveryRepository deliveries = mock(NotificationDeliveryRepository.class);
    private final NotificationRecipientLookup recipients = mock(NotificationRecipientLookup.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final Instant now = Instant.parse("2026-10-29T00:00:00Z");
    private final UUID owner = UUID.randomUUID();
    private final NotificationPublisher publisher = new NotificationPublisher(events, deliveries, recipients, settings, Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach void setup() {
        when(recipients.lock(owner)).thenReturn(new NotificationRecipientLookup.Recipient(owner, "recipient@example.invalid", "0900000000", true, false, UserStatus.ACTIVE, false));
        when(events.findByRecipientAndEventKey(any(), anyString())).thenReturn(Optional.empty());
        when(events.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(settings.channels(owner, NotificationCategory.KYC)).thenReturn(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
    }

    @Test void createsOneDurableEventAndIndependentChannelsWithoutSending() {
        publisher.publish(owner, NotificationCategory.KYC, "kyc:result:1", "Kết quả hồ sơ", "Hồ sơ đã được duyệt.", "/me/kyc");
        var event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(events).saveAndFlush(event.capture());
        assertThat(event.getValue().getRecipient()).isEqualTo(owner);
        assertThat(event.getValue().getAt()).isEqualTo(now);
        var jobs = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries, times(2)).save(jobs.capture());
        assertThat(jobs.getAllValues()).extracting(NotificationDelivery::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
        assertThat(jobs.getAllValues()).allSatisfy(job -> {
            assertThat(job.getEvent()).isSameAs(event.getValue());
            assertThat(job.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
            assertThat(job.getAttempts()).isZero();
        });
        var ordered = inOrder(recipients, events);
        ordered.verify(recipients).lock(owner);
        ordered.verify(events).findByRecipientAndEventKey(owner, "kyc:result:1");
    }

    @Test void duplicateBusinessKeyDoesNotCreateAnotherEventOrDelivery() {
        when(events.findByRecipientAndEventKey(owner, "kyc:1")).thenReturn(Optional.of(NotificationEvent.builder().id(UUID.randomUUID()).build()));
        publisher.publish(owner, NotificationCategory.KYC, "kyc:1", "Kết quả", "Hồ sơ đã duyệt", "/me/kyc");
        verify(events, never()).saveAndFlush(any()); verifyNoInteractions(deliveries, settings);
    }

    @Test void rejectsDeletedRecipientBeforeWriting() {
        when(recipients.lock(owner)).thenReturn(new NotificationRecipientLookup.Recipient(owner, "deleted@example.invalid", "0900000000", true, false, UserStatus.DELETED, true));
        assertThatThrownBy(() -> publisher.publish(owner, NotificationCategory.KYC, "kyc:1", "Kết quả", "Hồ sơ đã duyệt", null)).isInstanceOf(AppException.class);
        verifyNoInteractions(events, deliveries, settings);
    }

    @Test void disallowsExternalLinksAndEmailHeaderInjection() {
        for (String link : List.of("https://example.invalid", "//example.invalid", "/%2fexample.invalid", "/../secret", "/path\\bad")) {
            assertThatThrownBy(() -> publisher.publish(owner, NotificationCategory.KYC, "kyc:1", "Kết quả", "Nội dung", link)).isInstanceOf(AppException.class);
        }
        assertThatThrownBy(() -> publisher.publish(owner, NotificationCategory.KYC, "kyc:1", "Title\r\nBcc: bad", "Nội dung", null)).isInstanceOf(AppException.class);
        verifyNoInteractions(recipients, events, deliveries);
    }
}

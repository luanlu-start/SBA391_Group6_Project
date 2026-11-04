package com.fptpost.modules.notification;

import com.fptpost.common.mail.dto.EmailRequest;
import com.fptpost.common.mail.enums.EmailTemplateType;
import com.fptpost.common.mail.service.EmailService;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import com.fptpost.modules.notification.dto.response.NotificationResponse;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.model.NotificationEvent;
import com.fptpost.modules.notification.service.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationDeliveryAdapterTest {
    private final UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    private final NotificationInboxStore inbox = mock(NotificationInboxStore.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final EmailService mail = mock(EmailService.class);
    private final NotificationRecipientLookup recipients = mock(NotificationRecipientLookup.class);
    private final NotificationSettingsService settings = mock(NotificationSettingsService.class);
    private final MockSmsDelivery sms = mock(MockSmsDelivery.class);
    private final NotificationDeliveryAdapter adapter = new NotificationDeliveryAdapter(inbox, messaging, mail, recipients, settings, sms);
    private NotificationRecipientLookup.Recipient contact;

    @BeforeEach void setup() {
        contact = new NotificationRecipientLookup.Recipient(owner, "recipient@example.invalid", "0900000000", true, true, UserStatus.ACTIVE, false);
        when(recipients.find(owner)).thenReturn(Optional.of(contact));
        when(settings.channels(owner, NotificationCategory.KYC)).thenReturn(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
    }

    @Test void inboxBroadcastUsesOnlyRecipientUserQueue() {
        var claim = claim(NotificationChannel.IN_APP);
        var response = new NotificationResponse(id, NotificationCategory.KYC, "Kết quả", "Hồ sơ đã duyệt", "/me/kyc", Instant.now(), null);
        when(inbox.append(claim.event())).thenReturn(response);
        assertThat(adapter.deliver(claim)).isTrue();
        verify(messaging).convertAndSendToUser(owner.toString(), "/queue/notifications", response);
        verifyNoInteractions(mail, sms);
    }

    @Test void deletedAccountCannotReceivePreviouslyQueuedNotifications() {
        when(recipients.find(owner)).thenReturn(Optional.of(new NotificationRecipientLookup.Recipient(owner, contact.email(), contact.phone(), true, true, UserStatus.DELETED, true)));
        assertThat(adapter.deliver(claim(NotificationChannel.EMAIL))).isFalse();
        verifyNoInteractions(inbox, messaging, mail, sms);
    }

    @Test void emailUsesCurrentVerifiedContactAndEscapableTemplateVariables() {
        assertThat(adapter.deliver(claim(NotificationChannel.EMAIL))).isTrue();
        var request = ArgumentCaptor.forClass(EmailRequest.class); verify(mail).sendEmail(request.capture());
        assertThat(request.getValue().getEmail()).isEqualTo(contact.email());
        assertThat(request.getValue().getTemplateType()).isEqualTo(EmailTemplateType.NOTIFICATION);
        assertThat(request.getValue().getVariables()).containsOnlyKeys("title", "body");
        verifyNoInteractions(inbox, messaging, sms);
    }

    @Test void smsWithoutDevelopmentAdapterAndVerifiedContactCannotPretendSent() {
        when(settings.channels(owner, NotificationCategory.KYC)).thenReturn(EnumSet.allOf(NotificationChannel.class));
        when(settings.smsAvailable(contact)).thenReturn(false);
        assertThatThrownBy(() -> adapter.deliver(claim(NotificationChannel.SMS))).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(sms, mail);
    }

    @Test void smsMockReceivesTheStableDeliveryIdentifierForDeduplication() {
        var claim = claim(NotificationChannel.SMS);
        when(settings.channels(owner, NotificationCategory.KYC)).thenReturn(EnumSet.allOf(NotificationChannel.class));
        when(settings.smsAvailable(contact)).thenReturn(true);
        assertThat(adapter.deliver(claim)).isTrue(); verify(sms).send(claim, contact.phone());
    }

    @Test void optingOutOfSmsSkipsQueuedSmsEvenForMandatoryCategories() {
        for (var category : NotificationCategory.values()) {
            if (!category.isMandatory()) continue;
            when(settings.channels(owner, category)).thenReturn(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
            assertThat(adapter.deliver(claim(NotificationChannel.SMS, category))).isFalse();
        }
        verifyNoInteractions(sms, inbox, mail, messaging);
        verify(settings, never()).smsAvailable(any());
    }

    @Test void newlyDisabledOptionalChannelSkipsPreviouslyQueuedDelivery() {
        when(settings.channels(owner, NotificationCategory.PROMOTION)).thenReturn(Set.of());
        assertThat(adapter.deliver(claim(NotificationChannel.EMAIL, NotificationCategory.PROMOTION))).isFalse();
        assertThat(adapter.deliver(claim(NotificationChannel.IN_APP, NotificationCategory.PROMOTION))).isFalse();
        verifyNoInteractions(inbox, messaging, mail, sms);
    }

    @Test void optionalDeliveryStillUsesChannelsEnabledAtDispatch() {
        when(settings.channels(owner, NotificationCategory.RECOMMENDATION)).thenReturn(Set.of(NotificationChannel.EMAIL));
        assertThat(adapter.deliver(claim(NotificationChannel.EMAIL, NotificationCategory.RECOMMENDATION))).isTrue();
        verify(mail).sendEmail(any(EmailRequest.class)); verifyNoInteractions(inbox, messaging, sms);
    }

    private NotificationDeliveryClaim claim(NotificationChannel channel) {
        return claim(channel, NotificationCategory.KYC);
    }

    private NotificationDeliveryClaim claim(NotificationChannel channel, NotificationCategory category) {
        var event = NotificationEvent.builder().id(id).recipient(owner).category(category).title("Kết quả")
                .body("Hồ sơ đã duyệt").relativeLink("/me/kyc").at(Instant.now()).build();
        return new NotificationDeliveryClaim(UUID.randomUUID(), UUID.randomUUID(), channel, 1, event);
    }
}

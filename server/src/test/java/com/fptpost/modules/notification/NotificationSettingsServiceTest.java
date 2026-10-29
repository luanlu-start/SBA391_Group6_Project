package com.fptpost.modules.notification;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import com.fptpost.modules.notification.dto.request.NotificationSettingsRequest;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.model.NotificationPreference;
import com.fptpost.modules.notification.repository.NotificationPreferenceRepository;
import com.fptpost.modules.notification.service.NotificationSettingsService;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationSettingsServiceTest {
    private final UUID owner = UUID.randomUUID();
    private final NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
    private final NotificationRecipientLookup recipients = mock(NotificationRecipientLookup.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final NotificationSettingsService service = new NotificationSettingsService(preferences, recipients, environment);

    @BeforeEach void setup() {
        environment.setActiveProfiles("test");
        var contact = new NotificationRecipientLookup.Recipient(owner, "recipient@example.invalid", "0900000000", true, true, UserStatus.ACTIVE, false);
        when(recipients.find(owner)).thenReturn(Optional.of(contact)); when(recipients.lock(owner)).thenReturn(contact);
        when(preferences.findByRecipient(owner)).thenReturn(List.of());
    }

    @Test void exposesAllSevenGroupsAndOnlyExplicitAvailableCapabilities() {
        var result = service.get(owner);
        assertThat(result.settings()).hasSize(7); assertThat(result.smsAvailable()).isTrue(); assertThat(result.webPushAvailable()).isFalse();
        assertThat(result.settings()).filteredOn(value -> value.mandatory()).hasSize(5).allSatisfy(value -> {
            assertThat(value.channels().get(NotificationChannel.IN_APP)).isTrue();
            assertThat(value.channels().get(NotificationChannel.EMAIL)).isTrue();
            assertThat(value.channels().get(NotificationChannel.SMS)).isFalse();
        });
    }

    @Test void mandatoryGroupCannotDisableEitherRequiredChannel() {
        for (var category : List.of(NotificationCategory.KYC, NotificationCategory.SECURITY, NotificationCategory.PAYMENT, NotificationCategory.DISPUTE, NotificationCategory.ORDER)) {
            assertThatThrownBy(() -> service.update(owner, request(category, false, true, false))).isInstanceOf(AppException.class);
            assertThatThrownBy(() -> service.update(owner, request(category, true, false, false))).isInstanceOf(AppException.class);
        }
        verify(preferences, never()).saveAllAndFlush(any());
    }

    @Test void promotionAndRecommendationCanDisableAllChannels() {
        service.update(owner, request(NotificationCategory.PROMOTION, false, false, false));
        verify(preferences).saveAllAndFlush(argThat(rows -> {
            var row = rows.iterator().next();
            return row.getRecipient().equals(owner) && row.getCategory() == NotificationCategory.PROMOTION
                    && !row.isInApp() && !row.isEmail() && !row.isSms();
        }));
    }

    @Test void duplicateOrIncompleteSettingsAreRejectedWithoutWrites() {
        var row = new NotificationSettingsRequest.Setting(NotificationCategory.ORDER, Map.of(NotificationChannel.IN_APP, true));
        assertThatThrownBy(() -> service.update(owner, new NotificationSettingsRequest(List.of(row)))).isInstanceOf(AppException.class);
        var duplicate = request(NotificationCategory.PROMOTION, true, true, false).settings().getFirst();
        assertThatThrownBy(() -> service.update(owner, new NotificationSettingsRequest(List.of(duplicate, duplicate)))).isInstanceOf(AppException.class);
        verify(preferences, never()).saveAllAndFlush(any());
    }

    @Test void smsRequiresVerifiedContactAndDevelopmentProfile() {
        environment.setActiveProfiles("prod");
        assertThat(service.get(owner).smsAvailable()).isFalse();
        assertThatThrownBy(() -> service.update(owner, request(NotificationCategory.PROMOTION, true, true, true)))
                .isInstanceOf(AppException.class).extracting(value -> ((AppException) value).getErrorCode().getCode()).isEqualTo("NOTIFICATION_CHANNEL_UNAVAILABLE");
        environment.setActiveProfiles("test");
        when(recipients.lock(owner)).thenReturn(new NotificationRecipientLookup.Recipient(owner, "recipient@example.invalid", "0900000000", true, false, UserStatus.ACTIVE, false));
        assertThatThrownBy(() -> service.update(owner, request(NotificationCategory.PROMOTION, true, true, true))).isInstanceOf(AppException.class);
    }

    @Test void corruptedLegacyPreferenceCannotSuppressMandatoryNotification() {
        when(preferences.findByRecipient(owner)).thenReturn(List.of(NotificationPreference.builder().category(NotificationCategory.PAYMENT).inApp(false).email(false).sms(false).build()));
        assertThat(service.channels(owner, NotificationCategory.PAYMENT)).containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
    }

    @Test void previouslyEnabledSmsCanBeDisabledAfterContactLosesVerification() {
        var stored = NotificationPreference.builder().id(UUID.randomUUID()).recipient(owner)
                .category(NotificationCategory.PROMOTION).inApp(true).email(true).sms(true).build();
        var contact = new NotificationRecipientLookup.Recipient(owner, "recipient@example.invalid", "0900000000",
                true, false, UserStatus.ACTIVE, false);
        when(recipients.find(owner)).thenReturn(Optional.of(contact));
        when(recipients.lock(owner)).thenReturn(contact);
        when(preferences.findByRecipient(owner)).thenReturn(List.of(stored));

        var settings = service.get(owner);
        assertThat(settings.smsAvailable()).isFalse();
        assertThat(settings.settings()).filteredOn(value -> value.category() == NotificationCategory.PROMOTION)
                .singleElement().satisfies(value -> assertThat(value.channels().get(NotificationChannel.SMS)).isTrue());

        var saved = service.update(owner, request(NotificationCategory.PROMOTION, true, false, false));
        verify(preferences).saveAllAndFlush(argThat(rows -> rows.iterator().next() == stored));
        assertThat(stored.isSms()).isFalse();
        assertThat(saved.settings()).filteredOn(value -> value.category() == NotificationCategory.PROMOTION)
                .singleElement().satisfies(value -> {
                    assertThat(value.channels().get(NotificationChannel.SMS)).isFalse();
                    assertThat(value.channels().get(NotificationChannel.EMAIL)).isFalse();
                });
        assertThatThrownBy(() -> service.update(owner, request(NotificationCategory.PROMOTION, true, false, true)))
                .isInstanceOf(AppException.class)
                .extracting(value -> ((AppException) value).getErrorCode().getCode())
                .isEqualTo("NOTIFICATION_CHANNEL_UNAVAILABLE");
        verify(preferences, times(1)).saveAllAndFlush(any());
    }

    @Test void productionAndStagingOverrideAnyDevelopmentSmsProfile() {
        for (var profiles : List.of(new String[] {"prod", "dev"}, new String[] {"production", "local"},
                new String[] {"staging", "test"})) {
            environment.setActiveProfiles(profiles);
            assertThat(service.get(owner).smsAvailable()).isFalse();
            assertThatThrownBy(() -> service.update(owner, request(NotificationCategory.PROMOTION, true, true, true)))
                    .isInstanceOf(AppException.class)
                    .extracting(value -> ((AppException) value).getErrorCode().getCode())
                    .isEqualTo("NOTIFICATION_CHANNEL_UNAVAILABLE");
        }
        verify(preferences, never()).saveAllAndFlush(any());
    }

    private NotificationSettingsRequest request(NotificationCategory category, boolean inApp, boolean email, boolean sms) {
        return new NotificationSettingsRequest(List.of(new NotificationSettingsRequest.Setting(category, Map.of(NotificationChannel.IN_APP, inApp, NotificationChannel.EMAIL, email, NotificationChannel.SMS, sms))));
    }
}

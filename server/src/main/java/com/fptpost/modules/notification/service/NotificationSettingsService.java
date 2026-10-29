package com.fptpost.modules.notification.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import com.fptpost.modules.notification.dto.request.NotificationSettingsRequest;
import com.fptpost.modules.notification.dto.response.*;
import com.fptpost.modules.notification.enums.*;
import com.fptpost.modules.notification.exception.NotificationErrorCode;
import com.fptpost.modules.notification.model.NotificationPreference;
import com.fptpost.modules.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class NotificationSettingsService {
    private final NotificationPreferenceRepository preferences;
    private final NotificationRecipientLookup recipients;
    private final Environment environment;

    @Transactional(readOnly = true) @PreAuthorize("isAuthenticated()")
    public NotificationSettingsResponse get(UUID owner) {
        var recipient = recipients.find(owner).filter(NotificationRecipientLookup.Recipient::canReceive)
                .orElseThrow(() -> new AppException(NotificationErrorCode.NOT_FOUND));
        return response(owner, smsAvailable(recipient));
    }

    @Transactional @PreAuthorize("isAuthenticated()")
    public NotificationSettingsResponse update(UUID owner, NotificationSettingsRequest request) {
        var recipient = recipients.lock(owner);
        if (!recipient.canReceive()) throw new AppException(NotificationErrorCode.NOT_FOUND);
        boolean smsAvailable = smsAvailable(recipient);
        if (request == null || request.settings() == null || request.settings().isEmpty() || request.settings().size() > 7)
            throw invalid();
        Set<NotificationCategory> supplied = EnumSet.noneOf(NotificationCategory.class);
        var stored = new EnumMap<NotificationCategory, NotificationPreference>(NotificationCategory.class);
        preferences.findByRecipient(owner).forEach(row -> stored.put(row.getCategory(), row));
        List<NotificationPreference> changed = new ArrayList<>();
        for (var setting : request.settings()) {
            if (setting == null || setting.category() == null || !supplied.add(setting.category()) || setting.channels() == null
                    || !setting.channels().keySet().equals(EnumSet.allOf(NotificationChannel.class))
                    || setting.channels().values().stream().anyMatch(Objects::isNull)) throw invalid();
            boolean inApp = setting.channels().get(NotificationChannel.IN_APP);
            boolean email = setting.channels().get(NotificationChannel.EMAIL);
            boolean sms = setting.channels().get(NotificationChannel.SMS);
            if (setting.category().isMandatory() && (!inApp || !email)) throw invalid();
            if (sms && !smsAvailable) throw new AppException(NotificationErrorCode.CHANNEL_UNAVAILABLE);
            var row = stored.getOrDefault(setting.category(), NotificationPreference.builder().id(UUID.randomUUID())
                    .recipient(owner).category(setting.category()).build());
            row.setInApp(inApp); row.setEmail(email); row.setSms(sms); changed.add(row);
        }
        preferences.saveAllAndFlush(changed);
        return response(owner, smsAvailable);
    }

    /** Internal event-time preferences; mandatory channels remain enabled even if old rows are inconsistent. */
    @Transactional(readOnly = true)
    public Set<NotificationChannel> channels(UUID owner, NotificationCategory category) {
        var row = preferences.findByRecipient(owner).stream().filter(value -> value.getCategory() == category).findFirst();
        Set<NotificationChannel> result = EnumSet.noneOf(NotificationChannel.class);
        if (category.isMandatory() || row.map(NotificationPreference::isInApp).orElse(true)) result.add(NotificationChannel.IN_APP);
        if (category.isMandatory() || row.map(NotificationPreference::isEmail).orElse(true)) result.add(NotificationChannel.EMAIL);
        if (row.map(NotificationPreference::isSms).orElse(false)) result.add(NotificationChannel.SMS);
        return result;
    }

    public boolean smsMockEnabled() {
        return !environment.acceptsProfiles(Profiles.of("prod", "production", "staging"))
                && environment.acceptsProfiles(Profiles.of("local", "dev", "demo", "test"));
    }

    public boolean smsAvailable(NotificationRecipientLookup.Recipient recipient) {
        return smsMockEnabled() && recipient.canReceive() && recipient.phoneVerified()
                && recipient.phone() != null && recipient.phone().matches("\\+?[0-9]{8,16}");
    }

    private NotificationSettingsResponse response(UUID owner, boolean smsAvailable) {
        var stored = new EnumMap<NotificationCategory, NotificationPreference>(NotificationCategory.class);
        preferences.findByRecipient(owner).forEach(row -> stored.put(row.getCategory(), row));
        var settings = Arrays.stream(NotificationCategory.values()).map(category -> {
            var row = stored.get(category);
            return new NotificationSettingResponse(category, category.isMandatory(), Map.of(
                    NotificationChannel.IN_APP, category.isMandatory() || row == null || row.isInApp(),
                    NotificationChannel.EMAIL, category.isMandatory() || row == null || row.isEmail(),
                    NotificationChannel.SMS, row != null && row.isSms()));
        }).toList();
        return new NotificationSettingsResponse(settings, smsAvailable, false);
    }

    private AppException invalid() { return new AppException(NotificationErrorCode.INVALID_SETTINGS); }
}

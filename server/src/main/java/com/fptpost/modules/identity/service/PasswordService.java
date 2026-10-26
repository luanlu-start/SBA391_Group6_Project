package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.mail.dto.EmailRequest;
import com.fptpost.common.mail.enums.EmailTemplateType;
import com.fptpost.common.mail.exception.EmailDeliveryException;
import com.fptpost.common.mail.service.EmailService;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.dto.request.ResetPasswordRequest;
import com.fptpost.modules.identity.dto.request.ChangePasswordRequest;
import com.fptpost.modules.identity.dto.response.PasswordResetRequestResponse;
import com.fptpost.modules.identity.enums.OtpType;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.event.PasswordChangedEvent;
import com.fptpost.modules.identity.exception.*;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.security.AuthenticationRateLimiter;
import com.fptpost.modules.identity.util.ContactUtils;
import com.fptpost.modules.identity.util.PasswordPolicy;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {
    private final OtpService otpService;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final AuthSessionRepository sessions;
    private final OtpCodeRepository otps;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final RegistrationProperties properties;
    private final AuthenticationRateLimiter limiter;
    private final Clock identityClock;
    private final ApplicationEventPublisher events;
    private final EmailService emailService;

    // The OTP transaction completes (or rolls back) before the public acknowledgement.
    public PasswordResetRequestResponse forgotPassword(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        limiter.checkPasswordReset(normalized);
        var availableAt = identityClock.instant().plusSeconds(properties.getOtpResendCooldownSeconds());
        try {
            otpService.requestPasswordReset(normalized);
        } catch (AppException exception) {
            if (exception.getErrorCode() != IdentityErrorCode.OTP_DELIVERY_FAILED) throw exception;
            // Delivery failures must not disclose whether a submitted account exists.
            log.warn("Password recovery OTP delivery failed");
        }
        return PasswordResetRequestResponse.builder().resendAvailableAt(availableAt).build();
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public void resetPassword(ResetPasswordRequest request) {
        var user = otpService.consumePasswordResetOtp(normalizeIdentifier(request.getIdentifier()), request.getOtp());
        passwordPolicy.validate(request.getNewPassword(), user.getEmail(), user.getPhone());
        updatePassword(user, request.getNewPassword());
    }

    @Transactional
    public void changePassword(UUID userPublicId, UUID sessionId, ChangePasswordRequest request) {
        var user = users.findByPublicIdForUpdate(userPublicId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        // Recheck after acquiring the same user lock used by login, refresh and password reset.
        var session = sessions.findByPublicIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        if (user.isDeleted() || user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null
                || session.getRevokedAt() != null || session.getReplacedBy() != null
                || !identityClock.instant().isBefore(session.getExpiresAt())) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        if (request.getCurrentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(IdentityErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        passwordPolicy.validate(request.getNewPassword(), user.getEmail(), user.getPhone());
        if (encoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(IdentityErrorCode.PASSWORD_UNCHANGED);
        }
        updatePassword(user, request.getNewPassword());
    }

    private void updatePassword(User user, String newPassword) {
        var now = identityClock.instant();
        user.setPasswordHash(encoder.encode(newPassword));
        user.setFailedLoginCount(0);
        user.setLoginLockedUntil(null);
        user.setUpdatedAt(now);
        users.saveAndFlush(user);
        sessions.revokeAll(user.getId(), now);
        otps.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), OtpType.PASSWORD_RESET)
                .filter(otp -> otp.getUsedAt() == null && now.isBefore(otp.getExpiresAt()))
                .ifPresent(otp -> otp.setExpiresAt(now));
        String fullName = profiles.findById(user.getId()).map(profile -> profile.getFullName()).orElse("bạn");
        events.publishEvent(new PasswordChangedEvent(user.getEmail(), fullName, now));
    }

    @TransactionalEventListener
    public void notifyPasswordChanged(PasswordChangedEvent event) {
        String changedAt = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.ROOT)
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(event.changedAt());
        try {
            emailService.sendEmail(EmailRequest.builder().email(event.email())
                    .templateType(EmailTemplateType.PASSWORD_CHANGED)
                    .variables(Map.of("fullName", event.fullName(), "changedAt", changedAt)).build());
        } catch (EmailDeliveryException exception) {
            // The password change has committed; notification failure must not undo it.
            log.warn("Password change security notification delivery failed");
        }
    }

    private String normalizeIdentifier(String identifier) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("0[35789]\\d{8}") ? ContactUtils.normalizePhone(normalized) : normalized;
    }
}

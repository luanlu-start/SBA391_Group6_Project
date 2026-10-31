package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.mail.dto.EmailRequest;
import com.fptpost.common.mail.enums.EmailTemplateType;
import com.fptpost.common.mail.exception.EmailDeliveryException;
import com.fptpost.common.mail.service.EmailService;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.config.ProfileProperties;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.exception.IdentityException;
import com.fptpost.modules.identity.dto.request.OtpVerificationRequest;
import com.fptpost.modules.identity.dto.response.OtpVerificationResponse;
import com.fptpost.modules.identity.dto.response.OtpChallengeResponse;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.enums.OtpType;
import com.fptpost.modules.identity.mapper.UserMapper;
import com.fptpost.modules.identity.model.OtpCode;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.OtpCodeRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OtpService {
    OtpCodeRepository otpCodeRepository;
    UserRepository userRepository;
    UserProfileRepository userProfileRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    RegistrationProperties registrationProperties;
    ProfileProperties profileProperties;
    EmailService emailService;
    Clock identityClock;
    SecureRandom identityRandom;
    @NonFinal String dummyOtpHash;

    @PostConstruct
    void initializeDummyOtp() { dummyOtpHash = passwordEncoder.encode(UUID.randomUUID().toString()); }

    @Transactional
    public OtpCode generateAndSendOtp(User user, String fullName) {
        return generateAndSendOtp(user, fullName, OtpType.REGISTRATION);
    }

    private OtpCode generateAndSendOtp(User user, String fullName, OtpType type) {
        return generateAndSendOtp(user, fullName, type, user.getEmail());
    }

    private OtpCode generateAndSendOtp(User user, String fullName, OtpType type, String target) {
        Instant now = identityClock.instant();
        String code = String.format(Locale.ROOT, "%06d", identityRandom.nextInt(1000000));

        OtpCode otp = OtpCode.builder()
                .publicId(UUID.randomUUID())
                .user(user)
                .target(target)
                .type(type)
                .codeHash(passwordEncoder.encode(code))
                .createdAt(now)
                .expiresAt(now.plus(registrationProperties.getOtpTtlMinutes(), ChronoUnit.MINUTES))
                .build();
        otpCodeRepository.saveAndFlush(otp);

        // Shares the user creation transaction; delivery failure rolls back all registration data.
        try {
            emailService.sendEmail(EmailRequest.builder()
                    .email(target)
                    .templateType(switch (type) {
                        case PASSWORD_RESET -> EmailTemplateType.RESET_PASSWORD;
                        case REGISTRATION -> EmailTemplateType.VERIFY_EMAIL;
                        default -> EmailTemplateType.CONTACT_CHANGE;
                    })
                    .variables(Map.of("fullName", fullName, "otp", code,
                            "expirationMinutes", registrationProperties.getOtpTtlMinutes()))
                    .build());
        } catch (EmailDeliveryException exception) {
            throw new AppException(IdentityErrorCode.OTP_DELIVERY_FAILED);
        }
        return otp;
    }

    @Transactional
    public OtpCode issueContactOtp(User user, OtpType type, String target) {
        if (type != OtpType.EMAIL_CHANGE && type != OtpType.PHONE_CHANGE) throw new IllegalArgumentException("Invalid contact OTP purpose");
        String fullName = userProfileRepository.findById(user.getId()).orElseThrow().getFullName();
        return generateAndSendOtp(user, fullName, type, target);
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public void consumeContactOtp(OtpCode otp, String code) {
        if (otp.getUsedAt() != null) throw new AppException(IdentityErrorCode.OTP_ALREADY_USED);
        if (otp.getTries() >= registrationProperties.getOtpMaxAttempts()) throw new AppException(IdentityErrorCode.OTP_ATTEMPTS_EXCEEDED);
        if (!identityClock.instant().isBefore(otp.getExpiresAt())) throw new AppException(IdentityErrorCode.OTP_EXPIRED);
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setTries(otp.getTries() + 1); otpCodeRepository.saveAndFlush(otp);
            throw new IdentityException(otp.getTries() >= registrationProperties.getOtpMaxAttempts()
                    ? IdentityErrorCode.OTP_ATTEMPTS_EXCEEDED : IdentityErrorCode.OTP_INVALID);
        }
        otp.setUsedAt(identityClock.instant()); otpCodeRepository.saveAndFlush(otp);
    }

    @Transactional
    public void requestPasswordReset(String identifier) {
        Long id = userRepository.findIdByIdentifier(identifier).orElse(null);
        User user = id == null ? null : userRepository.findByIdForUpdate(id).orElse(null);
        if (!canResetPassword(user)) {
            passwordEncoder.matches("000000", dummyOtpHash);
            return;
        }
        Instant now = identityClock.instant();
        var latest = otpCodeRepository.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), OtpType.PASSWORD_RESET);
        if (latest.isPresent()) {
            OtpCode previous = latest.get();
            // Also enforce the delivery cooldown under the shared SQL lock across instances.
            if (now.isBefore(previous.getCreatedAt().plusSeconds(registrationProperties.getOtpResendCooldownSeconds()))) return;
            previous.setExpiresAt(now);
            otpCodeRepository.saveAndFlush(previous);
        }
        String fullName = userProfileRepository.findById(user.getId()).map(profile -> profile.getFullName()).orElse("bạn");
        generateAndSendOtp(user, fullName, OtpType.PASSWORD_RESET);
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public User consumePasswordResetOtp(String identifier, String code) {
        Long id = userRepository.findIdByIdentifier(identifier).orElse(null);
        User user = id == null ? null : userRepository.findByIdForUpdate(id).orElse(null);
        OtpCode otp = user == null ? null : otpCodeRepository
                .findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), OtpType.PASSWORD_RESET).orElse(null);
        Instant now = identityClock.instant();
        boolean usable = canResetPassword(user) && otp != null && otp.getUsedAt() == null
                && otp.getTarget().equals(user.getEmail()) && now.isBefore(otp.getExpiresAt())
                && otp.getTries() < registrationProperties.getOtpMaxAttempts();
        boolean correct = passwordEncoder.matches(code, usable ? otp.getCodeHash() : dummyOtpHash);
        if (!usable || !correct) {
            if (usable) {
                otp.setTries(otp.getTries() + 1);
                otpCodeRepository.saveAndFlush(otp);
                throw new IdentityException(IdentityErrorCode.PASSWORD_RESET_INVALID);
            }
            throw new AppException(IdentityErrorCode.PASSWORD_RESET_INVALID);
        }
        otp.setUsedAt(now);
        otpCodeRepository.saveAndFlush(otp);
        return user;
    }

    private boolean canResetPassword(User user) {
        return user != null && !user.isDeleted() && (user.getStatus() == UserStatus.ACTIVE
                || user.getStatus() == UserStatus.PENDING_VERIFICATION);
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public OtpVerificationResponse verifyOtp(OtpVerificationRequest request) {
        User user = lockChallengeOwner(request.getChallengeId());
        OtpCode otp = otpCodeRepository.findByPublicIdAndType(request.getChallengeId(), OtpType.REGISTRATION)
                .orElseThrow(() -> new AppException(IdentityErrorCode.OTP_NOT_FOUND));
        if (otp.getUsedAt() != null) {
            throw new AppException(IdentityErrorCode.OTP_ALREADY_USED);
        }
        requirePendingUser(user);
        OtpCode latest = latestChallenge(user);
        if (!latest.getPublicId().equals(otp.getPublicId()) || !otp.getTarget().equals(user.getEmail())) {
            throw new AppException(IdentityErrorCode.OTP_SUPERSEDED);
        }
        Instant now = identityClock.instant();
        if (otp.getTries() >= registrationProperties.getOtpMaxAttempts()) {
            throw new AppException(IdentityErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }
        if (!now.isBefore(otp.getExpiresAt())) {
            throw new AppException(IdentityErrorCode.OTP_EXPIRED);
        }
        if (!passwordEncoder.matches(request.getOtp(), otp.getCodeHash())) {
            otp.setTries(otp.getTries() + 1);
            otpCodeRepository.saveAndFlush(otp);
            // Commit the attempt before the HTTP error; unrelated exceptions still roll back.
            throw new IdentityException(otp.getTries() >= registrationProperties.getOtpMaxAttempts()
                    ? IdentityErrorCode.OTP_ATTEMPTS_EXCEEDED : IdentityErrorCode.OTP_INVALID);
        }

        otp.setUsedAt(now);
        user.setEmailVerifiedAt(now);
        user.setFptBadge(profileProperties.isFptEmail(user.getEmail()));
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(now);
        otpCodeRepository.saveAndFlush(otp);
        userRepository.saveAndFlush(user);
        return userMapper.toOtpVerificationResponse(user);
    }

    @Transactional
    public OtpChallengeResponse resendOtp(UUID challengeId) {
        User user = lockChallengeOwner(challengeId);
        requirePendingUser(user);
        // An older challenge cannot bypass the cooldown for the latest email.
        OtpCode latest = latestChallenge(user);
        Instant now = identityClock.instant();
        Instant availableAt = latest.getCreatedAt().plusSeconds(registrationProperties.getOtpResendCooldownSeconds());
        if (now.isBefore(availableAt)) {
            long milliseconds = Duration.between(now, availableAt).toMillis();
            throw new IdentityException(IdentityErrorCode.OTP_RESEND_TOO_SOON,
                    Math.max(1, (milliseconds + 999) / 1000));
        }
        String fullName = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Missing registration profile")).getFullName();
        latest.setExpiresAt(now);
        otpCodeRepository.saveAndFlush(latest);
        // Both the invalidation and the new challenge roll back if SMTP fails.
        OtpCode replacement = generateAndSendOtp(user, fullName);
        return userMapper.toOtpChallengeResponse(replacement, registrationProperties);
    }

    @Transactional(readOnly = true)
    public OtpChallengeResponse registrationChallenge(User user) {
        requirePendingUser(user);
        return userMapper.toOtpChallengeResponse(latestChallenge(user), registrationProperties);
    }

    private User lockChallengeOwner(UUID challengeId) {
        Long userId = otpCodeRepository.findUserIdByChallenge(challengeId, OtpType.REGISTRATION)
                .orElseThrow(() -> new AppException(IdentityErrorCode.OTP_NOT_FOUND));
        // Serialize verification and resend across application instances, including failed attempts.
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.OTP_NOT_FOUND));
    }

    private OtpCode latestChallenge(User user) {
        return otpCodeRepository.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), OtpType.REGISTRATION)
                .orElseThrow(() -> new AppException(IdentityErrorCode.OTP_NOT_FOUND));
    }

    private void requirePendingUser(User user) {
        if (user.isDeleted() || user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new AppException(IdentityErrorCode.VERIFICATION_NOT_ALLOWED);
        }
    }
}

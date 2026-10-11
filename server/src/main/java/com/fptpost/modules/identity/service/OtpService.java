package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.mail.dto.EmailRequest;
import com.fptpost.common.mail.enums.EmailTemplateType;
import com.fptpost.common.mail.exception.EmailDeliveryException;
import com.fptpost.common.mail.service.EmailService;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.model.OtpCode;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.OtpCodeRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OtpService {
    OtpCodeRepository otpCodeRepository;
    PasswordEncoder passwordEncoder;
    RegistrationProperties registrationProperties;
    EmailService emailService;
    Clock identityClock;
    SecureRandom identityRandom;

    @Transactional
    public OtpCode generateAndSendOtp(User user, String fullName) {
        Instant now = identityClock.instant();
        String code = String.format(Locale.ROOT, "%06d", identityRandom.nextInt(1000000));

        OtpCode otp = OtpCode.builder()
                .publicId(UUID.randomUUID())
                .user(user)
                .target(user.getEmail())
                .purpose("REGISTRATION")
                .codeHash(passwordEncoder.encode(code))
                .createdAt(now)
                .expiresAt(now.plus(registrationProperties.getOtpTtlMinutes(), ChronoUnit.MINUTES))
                .build();
        otpCodeRepository.saveAndFlush(otp);

        // Shares the user creation transaction; delivery failure rolls back all registration data.
        try {
            emailService.sendEmail(EmailRequest.builder()
                    .email(user.getEmail())
                    .templateType(EmailTemplateType.VERIFY_EMAIL)
                    .variables(Map.of("fullName", fullName, "otp", code,
                            "expirationMinutes", registrationProperties.getOtpTtlMinutes()))
                    .build());
        } catch (EmailDeliveryException exception) {
            throw new AppException(IdentityErrorCode.OTP_DELIVERY_FAILED);
        }
        return otp;
    }
}

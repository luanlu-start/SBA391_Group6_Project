package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.dto.RegisterRequest;
import com.fptpost.modules.identity.dto.RegisterResponse;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.mapper.RegistrationMapper;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class RegistrationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserProfileRepository profiles;
    private final ConsentRepository consents;
    private final OtpCodeRepository otps;
    private final BlacklistRepository blacklist;
    private final RegistrationProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationMapper mapper;
    private final RegistrationMailer mailer;
    private final Clock clock;
    private final SecureRandom random;

    @Transactional
    public RegisterResponse register(RegisterRequest request, String remoteAddress) {
        if (!properties.getTermsVersion().equals(request.termsVersion())
                || !properties.getPrivacyVersion().equals(request.privacyVersion())) {
            throw new AppException(IdentityErrorCode.POLICY_CHANGED);
        }
        String phone = RegistrationContact.normalizePhone(request.phone());
        validatePassword(request.password(), request.email(), phone, request.phone());
        if (blacklist.existsByKindAndValueHash("PHONE", RegistrationContact.phoneHash(phone))) {
            throw new AppException(IdentityErrorCode.REGISTRATION_NOT_ALLOWED);
        }
        if (users.existsByEmail(request.email())) throw new AppException(IdentityErrorCode.EMAIL_ALREADY_USED);
        if (users.existsByPhone(phone)) throw new AppException(IdentityErrorCode.PHONE_ALREADY_USED);

        Instant now = clock.instant();
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setEmail(request.email());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.getRoles().add(roles.findByCode("SENDER").orElseThrow(
                () -> new IllegalStateException("Missing required SENDER role migration")));
        // Flush unique constraints before sending anything to an external mail server.
        users.saveAndFlush(user);
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(request.fullName());
        profile.setAdultConfirmedAt(now);
        profiles.save(profile);
        saveConsent(user, "TERMS", request.termsVersion(), now, remoteAddress);
        saveConsent(user, "PRIVACY", request.privacyVersion(), now, remoteAddress);

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
        OtpCode otp = new OtpCode();
        otp.setPublicId(UUID.randomUUID());
        otp.setUser(user);
        otp.setTarget(user.getEmail());
        otp.setPurpose("REGISTRATION");
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setCreatedAt(now);
        otp.setExpiresAt(now.plus(properties.getOtpTtlMinutes(), ChronoUnit.MINUTES));
        otps.saveAndFlush(otp);
        // UC-01 uses bounded synchronous SMTP; a delivery failure rolls back registration.
        mailer.sendOtp(user.getEmail(), code);
        return mapper.toResponse(user, otp);
    }

    private void saveConsent(User user, String type, String version, Instant now, String ip) {
        Consent consent = new Consent();
        consent.setUser(user);
        consent.setPolicyType(type);
        consent.setPolicyVersion(version);
        consent.setAcceptedAt(now);
        consent.setIp(ip);
        consents.save(consent);
    }

    private void validatePassword(String password, String email, String phone, String submittedPhone) {
        if (password.codePointCount(0, password.length()) < properties.getPasswordMinLength()
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().noneMatch(Character::isUpperCase)
                || password.codePoints().noneMatch(Character::isLowerCase)
                || password.codePoints().noneMatch(Character::isDigit)
                || password.equalsIgnoreCase(email) || password.equals(phone) || password.equals(submittedPhone)) {
            throw new AppException(IdentityErrorCode.PASSWORD_INVALID);
        }
    }
}

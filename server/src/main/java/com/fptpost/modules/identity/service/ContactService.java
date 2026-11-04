package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.*;
import com.fptpost.modules.identity.config.*;
import com.fptpost.modules.identity.dto.request.*;
import com.fptpost.modules.identity.dto.response.OtpChallengeResponse;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.exception.*;
import com.fptpost.modules.identity.mapper.UserMapper;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.util.ContactUtils;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ContactService {
    private final IdentityUserAccess userAccess;
    private final UserRepository userRepository;
    private final ContactChangeRepository contactRepository;
    private final OtpCodeRepository otpRepository;
    private final BlacklistRepository blacklistRepository;
    private final AuthSessionRepository sessionRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationProperties registrationProperties;
    private final ProfileProperties profileProperties;
    private final UserMapper userMapper;
    private final Validator validator;
    private final Clock identityClock;

    @Transactional
    public OtpChallengeResponse request(UUID owner, UUID session, ContactChangeRequest request) {
        User user = userAccess.lock(owner, session);
        if (request.getCurrentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) throw new AppException(IdentityErrorCode.CURRENT_PASSWORD_INCORRECT);
        String value = normalize(request.getType(), request.getValue());
        checkAvailable(user, request.getType(), value);
        var previous = contactRepository.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), request.getType());
        previous.ifPresent(change -> { cooldown(change.getOtp()); change.getOtp().setExpiresAt(identityClock.instant()); });
        OtpCode otp = otpService.issueContactOtp(user, purpose(request.getType()), request.getType() == ContactType.EMAIL ? value : user.getEmail());
        contactRepository.saveAndFlush(ContactChange.builder().publicId(otp.getPublicId()).user(user).type(request.getType()).newValue(value)
                .baseEmail(user.getEmail()).basePhone(user.getPhone()).otp(otp).createdAt(otp.getCreatedAt()).build());
        return userMapper.toOtpChallengeResponse(otp, registrationProperties);
    }
    @Transactional
    public OtpChallengeResponse resend(UUID owner, UUID session, UUID challengeId) {
        User user = userAccess.lock(owner, session);
        var change = pending(user, challengeId); cooldown(change.getOtp());
        checkAvailable(user, change.getType(), change.getNewValue());
        change.getOtp().setExpiresAt(identityClock.instant()); otpRepository.flush();
        OtpCode otp = otpService.issueContactOtp(user, purpose(change.getType()), change.getType() == ContactType.EMAIL ? change.getNewValue() : user.getEmail());
        contactRepository.saveAndFlush(ContactChange.builder().publicId(otp.getPublicId()).user(user).type(change.getType()).newValue(change.getNewValue())
                .baseEmail(user.getEmail()).basePhone(user.getPhone()).otp(otp).createdAt(otp.getCreatedAt()).build());
        return userMapper.toOtpChallengeResponse(otp, registrationProperties);
    }
    @Transactional(noRollbackFor = IdentityException.class)
    public void verify(UUID owner, UUID session, ContactVerificationRequest request) {
        User user = userAccess.lock(owner, session);
        var change = pending(user, request.getChallengeId());
        checkAvailable(user, change.getType(), change.getNewValue());
        otpService.consumeContactOtp(change.getOtp(), request.getOtp());
        if (change.getType() == ContactType.EMAIL) {
            user.setEmail(change.getNewValue()); user.setEmailVerifiedAt(identityClock.instant());
            user.setFptBadge(profileProperties.isFptEmail(user.getEmail()));
        } else {
            user.setPhone(change.getNewValue());
            // Email OTP authorizes the account change; it does not prove possession of the new phone.
            user.setPhoneVerifiedAt(null);
        }
        user.setUpdatedAt(identityClock.instant()); change.setCompletedAt(identityClock.instant());
        userRepository.saveAndFlush(user); contactRepository.save(change);
        sessionRepository.revokeAll(user.getId(), identityClock.instant());
    }
    private ContactChange pending(User user, UUID challengeId) {
        var change = contactRepository.findByPublicIdAndUserId(challengeId, user.getId())
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND));
        var latest = contactRepository.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(), change.getType()).orElseThrow();
        if (change.getCompletedAt() != null || !change.getPublicId().equals(latest.getPublicId())
                || !change.getBaseEmail().equals(user.getEmail()) || !change.getBasePhone().equals(user.getPhone()))
            throw new AppException(IdentityErrorCode.CONTACT_SUPERSEDED);
        return change;
    }
    private void checkAvailable(User user, ContactType type, String value) {
        if (value.equals(type == ContactType.EMAIL ? user.getEmail() : user.getPhone())) throw new AppException(IdentityErrorCode.CONTACT_UNCHANGED);
        if (type == ContactType.EMAIL && userRepository.existsByEmail(value)) throw new AppException(IdentityErrorCode.EMAIL_ALREADY_USED);
        if (type == ContactType.PHONE && userRepository.existsByPhone(value)) throw new AppException(IdentityErrorCode.PHONE_ALREADY_USED);
        if (type == ContactType.PHONE && blacklistRepository.existsByKindAndValueHash("PHONE", ContactUtils.phoneHash(value)))
            throw new AppException(IdentityErrorCode.REGISTRATION_NOT_ALLOWED);
    }
    private String normalize(ContactType type, String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (type == ContactType.EMAIL) {
            if (!validator.validateValue(User.class, "email", normalized).isEmpty()) throw new AppException(IdentityErrorCode.CONTACT_INVALID);
            return normalized;
        }
        if (!normalized.matches("(?:0[35789]\\d{8}|\\+84[35789]\\d{8})")) throw new AppException(IdentityErrorCode.CONTACT_INVALID);
        return ContactUtils.normalizePhone(normalized);
    }
    private OtpType purpose(ContactType type) { return type == ContactType.EMAIL ? OtpType.EMAIL_CHANGE : OtpType.PHONE_CHANGE; }
    private void cooldown(OtpCode otp) {
        long milliseconds = Duration.between(identityClock.instant(), otp.getCreatedAt().plusSeconds(registrationProperties.getOtpResendCooldownSeconds())).toMillis();
        if (milliseconds > 0) throw new IdentityException(IdentityErrorCode.OTP_RESEND_TOO_SOON, (milliseconds + 999) / 1000);
    }
}

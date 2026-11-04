package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.dto.request.UserCreationRequest;
import com.fptpost.modules.identity.dto.response.RegisterResponse;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.mapper.UserMapper;
import com.fptpost.modules.identity.model.Consent;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.BlacklistRepository;
import com.fptpost.modules.identity.repository.ConsentRepository;
import com.fptpost.modules.identity.repository.RoleRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.util.ContactUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fptpost.modules.identity.util.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    UserRepository userRepository;
    UserMapper userMapper;
    RoleRepository roleRepository;
    UserProfileRepository userProfileRepository;
    ConsentRepository consentRepository;
    BlacklistRepository blacklistRepository;
    PasswordEncoder passwordEncoder;
    OtpService otpService;
    RegistrationProperties registrationProperties;
    Clock identityClock;
    PasswordPolicy passwordPolicy;

    @Transactional
    public RegisterResponse createUser(UserCreationRequest request, String remoteAddress) {
        validatePolicyVersions(request);
        String phone = ContactUtils.normalizePhone(request.getPhone());
        passwordPolicy.validate(request.getPassword(), request.getEmail(), phone);
        validateContactUtils(request.getEmail(), phone);

        Instant now = identityClock.instant();
        User user = userMapper.toUser(request);
        user.setPublicId(UUID.randomUUID());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.getRoles().add(roleRepository.findByCode("SENDER").orElseThrow(
                () -> new IllegalStateException("Missing required SENDER role initialization")));

        // Flush unique constraints before sending anything to an external mail server.
        User savedUser = userRepository.saveAndFlush(user);
        saveProfile(savedUser, request.getFullName(), now);
        saveConsent(savedUser, "TERMS", request.getTermsVersion(), now, remoteAddress);
        saveConsent(savedUser, "PRIVACY", request.getPrivacyVersion(), now, remoteAddress);

        var otp = otpService.generateAndSendOtp(savedUser, request.getFullName());
        return userMapper.toRegisterResponse(savedUser, otp, registrationProperties);
    }

    private void validatePolicyVersions(UserCreationRequest request) {
        if (!registrationProperties.getTermsVersion().equals(request.getTermsVersion())
                || !registrationProperties.getPrivacyVersion().equals(request.getPrivacyVersion())) {
            throw new AppException(IdentityErrorCode.POLICY_CHANGED);
        }
    }

    private void validateContactUtils(String email, String phone) {
        if (blacklistRepository.existsByKindAndValueHash("PHONE", ContactUtils.phoneHash(phone))) {
            throw new AppException(IdentityErrorCode.REGISTRATION_NOT_ALLOWED);
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppException(IdentityErrorCode.EMAIL_ALREADY_USED);
        }
        if (userRepository.existsByPhone(phone)) {
            throw new AppException(IdentityErrorCode.PHONE_ALREADY_USED);
        }
    }

    private void saveProfile(User user, String fullName, Instant now) {
        UserProfile profile = UserProfile.builder()
                .user(user)
                .fullName(fullName)
                .adultConfirmedAt(now)
                .build();
        userProfileRepository.save(profile);
    }

    private void saveConsent(User user, String type, String version, Instant now, String ip) {
        Consent consent = Consent.builder()
                .user(user)
                .policyType(type)
                .policyVersion(version)
                .acceptedAt(now)
                .ip(ip)
                .build();
        consentRepository.save(consent);
    }

}

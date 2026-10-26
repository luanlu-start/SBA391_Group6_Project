package com.fptpost.modules.identity.mapper;

import com.fptpost.modules.identity.dto.request.UserCreationRequest;
import com.fptpost.modules.identity.dto.response.RegisterResponse;
import com.fptpost.modules.identity.dto.response.OtpChallengeResponse;
import com.fptpost.modules.identity.dto.response.OtpVerificationResponse;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.model.OtpCode;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.dto.response.UserResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "email", source = "email")
    @Mapping(target = "phone", source = "phone")
    User toUser(UserCreationRequest request);

    @Mapping(target = "id", source = "user.publicId")
    @Mapping(target = "fullName", source = "profile.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "status", source = "user.status")
    @Mapping(target = "emailVerifiedAt", source = "user.emailVerifiedAt")
    @Mapping(target = "dob", source = "profile.dob")
    @Mapping(target = "bio", source = "profile.bio")
    @Mapping(target = "emergencyContact", source = "profile.emergencyContact")
    @Mapping(target = "fptBadge", source = "user.fptBadge")
    @Mapping(target = "avatarAvailable", expression = "java(profile.getAvatarStorageKey() != null)")
    @Mapping(target = "roles", expression = "java(user.getRoles().stream().map(role -> role.getCode()).sorted().toList())")
    UserResponse toUserResponse(User user, UserProfile profile);

    @Mapping(target = "userId", source = "user.publicId")
    @Mapping(target = "status", source = "user.status")
    @Mapping(target = "challengeId", source = "otp.publicId")
    @Mapping(target = "expiresAt", source = "otp.expiresAt")
    @Mapping(target = "verificationChannel", constant = "EMAIL")
    @Mapping(target = "maskedTarget", source = "user.email", qualifiedByName = "maskEmail")
    @Mapping(target = "resendAvailableAt", expression = "java(otp.getCreatedAt().plusSeconds(properties.getOtpResendCooldownSeconds()))")
    RegisterResponse toRegisterResponse(User user, OtpCode otp, @Context RegistrationProperties properties);

    @Mapping(target = "challengeId", source = "publicId")
    @Mapping(target = "verificationChannel", constant = "EMAIL")
    @Mapping(target = "maskedTarget", source = "target", qualifiedByName = "maskEmail")
    @Mapping(target = "resendAvailableAt", expression = "java(otp.getCreatedAt().plusSeconds(properties.getOtpResendCooldownSeconds()))")
    OtpChallengeResponse toOtpChallengeResponse(OtpCode otp, @Context RegistrationProperties properties);

    @Mapping(target = "userId", source = "publicId")
    @Mapping(target = "verifiedAt", source = "emailVerifiedAt")
    @Mapping(target = "verificationChannel", constant = "EMAIL")
    OtpVerificationResponse toOtpVerificationResponse(User user);

    @Named("maskEmail")
    default String maskEmail(String email) {
        return email.substring(0, 1) + "***" + email.substring(email.indexOf('@'));
    }
}

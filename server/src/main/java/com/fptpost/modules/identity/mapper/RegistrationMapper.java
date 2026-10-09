package com.fptpost.modules.identity.mapper;

import com.fptpost.modules.identity.dto.RegisterResponse;
import com.fptpost.modules.identity.model.OtpCode;
import com.fptpost.modules.identity.model.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RegistrationMapper {
    @Mapping(target = "userId", source = "user.publicId")
    @Mapping(target = "status", source = "user.status")
    @Mapping(target = "challengeId", source = "otp.publicId")
    @Mapping(target = "expiresAt", source = "otp.expiresAt")
    @Mapping(target = "verificationChannel", constant = "EMAIL")
    @Mapping(target = "maskedTarget", source = "user.email", qualifiedByName = "maskEmail")
    RegisterResponse toResponse(User user, OtpCode otp);

    @Named("maskEmail")
    default String maskEmail(String email) {
        return email.substring(0, 1) + "***" + email.substring(email.indexOf('@'));
    }
}

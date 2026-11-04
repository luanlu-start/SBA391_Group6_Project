package com.fptpost.modules.identity.util;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordPolicy {
    private final RegistrationProperties properties;

    public void validate(String password, String email, String phone) {
        String localPhone = phone.startsWith("+84") ? "0" + phone.substring(3) : phone;
        if (password.codePointCount(0, password.length()) < properties.getPasswordMinLength()
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().noneMatch(Character::isUpperCase)
                || password.codePoints().noneMatch(Character::isLowerCase)
                || password.codePoints().noneMatch(Character::isDigit)
                || password.equalsIgnoreCase(email) || password.equals(phone) || password.equals(localPhone)) {
            throw new AppException(IdentityErrorCode.PASSWORD_INVALID);
        }
    }
}

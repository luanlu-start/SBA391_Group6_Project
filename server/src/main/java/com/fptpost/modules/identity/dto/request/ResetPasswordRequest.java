package com.fptpost.modules.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResetPasswordRequest {
    @NotBlank(message = "LOGIN_IDENTIFIER_REQUIRED")
    @Size(max = 254, message = "LOGIN_IDENTIFIER_INVALID")
    @Pattern(regexp = "(?:[^\\s@]+@[^\\s@]+\\.[^\\s@]+|0[35789]\\d{8}|\\+84[35789]\\d{8})", message = "RECOVERY_IDENTIFIER_INVALID")
    String identifier;

    @NotBlank(message = "OTP_REQUIRED")
    @Pattern(regexp = "[0-9]{6}", message = "OTP_FORMAT_INVALID")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String otp;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, max = 72, message = "PASSWORD_INVALID")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String newPassword;

    public void setIdentifier(String identifier) { this.identifier = identifier == null ? null : identifier.trim(); }
    @Override public String toString() { return "ResetPasswordRequest[redacted]"; }
}

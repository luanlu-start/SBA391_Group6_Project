package com.fptpost.modules.identity.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ForgotPasswordRequest {
    @NotBlank(message = "LOGIN_IDENTIFIER_REQUIRED")
    @Size(max = 254, message = "LOGIN_IDENTIFIER_INVALID")
    @Pattern(regexp = "(?:[^\\s@]+@[^\\s@]+\\.[^\\s@]+|0[35789]\\d{8}|\\+84[35789]\\d{8})", message = "RECOVERY_IDENTIFIER_INVALID")
    String identifier;

    public void setIdentifier(String identifier) { this.identifier = identifier == null ? null : identifier.trim(); }
    @Override public String toString() { return "ForgotPasswordRequest[redacted]"; }
}

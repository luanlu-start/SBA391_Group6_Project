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
public class AuthenticationRequest {
    @NotBlank(message = "LOGIN_IDENTIFIER_REQUIRED")
    @Size(max = 254, message = "LOGIN_IDENTIFIER_INVALID")
    String identifier;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(max = 72, message = "PASSWORD_INVALID")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String password;
    public void setIdentifier(String identifier) { this.identifier = identifier == null ? null : identifier.trim(); }
    @Override public String toString() { return "AuthenticationRequest[redacted]"; }
}

package com.fptpost.modules.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {
    @NotBlank(message = "CURRENT_PASSWORD_REQUIRED")
    @Size(max = 72, message = "CURRENT_PASSWORD_INVALID")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String currentPassword;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, max = 72, message = "PASSWORD_INVALID")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String newPassword;

    @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
}

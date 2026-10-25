package com.fptpost.modules.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContactVerificationRequest {
    @NotNull UUID challengeId;
    @NotBlank @Pattern(regexp = "[0-9]{6}") @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String otp;
    @Override public String toString() { return "ContactVerificationRequest[redacted]"; }
}

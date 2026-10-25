package com.fptpost.modules.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fptpost.modules.identity.enums.ContactType;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContactChangeRequest {
    @NotNull ContactType type;
    @NotBlank @Size(max = 254) String value;
    @NotBlank @Size(max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword;
    @Override public String toString() { return "ContactChangeRequest[redacted]"; }
}

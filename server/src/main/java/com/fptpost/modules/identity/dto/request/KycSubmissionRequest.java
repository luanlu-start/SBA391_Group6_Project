package com.fptpost.modules.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KycSubmissionRequest {
    @NotBlank @Pattern(regexp = "[0-9]{12}") @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String idNumber;
    @NotBlank @Size(max = 100) String fullName;
    @NotNull @Past LocalDate dob;
    @NotNull @PastOrPresent LocalDate issuedDate;
    @NotNull LocalDate expiryDate;
    @NotNull @AssertTrue(message = "KYC_CONSENT_REQUIRED") Boolean consentAccepted;
    @NotBlank @Size(max = 50) String policyVersion;
    @Override public String toString() { return "KycSubmissionRequest[redacted]"; }
}

package com.fptpost.modules.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    @NotBlank(message = "FULLNAME_REQUIRED")
    @Size(max = 100, message = "FULLNAME_INVALID")
    String fullName;

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "EMAIL_INVALID")
    @Size(max = 254, message = "EMAIL_INVALID")
    String email;

    @NotBlank(message = "PHONE_REQUIRED")
    @Pattern(regexp = "(?:0[35789]\\d{8}|\\+84[35789]\\d{8})",
            message = "PHONE_INVALID")
    String phone;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, max = 72, message = "PASSWORD_INVALID")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String password;

    @NotNull(message = "ADULT_CONFIRMATION_REQUIRED")
    @AssertTrue(message = "ADULT_CONFIRMATION_REQUIRED")
    Boolean adultConfirmed;

    @NotNull(message = "TERMS_NOT_ACCEPTED")
    @AssertTrue(message = "TERMS_NOT_ACCEPTED")
    Boolean termsAccepted;

    @NotNull(message = "PRIVACY_NOT_ACCEPTED")
    @AssertTrue(message = "PRIVACY_NOT_ACCEPTED")
    Boolean privacyAccepted;

    @NotBlank(message = "TERMS_VERSION_REQUIRED")
    @Size(max = 50, message = "POLICY_VERSION_INVALID")
    String termsVersion;

    @NotBlank(message = "PRIVACY_VERSION_REQUIRED")
    @Size(max = 50, message = "POLICY_VERSION_INVALID")
    String privacyVersion;

    public void setFullName(String fullName) {
        this.fullName = fullName == null ? null : fullName.trim();
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.trim();
    }

    // Never allow accidental logging of the submitted password or personal data.
    @Override
    public String toString() {
        return "UserCreationRequest[redacted]";
    }
}

package com.fptpost.modules.backoffice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StaffCreationRequest {

    @NotBlank(message = "FULLNAME_REQUIRED")
    @Size(max = 100, message = "FULLNAME_INVALID")
    String fullName;

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "EMAIL_INVALID")
    @Size(max = 254, message = "EMAIL_INVALID")
    String email;

    @NotBlank(message = "PHONE_REQUIRED")
    @Pattern(regexp = "(?:0[35789]\\d{8}|\\+84[35789]\\d{8})", message = "PHONE_INVALID")
    String phone;

    @NotNull
    @Pattern(regexp = "MODERATOR|ADMIN")
    String role;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.trim();
    }
}

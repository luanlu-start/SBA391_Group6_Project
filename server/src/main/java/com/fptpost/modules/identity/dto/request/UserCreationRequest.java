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
    @NotBlank
    @Size(max = 100)
    String fullName;

    @NotBlank
    @Email
    @Size(max = 254)
    String email;

    @NotBlank
    @Pattern(regexp = "(?:0[35789]\\d{8}|\\+84[35789]\\d{8})",
            message = "Số điện thoại di động Việt Nam không hợp lệ")
    String phone;

    @NotBlank
    @Size(min = 8, max = 72)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String password;

    @NotNull
    @AssertTrue(message = "Bạn cần xác nhận đủ 18 tuổi")
    Boolean adultConfirmed;

    @NotNull
    @AssertTrue(message = "Bạn cần đồng ý điều khoản sử dụng")
    Boolean termsAccepted;

    @NotNull
    @AssertTrue(message = "Bạn cần đồng ý chính sách dữ liệu")
    Boolean privacyAccepted;

    @NotBlank
    @Size(max = 50)
    String termsVersion;

    @NotBlank
    @Size(max = 50)
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

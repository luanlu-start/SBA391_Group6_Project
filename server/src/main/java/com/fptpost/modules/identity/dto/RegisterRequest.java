package com.fptpost.modules.identity.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "(?:0[35789]\\d{8}|\\+84[35789]\\d{8})",
                message = "Số điện thoại di động Việt Nam không hợp lệ") String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull @AssertTrue(message = "Bạn cần xác nhận đủ 18 tuổi") Boolean adultConfirmed,
        @NotNull @AssertTrue(message = "Bạn cần đồng ý điều khoản sử dụng") Boolean termsAccepted,
        @NotNull @AssertTrue(message = "Bạn cần đồng ý chính sách dữ liệu") Boolean privacyAccepted,
        @NotBlank @Size(max = 50) String termsVersion,
        @NotBlank @Size(max = 50) String privacyVersion) {
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        phone = phone == null ? null : phone.trim();
    }

    // Never allow accidental logging of the submitted password or personal data.
    @Override public String toString() { return "RegisterRequest[redacted]"; }
}

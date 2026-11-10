package com.fptpost.modules.guarantee.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Ghi nhận khoản trích từ doanh thu nền tảng vào Quỹ bảo đảm (phí bảo hiểm, phần trăm hoa hồng). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FundContributionRequest {

    @NotNull
    @Positive
    @Max(10_000_000_000L)
    Long amount;

    @NotBlank
    @Size(max = 300)
    String note;
}

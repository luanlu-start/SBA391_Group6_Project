package com.fptpost.modules.backoffice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountLockRequest {

    @NotBlank
    @Size(max = 500)
    String reason;

    /** Để trống để khóa vĩnh viễn; tối đa 365 ngày. */
    @Min(1)
    @Max(8760)
    Integer durationHours;

    boolean blacklistPhone;
}

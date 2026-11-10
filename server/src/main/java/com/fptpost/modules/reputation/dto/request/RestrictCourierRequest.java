package com.fptpost.modules.reputation.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Admin tạm khóa nhận đơn sau khi xem xét người giao có điểm dưới TRUST_REVIEW (BR-R-074). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RestrictCourierRequest {

    @NotNull
    @Min(1)
    @Max(30)
    Integer days;

    @NotBlank
    @Size(max = 300)
    String reason;
}

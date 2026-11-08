package com.fptpost.modules.dispute.dto.request;

import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComplaintRulingRequest {

    @NotNull
    ComplaintOutcome outcome;

    /** Bắt buộc khi chia theo tỷ lệ: phần trăm ký quỹ hoàn cho người gửi. */
    @Min(1)
    @Max(99)
    Integer senderPercent;

    @PositiveOrZero
    Long compensationAmount;

    @NotBlank
    @Size(max = 1000)
    String reason;
}

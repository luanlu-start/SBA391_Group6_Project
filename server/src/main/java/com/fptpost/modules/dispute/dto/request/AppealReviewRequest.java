package com.fptpost.modules.dispute.dto.request;

import com.fptpost.modules.dispute.enums.AppealDecision;
import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Quyết định của Admin với kháng nghị; khi sửa phán quyết cần gửi lại phán quyết mới. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppealReviewRequest {

    @NotNull
    AppealDecision decision;

    ComplaintOutcome outcome;

    @Min(1)
    @Max(99)
    Integer senderPercent;

    @PositiveOrZero
    Long compensationAmount;

    @NotBlank
    @Size(max = 1000)
    String note;
}

package com.fptpost.modules.guarantee.dto.request;

import com.fptpost.modules.guarantee.enums.CompensationDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Duyệt (có thể giảm mức đề nghị) hoặc từ chối bồi thường; từ chối phải có lý do. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompensationDecisionRequest {

    @NotNull
    CompensationDecision decision;

    @Positive
    Long amount;

    @Size(max = 500)
    String note;
}

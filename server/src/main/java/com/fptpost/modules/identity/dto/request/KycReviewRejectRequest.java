package com.fptpost.modules.identity.dto.request;

import com.fptpost.modules.identity.enums.KycRejectionReason;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class KycReviewRejectRequest {
    @NotNull @Min(0) private Long expectedVersion;
    @NotNull private KycRejectionReason reasonCode;
    @Size(max = 400) private String note;
}

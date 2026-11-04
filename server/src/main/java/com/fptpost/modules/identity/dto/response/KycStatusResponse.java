package com.fptpost.modules.identity.dto.response;

import java.time.Instant;
import com.fptpost.modules.identity.enums.KycStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KycStatusResponse {
    KycStatus status;
    KycRequestResponse request;
    int submissionsRemaining;
    Instant nextSubmissionAt;
    String policyVersion;
}

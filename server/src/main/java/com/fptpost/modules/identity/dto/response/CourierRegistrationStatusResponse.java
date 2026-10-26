package com.fptpost.modules.identity.dto.response;

import com.fptpost.modules.identity.enums.KycStatus;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CourierRegistrationStatusResponse {
    private CourierProfileResponse profile;
    private KycStatus kycStatus;
    private boolean eligible;
    private String commitmentVersion;
}

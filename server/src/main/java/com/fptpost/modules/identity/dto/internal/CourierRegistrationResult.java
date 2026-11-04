package com.fptpost.modules.identity.dto.internal;

import com.fptpost.modules.identity.dto.response.CourierProfileResponse;
import lombok.*;

@Getter @RequiredArgsConstructor
public class CourierRegistrationResult {
    private final CourierProfileResponse profile;
    private final AuthenticationResult authentication;
}

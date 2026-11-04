package com.fptpost.modules.identity.dto.internal;

import com.fptpost.modules.identity.dto.response.AuthenticationResponse;
import lombok.*;
import java.util.UUID;

@Getter
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class AuthenticationResult {
    private final AuthenticationResponse response;
    private final String refreshToken;
    private final boolean rememberMe;
    private final UUID browserId;
}

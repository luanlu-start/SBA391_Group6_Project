package com.fptpost.modules.identity.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CourierRegistrationResponse {
    private CourierProfileResponse profile;
    private AuthenticationResponse authentication;
}

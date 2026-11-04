package com.fptpost.modules.identity.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CsrfTokenResponse {
    String headerName;
    @ToString.Exclude String token;
}

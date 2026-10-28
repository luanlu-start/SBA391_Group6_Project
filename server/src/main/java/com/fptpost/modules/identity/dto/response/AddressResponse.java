package com.fptpost.modules.identity.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddressResponse {
    UUID id;
    String label;
    String addressText;
    BigDecimal lat;
    BigDecimal lng;
    Boolean isDefault;
}

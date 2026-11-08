package com.fptpost.modules.backoffice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlacklistRequest {

    @NotNull
    @Pattern(regexp = "PHONE|ID_NUMBER|DEVICE")
    String kind;

    @NotBlank
    @Size(max = 200)
    String value;

    @NotBlank
    @Size(max = 500)
    String reason;
}

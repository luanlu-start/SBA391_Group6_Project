package com.fptpost.modules.identity.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileUpdateRequest {
    @NotBlank @Size(min = 2, max = 100) String fullName;
    @Past LocalDate dob;
    @Size(max = 500) String bio;
    @Size(max = 200) String emergencyContact;
}

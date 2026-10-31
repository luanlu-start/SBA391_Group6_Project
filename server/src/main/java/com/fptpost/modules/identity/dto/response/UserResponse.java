package com.fptpost.modules.identity.dto.response;

import com.fptpost.modules.identity.enums.UserStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    UUID id;
    String fullName;
    String email;
    String phone;
    UserStatus status;
    List<String> roles;
    Instant emailVerifiedAt;
    LocalDate dob;
    String bio;
    String emergencyContact;
    boolean fptBadge;
    boolean avatarAvailable;
}

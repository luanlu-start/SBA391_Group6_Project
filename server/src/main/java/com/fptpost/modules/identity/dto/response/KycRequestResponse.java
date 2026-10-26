package com.fptpost.modules.identity.dto.response;

import java.time.*;
import java.util.*;
import com.fptpost.modules.identity.enums.KycStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KycRequestResponse {
    UUID id;
    KycStatus status;
    String fullName;
    LocalDate dob;
    LocalDate issuedDate;
    LocalDate expiryDate;
    String idNumberMasked;
    Instant submittedAt;
    Instant reviewedAt;
    String rejectReason;
    List<KycFileResponse> files;
}

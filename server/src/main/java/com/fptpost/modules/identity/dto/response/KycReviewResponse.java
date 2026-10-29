package com.fptpost.modules.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fptpost.modules.identity.enums.KycStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class KycReviewResponse {
    private UUID id;
    private UUID applicantId;
    private String fullName;
    private String email;
    private String idNumberMasked;
    private LocalDate dob;
    private LocalDate issuedDate;
    private LocalDate expiryDate;
    private KycStatus status;
    private Instant submittedAt;
    private Instant reviewedAt;
    private String reviewerName;
    private String rejectReason;
    private boolean flagged;
    private String flagReason;
    private long version;
    private Instant dueAt;
    private boolean overdue;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<KycFileResponse> files;
}

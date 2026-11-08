package com.fptpost.modules.moderation.dto.response;

import com.fptpost.modules.moderation.enums.ViolationReason;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ViolationReportItemResponse {
    ViolationParty reporter;
    ViolationReason reason;
    String description;
    Instant createdAt;
    List<UUID> evidenceIds;
}

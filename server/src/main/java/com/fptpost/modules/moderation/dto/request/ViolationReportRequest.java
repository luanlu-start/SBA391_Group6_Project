package com.fptpost.modules.moderation.dto.request;

import com.fptpost.modules.moderation.enums.ViolationReason;
import com.fptpost.modules.moderation.enums.ViolationTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ViolationReportRequest {

    @NotNull
    ViolationTargetType targetType;

    /** Mã đơn (số), mã người dùng (UUID) hoặc mã tin nhắn. */
    @NotBlank
    @Size(max = 64)
    String targetId;

    @NotNull
    ViolationReason reason;

    @Size(max = 1000)
    String description;
}

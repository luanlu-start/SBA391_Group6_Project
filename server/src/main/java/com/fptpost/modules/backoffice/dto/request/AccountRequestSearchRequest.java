package com.fptpost.modules.backoffice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountRequestSearchRequest {

    /** PENDING (mặc định), PROCESSING, COMPLETED, REJECTED hoặc ALL. */
    String status;

    /** DATA_EXPORT, ACCOUNT_DELETION hoặc để trống. */
    String type;

    @Builder.Default
    @Min(0)
    Integer page = 0;

    @Builder.Default
    @Min(1)
    @Max(100)
    Integer size = 20;
}

package com.fptpost.modules.identity.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AvailabilityWindowRequest {
    @NotNull private DayOfWeek day;
    @NotNull private LocalTime start;
    @NotNull private LocalTime end;
}

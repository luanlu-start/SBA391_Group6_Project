package com.fptpost.modules.identity.dto.response;

import java.time.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AvailabilityWindowResponse { private DayOfWeek day; private LocalTime start; private LocalTime end; }

package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Embeddable @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AvailabilityWindow {
    @Enumerated(EnumType.STRING) @Column(name = "day_of_week", nullable = false, length = 10) private DayOfWeek day;
    @Column(name = "starts_at", nullable = false) private LocalTime start;
    @Column(name = "ends_at", nullable = false) private LocalTime end;
}

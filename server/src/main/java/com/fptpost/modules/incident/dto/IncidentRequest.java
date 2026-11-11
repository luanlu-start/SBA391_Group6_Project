package com.fptpost.modules.incident.dto;

import com.fptpost.modules.incident.model.IncidentType;
import jakarta.validation.constraints.*;

public record IncidentRequest(@NotNull IncidentType type,
                              @NotBlank @Size(max = 1000) String description) {}

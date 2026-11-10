package com.fptpost.modules.incident.dto;

import com.fptpost.modules.incident.model.DeliveryIncident;
import com.fptpost.modules.incident.model.IncidentType;
import java.time.Instant;

public record IncidentResponse(Long id, Long orderId, Long reporterId, IncidentType type,
                               String description, boolean hasEvidence, Instant createdAt) {
    public static IncidentResponse from(DeliveryIncident incident) {
        return new IncidentResponse(incident.getId(), incident.getOrder().getId(), incident.getReporterId(),
                incident.getType(), incident.getDescription(), incident.getEvidenceKey() != null, incident.getCreatedAt());
    }
}

package com.fptpost.modules.activity.dto;

import java.time.Instant;

public record ActivityLogResponse(String id, String entityType, String entityId, String action,
                                  String entityName, Instant occurredAt) {}

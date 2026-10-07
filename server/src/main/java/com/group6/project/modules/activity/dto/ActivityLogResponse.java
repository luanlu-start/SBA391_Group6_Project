package com.group6.project.modules.activity.dto;

import java.time.Instant;

public record ActivityLogResponse(String id, String entityType, String entityId, String action,
                                  String entityName, Instant occurredAt) {}

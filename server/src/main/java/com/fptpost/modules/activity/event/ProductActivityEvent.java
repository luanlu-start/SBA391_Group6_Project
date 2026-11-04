package com.fptpost.modules.activity.event;

import java.time.Instant;
import java.util.UUID;

public record ProductActivityEvent(String action, UUID productId, String productName, Instant occurredAt) {}

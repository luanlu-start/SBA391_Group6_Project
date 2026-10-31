package com.fptpost.common.audit;

import java.util.UUID;

/** A lease token fences workers that finish after another worker recovered their expired lease. */
public record AuditOutboxClaim(Long id, UUID eventId, String payload, UUID claimToken) { }

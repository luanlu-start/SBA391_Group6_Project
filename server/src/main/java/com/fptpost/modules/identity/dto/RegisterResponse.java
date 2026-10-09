package com.fptpost.modules.identity.dto;

import com.fptpost.modules.identity.model.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(UUID userId, UserStatus status, UUID challengeId,
                               String verificationChannel, String maskedTarget, Instant expiresAt) {}

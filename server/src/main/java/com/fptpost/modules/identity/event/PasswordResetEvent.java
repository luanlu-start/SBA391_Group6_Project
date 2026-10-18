package com.fptpost.modules.identity.event;

import java.time.Instant;

public record PasswordResetEvent(String email, String fullName, Instant changedAt) {
    @Override public String toString() { return "PasswordResetEvent[redacted]"; }
}

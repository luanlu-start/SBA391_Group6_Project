package com.fptpost.modules.identity.event;

import java.time.Instant;

public record PasswordChangedEvent(String email, String fullName, Instant changedAt) {
    @Override public String toString() { return "PasswordChangedEvent[redacted]"; }
}

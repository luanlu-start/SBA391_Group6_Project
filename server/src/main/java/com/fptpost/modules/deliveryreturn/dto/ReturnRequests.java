package com.fptpost.modules.deliveryreturn.dto;

import com.fptpost.modules.deliveryreturn.model.FailureReason;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class ReturnRequests {
    private ReturnRequests() {}
    public record Contact(@NotNull UUID requestId, @NotBlank @Size(max = 500) String note) {}
    public record Failure(@NotNull FailureReason reason, @NotBlank @Size(max = 1000) String description) {}
    public record Resume(@NotBlank @Size(max = 1000) String description) {}
    public record Confirm(@NotBlank @Pattern(regexp = "[0-9]{6}") String code, @NotNull @AssertTrue Boolean received) {
        @Override public String toString() { return "Confirm[redacted]"; }
    }
}

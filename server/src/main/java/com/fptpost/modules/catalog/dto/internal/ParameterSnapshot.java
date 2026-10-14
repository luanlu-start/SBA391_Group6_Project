package com.fptpost.modules.catalog.dto.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Capture this value at order creation/acceptance, rather than re-reading current parameters. */
public record ParameterSnapshot(Map<String, String> values, Map<String, Long> versions, Instant capturedAt) {
    public ParameterSnapshot {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        versions = Collections.unmodifiableMap(new LinkedHashMap<>(versions));
        Objects.requireNonNull(capturedAt);
        if (!values.keySet().equals(versions.keySet())) {
            throw new IllegalArgumentException("Snapshot values and versions must describe the same keys");
        }
    }

    public String value(String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown parameter key: " + key);
        return value;
    }

    public BigDecimal decimal(String key) {
        return new BigDecimal(value(key));
    }

    public long integer(String key) {
        return decimal(key).longValueExact();
    }
}

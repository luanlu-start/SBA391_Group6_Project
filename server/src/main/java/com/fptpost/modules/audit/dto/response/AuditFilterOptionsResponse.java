package com.fptpost.modules.audit.dto.response;

import java.util.List;

public record AuditFilterOptionsResponse(List<Option> options, boolean hasMore) {
    public record Option(String value, String label, String description) {}
}

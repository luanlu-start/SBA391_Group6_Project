package com.fptpost.modules.identity.dto;

import java.util.List;

public record RegistrationPoliciesResponse(Policy terms, Policy privacy, int passwordMinLength) {
    public record Policy(String version, String title, List<String> paragraphs) {}
}

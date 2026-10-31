package com.fptpost.modules.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.dto.response.RegistrationPoliciesResponse;
import com.fptpost.modules.identity.dto.response.PolicyResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class PolicyService {
    private final RegistrationProperties properties;
    private final PolicyResponse terms;
    private final PolicyResponse privacy;

    public PolicyService(RegistrationProperties properties, ObjectMapper objectMapper) throws IOException {
        this.properties = properties;
        this.terms = readPolicy(objectMapper, "policies/terms.json");
        this.privacy = readPolicy(objectMapper, "policies/privacy.json");
    }

    public RegistrationPoliciesResponse getPolicies() {
        return RegistrationPoliciesResponse.builder()
                .terms(withVersion(terms, properties.getTermsVersion()))
                .privacy(withVersion(privacy, properties.getPrivacyVersion()))
                .passwordMinLength(properties.getPasswordMinLength())
                .build();
    }

    private PolicyResponse readPolicy(ObjectMapper objectMapper, String path) throws IOException {
        // Read once at startup; getInputStream also works for resources inside the executable JAR.
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(input, PolicyResponse.class);
        }
    }

    private PolicyResponse withVersion(PolicyResponse content, String version) {
        return PolicyResponse.builder()
                .version(version)
                .title(content.getTitle())
                .paragraphs(List.copyOf(content.getParagraphs()))
                .build();
    }
}

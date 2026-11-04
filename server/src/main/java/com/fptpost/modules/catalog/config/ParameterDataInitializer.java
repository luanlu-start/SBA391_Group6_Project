package com.fptpost.modules.catalog.config;

import com.fptpost.modules.catalog.dto.internal.ParameterDefinition;
import com.fptpost.modules.catalog.model.SystemParameter;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import com.fptpost.modules.catalog.service.ParameterCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Required reference data: restart inserts missing keys and never resets an administrator's edits. */
@Component
@Order(10)
@RequiredArgsConstructor
public class ParameterDataInitializer implements CommandLineRunner {
    private final SystemParameterRepository systemParameterRepository;
    private final ParameterCatalog parameterCatalog;

    @Override
    public void run(String... args) {
        for (ParameterDefinition definition : parameterCatalog.definitions()) {
            if (systemParameterRepository.findByKey(definition.key()).isPresent()) continue;
            SystemParameter parameter = SystemParameter.builder()
                    .publicId(UUID.nameUUIDFromBytes(("fptpost:" + definition.prmId()).getBytes(StandardCharsets.UTF_8)))
                    .prmId(definition.prmId()).key(definition.key()).group(definition.group())
                    .name(definition.name()).valueType(definition.valueType()).value(definition.defaultValue())
                    .unit(definition.unit()).description(definition.description())
                    // Version 0 is the initial baseline, not an administrator-authored change.
                    .effectiveFrom(Instant.EPOCH).createdAt(Instant.EPOCH).updatedAt(Instant.EPOCH).build();
            try {
                systemParameterRepository.saveAndFlush(parameter);
            } catch (DataIntegrityViolationException exception) {
                // Each repository call owns its transaction; a concurrent seed may win this key.
                if (systemParameterRepository.findByKey(definition.key()).isEmpty()) throw exception;
            }
        }
    }
}

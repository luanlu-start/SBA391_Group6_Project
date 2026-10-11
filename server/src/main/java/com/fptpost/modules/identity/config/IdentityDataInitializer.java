package com.fptpost.modules.identity.config;

import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Required reference data, independent of the optional demo profile.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class IdentityDataInitializer implements CommandLineRunner {
    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        for (String code : List.of("SENDER", "COURIER", "MODERATOR", "ADMIN")) {
            if (roleRepository.findByCode(code).isPresent()) continue;
            try {
                roleRepository.saveAndFlush(Role.builder().code(code).build());
            } catch (DataIntegrityViolationException exception) {
                // Repository transactions are separate: tolerate a concurrent initializer only.
                if (roleRepository.findByCode(code).isEmpty()) throw exception;
            }
        }
    }
}

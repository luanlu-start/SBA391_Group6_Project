package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.catalog.config.ParameterDataInitializer;
import com.fptpost.modules.catalog.model.SystemParameter;
import com.fptpost.modules.catalog.repository.SystemParameterRepository;
import com.fptpost.modules.catalog.service.ParameterCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ParameterDataInitializerTest {
    @Test void repeatedSeedAddsOnlyMissingSourceKeysAndKeepsEditedValues() {
        SystemParameterRepository repository = mock(SystemParameterRepository.class);
        Map<String, SystemParameter> existing = new HashMap<>();
        existing.put("OTP_TTL", SystemParameter.builder().key("OTP_TTL").value("7").version(4L).build());
        when(repository.findByKey(any())).thenAnswer(call -> Optional.ofNullable(existing.get(call.getArgument(0))));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            SystemParameter parameter = call.getArgument(0);
            existing.put(parameter.getKey(), parameter);
            return parameter;
        });
        var seeder = new ParameterDataInitializer(repository, new ParameterCatalog(new ObjectMapper()));
        seeder.run(); seeder.run();
        assertThat(existing).hasSize(109);
        assertThat(existing.get("OTP_TTL").getValue()).isEqualTo("7");
        assertThat(existing.get("OTP_TTL").getVersion()).isEqualTo(4L);
        assertThat(existing.get("BASE_FARE").getPublicId()).isNotNull();
        verify(repository, times(108)).saveAndFlush(any());
    }

    @Test void concurrentSeedMayWinAKeyButUnrelatedIntegrityFailureIsNotIgnored() {
        SystemParameterRepository repository = mock(SystemParameterRepository.class);
        var parameter = SystemParameter.builder().key("OTP_TTL").value("9").build();
        when(repository.findByKey("OTP_TTL")).thenReturn(Optional.empty(), Optional.of(parameter));
        when(repository.findByKey(argThat(key -> !"OTP_TTL".equals(key))))
                .thenReturn(Optional.of(SystemParameter.builder().build()));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Concurrent key"));
        new ParameterDataInitializer(repository, new ParameterCatalog(new ObjectMapper())).run();
        verify(repository, times(1)).saveAndFlush(any());

        reset(repository);
        when(repository.findByKey(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Other constraint"));
        assertThatThrownBy(() -> new ParameterDataInitializer(repository, new ParameterCatalog(new ObjectMapper())).run())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

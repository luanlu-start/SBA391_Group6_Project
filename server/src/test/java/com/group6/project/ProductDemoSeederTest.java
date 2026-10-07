package com.group6.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group6.project.modules.product.config.ProductDemoSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductDemoSeederTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> jdbc)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(ProductDemoSeeder.class);

    @Test
    void seedRunsOnlyForDemoAndNeverForProd() {
        contextRunner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).doesNotHaveBean(ProductDemoSeeder.class));
        contextRunner.withPropertyValues("spring.profiles.active=prod,demo")
                .run(context -> assertThat(context).doesNotHaveBean(ProductDemoSeeder.class));
        contextRunner.withPropertyValues("spring.profiles.active=dev,demo")
                .run(context -> assertThat(context).hasSingleBean(ProductDemoSeeder.class));
        // Spring invokes JdbcTemplate's lifecycle callback when registering the mock as a bean.
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void repeatedSeedInsertsOnlyMissingProductsWithoutOverwritingExistingRows() throws Exception {
        Set<String> existing = new HashSet<>(Set.of("00000000-0000-4000-8000-000000000001"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenAnswer(call -> existing.contains(call.getArgument(2)) ? 1 : 0);
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            assertThat(sql).startsWith("INSERT INTO products");
            assertThat(call.<Timestamp>getArgument(8)).isEqualTo(call.<Timestamp>getArgument(9));
            existing.add(call.getArgument(1));
            return 1;
        });
        ProductDemoSeeder seeder = new ProductDemoSeeder(jdbc, new ObjectMapper());
        seeder.run();
        seeder.run();
        assertThat(existing).hasSize(3);
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void concurrentDuplicateInsertDoesNotOverwriteExistingProduct() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("Concurrent insert"));
        new ProductDemoSeeder(jdbc, new ObjectMapper()).run();
        verify(jdbc, times(3)).update(anyString(), any(Object[].class));
    }
}

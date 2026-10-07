package com.group6.project;

import com.group6.project.modules.activity.mapper.ActivityLogMapper;
import com.group6.project.modules.activity.repository.ActivityLogRepository;
import com.group6.project.modules.activity.service.ActivityLogService;
import com.group6.project.modules.product.dto.ProductRequest;
import com.group6.project.modules.product.mapper.ProductMapper;
import com.group6.project.modules.product.repository.ProductRepository;
import com.group6.project.modules.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:activitytx;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password="})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductService.class, ActivityLogService.class, ActivityTransactionTest.MapperConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ActivityTransactionTest {
    @Autowired private ProductService products;
    @Autowired private ProductRepository productRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private ActivityLogRepository logs;

    @Test
    void writesHistoryOnlyAfterSqlTransactionCommits() {
        var transaction = new TransactionTemplate(transactionManager);
        UUID id = transaction.execute(status -> {
            UUID created = products.createProduct(request()).getId();
            verifyNoInteractions(logs);
            return created;
        });
        assertThat(productRepository.existsById(id)).isTrue();
        verify(logs).save(argThat(log -> log.getEntityId().equals(id.toString()) && log.getAction().equals("CREATED")));
        productRepository.deleteById(id);
    }

    @Test
    void sqlRollbackDoesNotLeaveProductOrMongoHistory() {
        var id = new AtomicReference<UUID>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            id.set(products.createProduct(request()).getId());
            status.setRollbackOnly();
        });
        assertThat(productRepository.existsById(id.get())).isFalse();
        verifyNoInteractions(logs);
    }

    private ProductRequest request() {
        return ProductRequest.builder().name("Transaction test").category("Other")
                .price(new BigDecimal("0.10")).stock(1).build();
    }

    @TestConfiguration
    static class MapperConfig {
        @Bean ProductMapper productMapper() { return Mappers.getMapper(ProductMapper.class); }
        @Bean ActivityLogMapper activityLogMapper() { return Mappers.getMapper(ActivityLogMapper.class); }
    }
}

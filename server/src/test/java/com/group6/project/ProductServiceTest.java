package com.group6.project;

import com.group6.project.common.exception.AppException;
import com.group6.project.modules.activity.event.ProductActivityEvent;
import com.group6.project.modules.product.dto.ProductRequest;
import com.group6.project.modules.product.dto.ProductSearchRequest;
import com.group6.project.modules.product.exception.ProductErrorCode;
import com.group6.project.modules.product.mapper.ProductMapper;
import com.group6.project.modules.product.model.Product;
import com.group6.project.modules.product.repository.ProductRepository;
import com.group6.project.modules.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ProductService service = new ProductService(repository, Mappers.getMapper(ProductMapper.class), events);

    @Test
    void delegatesSearchAndPreservesPageMetadata() {
        var request = new ProductSearchRequest();
        request.setPage(1);
        UUID id = UUID.randomUUID();
        var product = Product.builder().id(id).name("MacBook").build();
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Product>>any(), eq(request.toPageable())))
                .thenReturn(new PageImpl<>(List.of(product), request.toPageable(), 25));
        var result = service.getAllProducts(request);
        assertThat(result.content()).singleElement().satisfies(item -> assertThat(item.getId()).isEqualTo(id));
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(25);
        verifyNoInteractions(events);
    }

    @Test
    void missingProductHasBusinessErrorForReadUpdateAndDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertMissing(() -> service.getProductById(id));
        assertMissing(() -> service.updateProduct(id, new ProductRequest()));
        assertMissing(() -> service.deleteProduct(id));
        verify(repository, never()).save(any(Product.class));
        verify(repository, never()).deleteById(any());
        verifyNoInteractions(events);
    }

    @Test
    void databaseWriteFailurePropagatesWithoutPublishingActivity() {
        var failure = new DataAccessResourceFailureException("Database unavailable");
        when(repository.save(any(Product.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.createProduct(ProductRequest.builder()
                .name("New product").price(new BigDecimal("12.50")).stock(1).category("Electronics").build()))
                .isSameAs(failure);
        verifyNoInteractions(events);
    }

    @Test
    void updatePreservesIdentityAndPublishesSavedProductActivity() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        Product existing = Product.builder().id(id).createdAt(created).status("INACTIVE").build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        var response = service.updateProduct(id, ProductRequest.builder()
                .name("Updated").price(BigDecimal.ZERO).stock(0).category("Accessories").build());
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCreatedAt()).isEqualTo(created);
        assertThat(response.getUpdatedAt()).isAfter(created);
        assertThat(response.getStatus()).isEqualTo("INACTIVE");
        verify(events).publishEvent(org.mockito.ArgumentMatchers.<ProductActivityEvent>argThat(
                event -> event.action().equals("UPDATED") && event.productId().equals(id)
                        && event.productName().equals("Updated")));
    }

    @Test
    void createUsesTheIdAssignedByJpaBeforePublishingActivity() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(Product.class))).thenAnswer(call -> {
            Product product = call.getArgument(0);
            assertThat(product.getId()).isNull();
            product.setId(id);
            return product;
        });
        var response = service.createProduct(ProductRequest.builder().name("New product")
                .price(new BigDecimal("0.10")).stock(1).category("Electronics").build());
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getPrice()).isEqualByComparingTo("0.10");
        verify(events).publishEvent(org.mockito.ArgumentMatchers.<ProductActivityEvent>argThat(
                event -> event.action().equals("CREATED") && event.productId().equals(id)));
    }

    private void assertMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}

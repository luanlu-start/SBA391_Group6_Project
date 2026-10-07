package com.group6.project;

import com.group6.project.common.exception.AppException;
import com.group6.project.common.exception.GlobalExceptionHandler;
import com.group6.project.common.response.ApiResponse;
import com.group6.project.modules.product.dto.ProductRequest;
import com.group6.project.modules.product.exception.ProductErrorCode;
import com.group6.project.modules.product.mapper.ProductMapper;
import com.group6.project.modules.product.model.Product;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackendContractTest {
    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Test
    void createDefaultsStatusAndMapsZeroValues() {
        Product product = mapper.toEntity(ProductRequest.builder()
                .name("Product").category("Electronics").price(BigDecimal.ZERO).stock(0).build());
        assertThat(product.getStatus()).isEqualTo("ACTIVE");
        assertThat(mapper.toResponse(product).getPrice()).isZero();
        assertThat(product.getStock()).isZero();
        assertThat(product.getId()).isNull();
    }

    @Test
    void updatePreservesIdentityDatesAndOmittedStatusButCanClearDescription() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        Product product = Product.builder().id(id).createdAt(created).updatedAt(created)
                .status("INACTIVE").description("Old description").build();
        mapper.updateEntity(ProductRequest.builder().name("Updated").price(BigDecimal.ZERO)
                .stock(0).category("Accessories").build(), product);
        assertThat(product.getId()).isEqualTo(id);
        assertThat(product.getCreatedAt()).isEqualTo(created);
        assertThat(product.getUpdatedAt()).isEqualTo(created);
        assertThat(product.getStatus()).isEqualTo("INACTIVE");
        assertThat(product.getDescription()).isNull();
        mapper.updateEntity(ProductRequest.builder().status("ACTIVE").build(), product);
        assertThat(product.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void separatesBusinessCodeFromHttpStatus() {
        var response = new GlobalExceptionHandler().handleAppException(
                new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo(2001);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(ApiResponse.success("data").getCode()).isEqualTo(1000);
        assertThat(ApiResponse.created("Created", "data").getStatus()).isEqualTo(201);
    }

    @Test
    void internalErrorsDoNotExposeExceptionDetails() {
        var response = new GlobalExceptionHandler().handleGenericException(
                new RuntimeException("private database details"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo(9999);
        assertThat(response.getBody().getMessage()).doesNotContain("private database details");
    }
}

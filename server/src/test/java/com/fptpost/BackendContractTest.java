package com.fptpost;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalExceptionHandler;
import com.fptpost.common.exception.ProblemResponseFactory;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.FieldError;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.product.dto.ProductRequest;
import com.fptpost.modules.product.exception.ProductErrorCode;
import com.fptpost.modules.product.mapper.ProductMapper;
import com.fptpost.modules.product.model.Product;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackendContractTest {
    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private GlobalExceptionHandler handler() {
        return new GlobalExceptionHandler(problems());
    }

    private ProblemResponseFactory problems() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages/errors");
        messages.setDefaultEncoding("UTF-8");
        return new ProblemResponseFactory(messages);
    }

    @Test
    void resolvesRegisteredValidationCodeWithoutExposingRejectedPassword() {
        var error = new FieldError("request", "password", "private-password", false,
                new String[]{"Size"}, null, "PASSWORD_INVALID");
        var violation = problems().violation(error);
        assertThat(violation.getCode()).isEqualTo("PASSWORD_INVALID");
        assertThat(violation.getMessage()).contains("8", "72").doesNotContain("private-password");
    }

    @Test
    void unregisteredOrLiteralMessagesFallBackToConstraintCode() {
        for (String message : new String[]{"UNKNOWN_SECRET_CODE", "private database details"}) {
            var error = new FieldError("request", "email", "private@example.com", false,
                    new String[]{"Email"}, null, message);
            var violation = problems().violation(error);
            assertThat(violation.getCode()).isEqualTo("EMAIL");
            assertThat(violation.getMessage()).isEqualTo("Email không hợp lệ.");
        }
    }

    @Test
    void bindingFailuresUseTypeMismatchEvenWhenMessageLooksLikeRegisteredCode() {
        var error = new FieldError("request", "amount", "private-input", true,
                new String[]{"typeMismatch"}, null, "PASSWORD_INVALID");
        assertThat(problems().violation(error).getCode()).isEqualTo("TYPE_MISMATCH");
    }

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
        var response = handler().handleAppException(
                new AppException(ProductErrorCode.PRODUCT_NOT_FOUND), request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(response.getBody().getCorrelationId()).hasSize(36);
        assertThat(response.getBody().getDetail()).isEqualTo("Sản phẩm không tồn tại hoặc đã bị xóa.");
        assertThat(ApiResponse.success("data").getCode()).isEqualTo(1000);
        assertThat(ApiResponse.created("Created", "data").getStatus()).isEqualTo(201);
    }

    @Test
    void internalErrorsDoNotExposeExceptionDetails() {
        var response = handler().handleGenericException(
                new RuntimeException("private database details"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo("SYS_INTERNAL_ERROR");
        assertThat(response.getBody().getDetail()).doesNotContain("private database details");
    }

    @Test
    void ignoresCustomExceptionMessageInPublicProblemDetails() {
        var response = handler().handleAppException(
                new AppException(ProductErrorCode.PRODUCT_NOT_FOUND, "SELECT private email passwordHash"), request);
        assertThat(response.getBody().getDetail()).isEqualTo("Sản phẩm không tồn tại hoặc đã bị xóa.");
        assertThat(response.getBody().getTitle()).isEqualTo("Không tìm thấy sản phẩm");
    }
}

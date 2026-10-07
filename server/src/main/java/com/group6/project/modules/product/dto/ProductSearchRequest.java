package com.group6.project.modules.product.dto;

import com.group6.project.common.request.PageRequestDto;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.math.BigDecimal;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ProductSearchRequest extends PageRequestDto {
    @Size(max = 100)
    private String search;

    @Size(max = 100)
    private String category;

    @Pattern(regexp = "ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
    private String status;

    @DecimalMin("0.0")
    private BigDecimal minPrice;

    @DecimalMin("0.0")
    private BigDecimal maxPrice;

    @NotBlank
    @Pattern(regexp = "name|price|stock|createdAt|updatedAt", message = "Unsupported sort field")
    private String sortBy = "createdAt";

    @AssertTrue(message = "minPrice must not exceed maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public Pageable toPageable() {
        return PageRequest.of(getPage(), getSize(), Sort.by(getDirection(), sortBy)
                .and(Sort.by(Sort.Direction.ASC, "id")));
    }
}

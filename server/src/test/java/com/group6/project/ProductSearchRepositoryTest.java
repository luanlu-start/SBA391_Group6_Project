package com.group6.project;

import com.group6.project.modules.product.dto.ProductSearchRequest;
import com.group6.project.modules.product.model.Product;
import com.group6.project.modules.product.repository.ProductRepository;
import com.group6.project.modules.product.repository.ProductSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:products;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password="})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductSearchRepositoryTest {
    @Autowired
    private ProductRepository repository;

    @Test
    void combinesFiltersAndPagesWithAccurateTotals() {
        save("MacBook", "Electronics", "ACTIVE", "99.99");
        save("Mac Mini", "electronics", "ACTIVE", "50.00");
        save("Mac Case", "Accessories", "ACTIVE", "20.00");
        save("Mac Old", "Electronics", "INACTIVE", "50.00");
        var request = new ProductSearchRequest();
        request.setSearch(" mac ");
        request.setCategory(" Electronics ");
        request.setStatus("ACTIVE");
        request.setMinPrice(new BigDecimal("50.00"));
        request.setMaxPrice(new BigDecimal("100.00"));
        request.setSize(1);
        request.setPage(1);
        request.setSortBy("price");
        var result = repository.findAll(ProductSpecification.filter(request), request.toPageable());
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).singleElement().satisfies(product -> {
            assertThat(product.getName()).isEqualTo("Mac Mini");
            assertThat(product.getPrice()).isEqualByComparingTo("50.00");
        });
    }

    @Test
    void treatsLikeWildcardsAsLiteralText() {
        save("Sale 10%_\\ offer", "Other", "ACTIVE", "0.10");
        save("Sale 100 offer", "Other", "ACTIVE", "0.20");
        var request = new ProductSearchRequest();
        request.setSearch("10%_\\");
        var result = repository.findAll(ProductSpecification.filter(request), request.toPageable());
        assertThat(result.getContent()).extracting(Product::getName).containsExactly("Sale 10%_\\ offer");
    }

    @Test
    void emptyFiltersReturnAllRowsWithJpaGeneratedUuidAndExactDecimals() {
        Product product = save("First", "Other", "ACTIVE", "0.10");
        var request = new ProductSearchRequest();
        request.setSearch(" ");
        request.setCategory(" ");
        var result = repository.findAll(ProductSpecification.filter(request), request.toPageable());
        assertThat(product.getId()).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(repository.findById(product.getId())).get()
                .extracting(Product::getPrice).isEqualTo(new BigDecimal("0.10"));
    }

    private Product save(String name, String category, String status, String price) {
        Instant now = Instant.now();
        return repository.saveAndFlush(Product.builder().name(name).category(category).status(status)
                .price(new BigDecimal(price)).stock(1).createdAt(now).updatedAt(now).build());
    }
}

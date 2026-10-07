package com.group6.project.modules.product.repository;

import com.group6.project.modules.product.dto.ProductSearchRequest;
import com.group6.project.modules.product.model.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Locale;

public final class ProductSpecification {
    private ProductSpecification() {}

    public static Specification<Product> filter(ProductSearchRequest request) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (StringUtils.hasText(request.getSearch())) {
                String keyword = request.getSearch().trim().toLowerCase(Locale.ROOT);
                // Treat user input literally, including SQL LIKE wildcards and SQL Server brackets.
                String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_").replace("[", "\\[");
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\'));
            }
            if (StringUtils.hasText(request.getCategory())) {
                predicates.add(cb.equal(cb.lower(root.get("category")),
                        request.getCategory().trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(request.getStatus())) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            if (request.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), request.getMinPrice()));
            }
            if (request.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), request.getMaxPrice()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

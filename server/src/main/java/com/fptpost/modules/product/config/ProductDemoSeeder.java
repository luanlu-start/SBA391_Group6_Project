package com.fptpost.modules.product.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.product.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;

@Component
@Profile("demo & !prod")
@RequiredArgsConstructor
@Slf4j
public class ProductDemoSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        List<Product> products;
        try (InputStream input = new ClassPathResource("demo/products.json").getInputStream()) {
            products = objectMapper.readValue(input, new TypeReference<List<Product>>() {});
        }

        int inserted = 0;
        for (Product product : products) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?",
                    Integer.class, product.getId().toString());
            if (count != null && count > 0) {
                continue;
            }
            Instant now = Instant.now();
            product.setCreatedAt(now);
            product.setUpdatedAt(now);
            try {
                // Insert avoids overwriting an existing product if another instance seeded it first.
                jdbcTemplate.update("""
                        INSERT INTO products (id, name, description, price, category, stock, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, product.getId().toString(), product.getName(), product.getDescription(), product.getPrice(),
                        product.getCategory(), product.getStock(), product.getStatus(), Timestamp.from(now), Timestamp.from(now));
                inserted++;
            } catch (DuplicateKeyException ex) {
                log.debug("Demo product {} already exists", product.getId());
            }
        }
        log.info("Inserted {} missing demo products", inserted);
    }
}

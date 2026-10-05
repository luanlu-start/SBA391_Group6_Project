package com.group6.project.modules.product.repository;

import com.group6.project.modules.product.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByCategory(String category);

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByStatus(String status);
}

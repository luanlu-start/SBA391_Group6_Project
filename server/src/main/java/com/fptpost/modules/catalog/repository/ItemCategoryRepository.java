package com.fptpost.modules.catalog.repository;

import com.fptpost.modules.catalog.model.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {

    List<ItemCategory> findByActiveTrueOrderByNameAsc();

    List<ItemCategory> findAllByOrderByCreatedAtDesc();

    Optional<ItemCategory> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}

package com.fptpost.modules.catalog.repository;

import com.fptpost.modules.catalog.model.ParameterHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParameterHistoryRepository extends JpaRepository<ParameterHistory, Long> {
    Page<ParameterHistory> findByParameterKey(String key, Pageable pageable);
}

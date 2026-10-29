package com.fptpost.modules.catalog.repository;

import com.fptpost.modules.catalog.model.SystemParameter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SystemParameterRepository extends JpaRepository<SystemParameter, Long> {
    Optional<SystemParameter> findByKey(String key);
    List<SystemParameter> findAllByOrderByPrmIdAsc();

    // A single admin transaction owns the catalog while validating relationships across keys.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select parameter from SystemParameter parameter order by parameter.key asc")
    List<SystemParameter> lockCatalog();
}

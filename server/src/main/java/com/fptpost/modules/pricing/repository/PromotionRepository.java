package com.fptpost.modules.pricing.repository;

import com.fptpost.modules.pricing.model.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
    boolean existsByCode(String code);
    Optional<Promotion> findByCode(String code);

    /** All UC-44 usage/budget writers must acquire this lock before validation and insert, in the same SQL transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") UUID id);
}

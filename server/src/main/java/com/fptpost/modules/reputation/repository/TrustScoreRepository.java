package com.fptpost.modules.reputation.repository;

import com.fptpost.modules.reputation.model.TrustScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface TrustScoreRepository extends JpaRepository<TrustScore, Long> {

    @Query(value = "select t from TrustScore t where (:below is null or t.score < :below) order by t.score asc, t.courierId asc",
            countQuery = "select count(t) from TrustScore t where (:below is null or t.score < :below)")
    Page<TrustScore> ranked(@Param("below") BigDecimal below, Pageable pageable);
}

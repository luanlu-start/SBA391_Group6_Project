package com.fptpost.modules.reputation.repository;

import com.fptpost.modules.reputation.model.TrustScoreHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrustScoreHistoryRepository extends JpaRepository<TrustScoreHistory, Long> {

    List<TrustScoreHistory> findTop20ByCourierIdOrderByCalculatedAtDescIdDesc(Long courierId);
}

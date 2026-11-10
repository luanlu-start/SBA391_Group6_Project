package com.fptpost.modules.moderation.repository;

import com.fptpost.modules.moderation.model.ModerationKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationKeywordRepository extends JpaRepository<ModerationKeyword, Long> {

    boolean existsByTerm(String term);

    List<ModerationKeyword> findAllByOrderByLevelAscTermAsc();
}

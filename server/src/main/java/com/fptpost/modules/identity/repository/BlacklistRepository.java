package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.BlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistRepository extends JpaRepository<BlacklistEntry, Long> {
    boolean existsByKindAndValueHash(String kind, String valueHash);
}

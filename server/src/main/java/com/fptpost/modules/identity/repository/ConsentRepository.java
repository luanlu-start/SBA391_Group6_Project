package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {}

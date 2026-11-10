package com.fptpost.modules.moderation.repository;

import com.fptpost.modules.moderation.model.ViolationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViolationEvidenceRepository extends JpaRepository<ViolationEvidence, Long> {

    List<ViolationEvidence> findByReportIdIn(Collection<Long> reportIds);

    Optional<ViolationEvidence> findByPublicId(UUID publicId);
}

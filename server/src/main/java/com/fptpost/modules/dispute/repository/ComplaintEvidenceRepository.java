package com.fptpost.modules.dispute.repository;

import com.fptpost.modules.dispute.model.ComplaintEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplaintEvidenceRepository extends JpaRepository<ComplaintEvidence, Long> {

    List<ComplaintEvidence> findByComplaintIdOrderByIdAsc(Long complaintId);

    Optional<ComplaintEvidence> findByPublicIdAndComplaintId(UUID publicId, Long complaintId);
}

package com.fptpost.modules.dispute.repository;

import com.fptpost.modules.dispute.model.ComplaintMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintMessageRepository extends JpaRepository<ComplaintMessage, Long> {

    List<ComplaintMessage> findByComplaintIdOrderByCreatedAtAscIdAsc(Long complaintId);
}

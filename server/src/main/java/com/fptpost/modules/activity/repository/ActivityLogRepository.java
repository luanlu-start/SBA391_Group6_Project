package com.fptpost.modules.activity.repository;

import com.fptpost.modules.activity.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {
    Page<ActivityLog> findByEntityId(String entityId, Pageable pageable);
}

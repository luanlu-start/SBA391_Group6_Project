package com.fptpost.modules.activity.service;

import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.activity.dto.ActivityLogResponse;
import com.fptpost.modules.activity.dto.ActivityLogSearchRequest;
import com.fptpost.modules.activity.event.ProductActivityEvent;
import com.fptpost.modules.activity.mapper.ActivityLogMapper;
import com.fptpost.modules.activity.model.ActivityLog;
import com.fptpost.modules.activity.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {
    private final ActivityLogRepository repository;
    private final ActivityLogMapper mapper;

    public PageResponse<ActivityLogResponse> search(ActivityLogSearchRequest request) {
        var page = request.getEntityId() == null
                ? repository.findAll(request.toPageable())
                : repository.findByEntityId(request.getEntityId().toString(), request.toPageable());
        return PageResponse.from(page.map(mapper::toResponse));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordProductActivity(ProductActivityEvent event) {
        try {
            repository.save(ActivityLog.builder().entityType("PRODUCT").entityId(event.productId().toString())
                    .action(event.action()).entityName(event.productName()).occurredAt(event.occurredAt()).build());
        } catch (DataAccessException ex) {
            // SQL is already committed. Logging failure must not misreport a successful product write.
            log.error("Could not record {} activity for product {} after SQL commit",
                    event.action(), event.productId(), ex);
        }
    }
}

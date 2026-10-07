package com.group6.project;

import com.group6.project.modules.activity.dto.ActivityLogSearchRequest;
import com.group6.project.modules.activity.event.ProductActivityEvent;
import com.group6.project.modules.activity.mapper.ActivityLogMapper;
import com.group6.project.modules.activity.model.ActivityLog;
import com.group6.project.modules.activity.repository.ActivityLogRepository;
import com.group6.project.modules.activity.service.ActivityLogService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActivityLogServiceTest {
    private final ActivityLogRepository repository = mock(ActivityLogRepository.class);
    private final ActivityLogService service = new ActivityLogService(repository, Mappers.getMapper(ActivityLogMapper.class));

    @Test
    void recordsProductHistoryWithCanonicalUuid() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        service.recordProductActivity(new ProductActivityEvent("CREATED", id, "Product", now));
        verify(repository).save(argThat(log -> log.getEntityId().equals(id.toString())
                && log.getEntityType().equals("PRODUCT") && log.getAction().equals("CREATED")
                && log.getOccurredAt().equals(now)));
    }

    @Test
    void logFailureAfterSqlCommitDoesNotMisreportProductWrite() {
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("MongoDB unavailable"));
        assertThatCode(() -> service.recordProductActivity(
                new ProductActivityEvent("UPDATED", UUID.randomUUID(), "Product", Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void filtersHistoryAndMapsPageWithoutExposingDocuments() {
        var request = new ActivityLogSearchRequest();
        request.setEntityId(UUID.randomUUID());
        var log = ActivityLog.builder().id("log-id").entityId(request.getEntityId().toString()).action("DELETED").build();
        when(repository.findByEntityId(request.getEntityId().toString(), request.toPageable()))
                .thenReturn(new PageImpl<>(List.of(log), request.toPageable(), 1));
        var response = service.search(request);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).singleElement().satisfies(item -> assertThat(item.action()).isEqualTo("DELETED"));
    }

    @Test
    void historyReadFailurePropagatesToTheGlobalHandler() {
        var request = new ActivityLogSearchRequest();
        var failure = new DataAccessResourceFailureException("MongoDB unavailable");
        when(repository.findAll(request.toPageable())).thenThrow(failure);
        assertThatThrownBy(() -> service.search(request)).isSameAs(failure);
    }
}

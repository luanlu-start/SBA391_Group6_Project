package com.fptpost.modules.audit;

import com.fptpost.common.audit.MongoAuditDocument;
import com.fptpost.modules.audit.dto.request.AuditLogSearchRequest;
import com.fptpost.modules.audit.repository.AuditLogQueryRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuditLogQueryRepositoryTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    AuditLogQueryRepository repository = new AuditLogQueryRepository(mongo);

    @BeforeEach void setup() {
        when(mongo.count(any(Query.class), eq(MongoAuditDocument.class))).thenReturn(45L);
        when(mongo.find(any(Query.class), eq(MongoAuditDocument.class))).thenReturn(List.of(document()));
    }

    @Test void combinesExactLiteralFiltersAndTimeAtDatabaseWithStablePagedSort() {
        var request = new AuditLogSearchRequest();
        UUID actor = UUID.fromString("95005683-002b-4375-b340-10730532859b");
        request.setActor(actor); request.setAction(".*"); request.setEntityType(" system_parameters ");
        request.setEntityId("{\"$ne\":null}"); request.setFrom(Instant.parse("2026-10-14T00:00:00Z"));
        request.setTo(Instant.parse("2026-10-15T00:00:00Z")); request.setPage(1); request.setSize(20);
        var result = repository.find(request);
        ArgumentCaptor<Query> count = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Query> find = ArgumentCaptor.forClass(Query.class);
        verify(mongo).count(count.capture(), eq(MongoAuditDocument.class));
        verify(mongo).find(find.capture(), eq(MongoAuditDocument.class));
        assertThat(count.getValue().getSkip()).isZero(); assertThat(count.getValue().getLimit()).isZero();
        List<Document> filters = count.getValue().getQueryObject().getList("$and", Document.class);
        assertThat(filters).containsExactly(new Document("actor", actor.toString()), new Document("action", ".*"),
                new Document("entityType", "system_parameters"), new Document("entityId", "{\"$ne\":null}"),
                new Document("at", new Document("$gte", request.getFrom()).append("$lte", request.getTo())));
        assertThat(find.getValue().getSortObject()).isEqualTo(new Document("at", -1).append("eventId", -1));
        assertThat(find.getValue().getSkip()).isEqualTo(20); assertThat(find.getValue().getLimit()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(45); assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(1); assertThat(result.getContent()).hasSize(1);
        verifyNoMoreInteractions(mongo);
    }

    @Test void blankFiltersAreIgnoredAndDefaultsApplyNoFullCollectionLoading() {
        var request = new AuditLogSearchRequest(); request.setAction(" "); request.setEntityId("");
        repository.find(request);
        ArgumentCaptor<Query> find = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(find.capture(), eq(MongoAuditDocument.class));
        assertThat(find.getValue().getQueryObject()).isEmpty();
        assertThat(find.getValue().getLimit()).isEqualTo(20); assertThat(find.getValue().getSkip()).isZero();
        verify(mongo).count(any(Query.class), eq(MongoAuditDocument.class)); verifyNoMoreInteractions(mongo);
    }

    @Test void inclusiveInstantBoundsRoundLowerUpAndUpperDownToStoredMillisecondPrecision() {
        var request = new AuditLogSearchRequest();
        request.setFrom(Instant.parse("2026-10-14T00:00:00.123000001Z"));
        request.setTo(Instant.parse("2026-10-14T00:00:00.125999999Z"));
        repository.find(request);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).count(query.capture(), eq(MongoAuditDocument.class));
        Document at = query.getValue().getQueryObject().getList("$and", Document.class).getFirst().get("at", Document.class);
        assertThat(at.get("$gte")).isEqualTo(Instant.parse("2026-10-14T00:00:00.124Z"));
        assertThat(at.get("$lte")).isEqualTo(Instant.parse("2026-10-14T00:00:00.125Z"));
    }

    @Test void lowerBoundBeforeEpochAlsoRoundsTowardsTheFuture() {
        var request = new AuditLogSearchRequest(); request.setFrom(Instant.parse("1969-12-31T23:59:59.999000001Z"));
        repository.find(request);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).count(query.capture(), eq(MongoAuditDocument.class));
        Document at = query.getValue().getQueryObject().getList("$and", Document.class).getFirst().get("at", Document.class);
        assertThat(at.get("$gte")).isEqualTo(Instant.EPOCH);
    }

    private static MongoAuditDocument document() {
        return new MongoAuditDocument("event-1", "event-1", "USER", UUID.randomUUID().toString(), "PARAMETER_UPDATED",
                "system_parameters", "BASE_FARE", Map.of("value", "10000"), Map.of("value", "12000"),
                "127.0.0.1", "query-test", Instant.parse("2026-10-14T00:00:00Z"));
    }
}

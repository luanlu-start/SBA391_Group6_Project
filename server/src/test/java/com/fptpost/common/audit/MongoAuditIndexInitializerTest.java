package com.fptpost.common.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class MongoAuditIndexInitializerTest {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final IndexOperations indexOperations = mock(IndexOperations.class);
    private final AuditIndexProperties properties = new AuditIndexProperties();
    private final MongoAuditIndexInitializer initializer = new MongoAuditIndexInitializer(mongoTemplate, properties);

    @Test
    void createsStableQueryIndexesWithActualActorFieldAndNoTimeToLive() {
        when(mongoTemplate.indexOps(MongoAuditDocument.class)).thenReturn(indexOperations);
        initializer.ensureIndexes();
        var indexes = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, times(7)).createIndex(indexes.capture());
        List<IndexDefinition> definitions = indexes.getAllValues();
        assertThat(definitions.getFirst().getIndexKeys()).containsOnlyKeys("eventId").containsEntry("eventId", 1);
        assertThat(definitions.getFirst().getIndexOptions()).containsEntry("unique", true);
        assertThat(definitions.subList(1, 7)).extracting(index -> List.copyOf(index.getIndexKeys().keySet()))
                .containsExactly(List.of("at", "eventId"), List.of("actor", "at", "eventId"),
                        List.of("action", "at", "eventId"), List.of("entityType", "entityId", "at", "eventId"),
                        List.of("entityType", "at", "eventId"), List.of("entityId", "at", "eventId"));
        assertThat(definitions.subList(1, 7)).allSatisfy(index -> {
            assertThat(index.getIndexKeys()).containsEntry("at", -1).containsEntry("eventId", -1);
            assertThat(index.getIndexKeys()).doesNotContainKey("actorPublicId");
        });
        assertThat(definitions).allSatisfy(index -> assertThat(index.getIndexOptions()).doesNotContainKey("expireAfterSeconds"));
    }

    @Test
    void outageDoesNotEscapeIntoStartupAndLaterScheduledRunRetries(CapturedOutput output) {
        when(mongoTemplate.indexOps(MongoAuditDocument.class))
                .thenThrow(new DataAccessResourceFailureException("mongodb://secret-user:secret-password@example"))
                .thenReturn(indexOperations);
        assertThatCode(initializer::scheduledEnsureIndexes).doesNotThrowAnyException();
        assertThat(output).contains("Audit index initialization deferred (DataAccessResourceFailureException)")
                .doesNotContain("secret-user", "secret-password");
        assertThatCode(initializer::scheduledEnsureIndexes).doesNotThrowAnyException();
        verify(indexOperations, times(7)).createIndex(any(IndexDefinition.class));
    }

    @Test
    void keepsAnEquivalentLegacyUniqueIndexInsteadOfFailingOnItsDifferentName() {
        when(mongoTemplate.indexOps(MongoAuditDocument.class)).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(List.of(new IndexInfo(
                List.of(IndexField.create("eventId", Sort.Direction.ASC)), "eventId_1", true, false, null)));
        initializer.ensureIndexes();
        var indexes = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, times(6)).createIndex(indexes.capture());
        assertThat(indexes.getAllValues()).noneMatch(index -> Boolean.TRUE.equals(index.getIndexOptions().get("unique")));
        verify(indexOperations, never()).dropIndex(any());
    }

    @Test
    void disabledWorkerDoesNotConnectToMongo() {
        properties.setEnabled(false);
        initializer.scheduledEnsureIndexes();
        verifyNoInteractions(mongoTemplate, indexOperations);
    }
}

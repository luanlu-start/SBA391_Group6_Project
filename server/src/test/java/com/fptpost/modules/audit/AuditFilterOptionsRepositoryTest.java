package com.fptpost.modules.audit;

import com.fptpost.common.audit.MongoAuditDocument;
import com.fptpost.modules.audit.repository.AuditFilterOptionsRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditFilterOptionsRepositoryTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    AuditFilterOptionsRepository repository = new AuditFilterOptionsRepository(mongo);

    @Test void distinctValuesAreGroupedSortedAndBoundedAtMongoWithLiteralSearchAndEntityContext() {
        when(mongo.aggregate(any(Aggregation.class), eq(MongoAuditDocument.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(new Document("_id", "BASE_FARE")), new Document()));
        assertThat(repository.distinct("entityId", ".*[x]", "system_parameters", 31)).containsExactly("BASE_FARE");
        var aggregation = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(aggregation.capture(), eq(MongoAuditDocument.class), eq(Document.class));
        List<Document> pipeline = aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT);
        List<Document> filters = pipeline.getFirst().get("$match", Document.class).getList("$and", Document.class);
        assertThat(filters.get(0)).isEqualTo(new Document("entityId", new Document("$type", 2).append("$ne", "")));
        Pattern validValue = filters.get(1).get("entityId", Pattern.class);
        assertThat(validValue.matcher("BASE_FARE").matches()).isTrue();
        assertThat(validValue.matcher(" ").matches()).isFalse();
        assertThat(validValue.matcher("X".repeat(201)).matches()).isFalse();
        // Criteria retains Pattern directly; the BSON codec renders it as $regularExpression on the wire.
        Pattern search = filters.get(2).get("entityId", Pattern.class);
        assertThat(search.pattern()).isEqualTo(Pattern.quote(".*[x]"));
        assertThat(search.matcher("BASE_FARE").find()).isFalse();
        assertThat(search.matcher("prefix.*[x]suffix").find()).isTrue();
        assertThat(filters.get(3)).isEqualTo(new Document("entityType", "system_parameters"));
        assertThat(pipeline.get(1)).isEqualTo(new Document("$group", new Document("_id", "$entityId")));
        assertThat(pipeline.get(2)).isEqualTo(new Document("$sort", new Document("_id", 1)));
        assertThat(pipeline.get(3)).isEqualTo(new Document("$limit", 31L));
        verifyNoMoreInteractions(mongo);
    }

    @Test void emptySearchDoesNotLoadDocumentsAndHistoricalActorUsesExactMatch() {
        when(mongo.aggregate(any(Aggregation.class), eq(MongoAuditDocument.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));
        repository.distinct("action", "", "ignored", 2);
        var aggregation = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(aggregation.capture(), eq(MongoAuditDocument.class), eq(Document.class));
        assertThat(aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).getFirst().toJson())
                .doesNotContain("entityType", "\\Q");
        when(mongo.exists(any(Query.class), eq(MongoAuditDocument.class))).thenReturn(true);
        assertThat(repository.containsActor("historical-uuid")).isTrue();
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(query.capture(), eq(MongoAuditDocument.class));
        assertThat(query.getValue().getQueryObject()).isEqualTo(new Document("actor", "historical-uuid"));
        verifyNoMoreInteractions(mongo);
    }

    @Test void arbitraryFieldNamesAndUnboundedReadsAreRejectedBeforeMongo() {
        assertThatThrownBy(() -> repository.distinct("$where", "", null, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.distinct("action", "", null, 1000)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mongo);
    }
}

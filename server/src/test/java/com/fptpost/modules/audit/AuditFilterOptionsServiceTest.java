package com.fptpost.modules.audit;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.audit.exception.AuditErrorCode;
import com.fptpost.modules.audit.repository.AuditFilterOptionsRepository;
import com.fptpost.modules.audit.service.AuditFilterOptionsService;
import com.fptpost.modules.identity.dto.response.UserDirectoryEntry;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditFilterOptionsServiceTest {
    AuditFilterOptionsRepository repository = mock(AuditFilterOptionsRepository.class);
    UserDirectoryLookup users = mock(UserDirectoryLookup.class);
    AuditFilterOptionsService service = new AuditFilterOptionsService(repository, users);

    @Test void boundedOptionsUseOneExtraResultToIndicateMoreAndPreserveStoreOrdering() {
        when(repository.distinct("entityId", "BASE", "system_parameters", 3))
                .thenReturn(List.of("BASE_FARE", "BASE_FARE_2", "BASE_FARE_3"));
        var result = service.options("entityId", " BASE ", " system_parameters ", "2");
        assertThat(result.options()).extracting(option -> option.value()).containsExactly("BASE_FARE", "BASE_FARE_2");
        assertThat(result.hasMore()).isTrue();
        verifyNoInteractions(users);
    }

    @Test void actorLabelsAreNamesAndEmailWithUuidValueAndEmailFallbackForMissingProfile() {
        UUID admin = UUID.randomUUID(); UUID legacy = UUID.randomUUID();
        when(users.search("Thuận", 31)).thenReturn(List.of(new UserDirectoryEntry(admin, "Trần Công Thuận", "thuan@example.invalid"),
                new UserDirectoryEntry(legacy, null, "legacy@example.invalid")));
        var result = service.options("actor", "Thuận", null, "30");
        assertThat(result.hasMore()).isFalse();
        assertThat(result.options().getFirst().value()).isEqualTo(admin.toString());
        assertThat(result.options().getFirst().label()).isEqualTo("Trần Công Thuận");
        assertThat(result.options().getFirst().description()).isEqualTo("thuan@example.invalid");
        assertThat(result.options().get(1).label()).isEqualTo("legacy@example.invalid");
        verifyNoInteractions(repository);
    }

    @Test void historicalUuidIsSelectableEvenAfterIdentityWasRemovedButArbitraryUuidIsNotInvented() {
        String id = UUID.randomUUID().toString();
        when(users.search(id.toUpperCase(), 31)).thenReturn(List.of());
        when(repository.containsActor(id)).thenReturn(true);
        var result = service.options("actor", id.toUpperCase(), null, "30");
        assertThat(result.options()).hasSize(1);
        assertThat(result.options().getFirst().value()).isEqualTo(id);
        assertThat(result.options().getFirst().label()).startsWith("Người dùng ");
        when(repository.containsActor(id)).thenReturn(false);
        assertThat(service.options("actor", id.toUpperCase(), null, "30").options()).isEmpty();
    }

    @Test void invalidOptionFieldsLengthsAndLimitsFailBeforeEitherStore() {
        for (String field : List.of("$where", "Actor", "", "before.password")) invalid(field, null, null, "30");
        invalid(null, null, null, "30"); invalid("action", "x".repeat(101), null, "30");
        invalid("entityId", null, "x".repeat(101), "30");
        for (String limit : List.of("0", "51", "-1", "1.5", "not-a-number", "2147483648")) {
            invalid("action", null, null, limit);
        }
        invalid("action", null, null, null);
        verifyNoInteractions(repository, users);
    }

    @Test void eitherStoreFailureReturnsGenericUnavailableWithoutPrivateDetailsOrFallback() {
        when(repository.distinct("action", "", null, 31)).thenThrow(new MongoException("mongodb://private/secret"));
        unavailable(() -> service.options("action", null, null, "30"));
        when(users.search("admin", 31)).thenThrow(new DataAccessResourceFailureException("sql-password=secret"));
        unavailable(() -> service.options("actor", "admin", null, "30"));
    }

    private void invalid(String field, String query, String entityType, String limit) {
        assertThatThrownBy(() -> service.options(field, query, entityType, limit)).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_INVALID_FILTER));
    }

    private void unavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuditErrorCode.AUDIT_STORE_UNAVAILABLE))
                .hasMessage("AUDIT_STORE_UNAVAILABLE");
    }
}

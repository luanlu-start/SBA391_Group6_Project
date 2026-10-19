package com.fptpost.modules.identity;

import com.fptpost.modules.identity.repository.UserDirectoryRepository;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserDirectoryLookupTest {
    UserDirectoryRepository repository = mock(UserDirectoryRepository.class);
    UserDirectoryLookup lookup = new UserDirectoryLookup(repository);

    @Test void searchEscapesSqlWildcardsAndAppliesBoundWithoutLoadingUserEntities() {
        when(repository.findOptions(anyString(), any())).thenReturn(List.of());
        lookup.search("  A!%_[d]  ", 31);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findOptions(eq("%a!!!%!_![d]%"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(31);
        verifyNoMoreInteractions(repository);
    }

    @Test void emptyQueryAndBoundsAreExplicit() {
        lookup.search(" ", 1);
        verify(repository).findOptions(isNull(), any());
        assertThatThrownBy(() -> lookup.search("a".repeat(101), 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lookup.search(null, 52)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lookup.search(null, 0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(repository);
    }

    @Test void hydrationAcceptsImmutableListsAndRejectsInvalidCollections() {
        UUID publicId = UUID.randomUUID();
        lookup.lookup(List.of(publicId));
        verify(repository).findEntriesByPublicIds(List.of(publicId));
        assertThat(lookup.lookup(List.of())).isEmpty();
        assertThatThrownBy(() -> lookup.lookup(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lookup.lookup(Arrays.asList((UUID) null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lookup.lookup(java.util.Collections.nCopies(1001, publicId)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(repository);
    }
}

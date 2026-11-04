package com.fptpost.modules.identity.service;

import com.fptpost.modules.identity.dto.response.UserDirectoryEntry;
import com.fptpost.modules.identity.repository.UserDirectoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Collection;
import java.util.UUID;
import java.util.Objects;

/** Read-only cross-module directory access for administrative selectors, including historical/locked accounts. */
@Service
@RequiredArgsConstructor
public class UserDirectoryLookup {
    private final UserDirectoryRepository repository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDirectoryEntry> search(String query, int limit) {
        if (limit < 1 || limit > 51 || query != null && query.length() > 100) {
            throw new IllegalArgumentException("Invalid directory search bounds");
        }
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String pattern = text.isEmpty() ? null : "%" + text.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_").replace("[", "![") + "%";
        return repository.findOptions(pattern, PageRequest.of(0, limit));
    }

    /** Bounded public projection lookup; callers batch larger pages below SQL Server's parameter ceiling. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDirectoryEntry> lookup(Collection<UUID> publicIds) {
        if (publicIds == null || publicIds.size() > 1000 || publicIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid directory lookup bounds");
        }
        return publicIds.isEmpty() ? List.of() : repository.findEntriesByPublicIds(publicIds);
    }
}

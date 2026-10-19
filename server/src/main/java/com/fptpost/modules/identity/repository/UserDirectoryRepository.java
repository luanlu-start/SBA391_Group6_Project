package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.dto.response.UserDirectoryEntry;
import com.fptpost.modules.identity.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface UserDirectoryRepository extends Repository<User, Long> {
    @Query("select new com.fptpost.modules.identity.dto.response.UserDirectoryEntry(u.publicId, p.fullName, u.email) "
            + "from User u left join UserProfile p on p.userId = u.id "
            + "where :pattern is null or lower(coalesce(p.fullName, '')) like :pattern escape '!' "
            + "or lower(u.email) like :pattern escape '!' or lower(cast(u.publicId as string)) like :pattern escape '!' "
            + "order by lower(coalesce(p.fullName, u.email)), lower(u.email), u.id")
    List<UserDirectoryEntry> findOptions(@Param("pattern") String pattern, Pageable pageable);

    @Query("select new com.fptpost.modules.identity.dto.response.UserDirectoryEntry(u.publicId, p.fullName, u.email) "
            + "from User u left join UserProfile p on p.userId = u.id where u.publicId in :ids")
    List<UserDirectoryEntry> findEntriesByPublicIds(@Param("ids") Collection<UUID> ids);
}

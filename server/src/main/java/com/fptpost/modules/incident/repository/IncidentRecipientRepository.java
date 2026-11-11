package com.fptpost.modules.incident.repository;

import com.fptpost.modules.identity.model.User;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

/** UC29-only projection; does not expose identity or credentials. */
public interface IncidentRecipientRepository extends Repository<User, Long> {
    @Query("select distinct u.publicId from User u join u.roles r where r.code = 'MODERATOR' "
            + "and u.deleted = false and u.status = com.fptpost.modules.identity.enums.UserStatus.ACTIVE")
    List<UUID> findActiveModerators();
}

package com.fptpost.modules.jobs.repository;

import com.fptpost.modules.identity.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

public interface JobAlertRecipientRepository extends Repository<User, Long> {

    @Query("select u.publicId from User u join u.roles r where r.code = 'ADMIN' and u.deleted = false "
            + "and u.status = com.fptpost.modules.identity.enums.UserStatus.ACTIVE")
    List<UUID> findActiveAdministrators();
}

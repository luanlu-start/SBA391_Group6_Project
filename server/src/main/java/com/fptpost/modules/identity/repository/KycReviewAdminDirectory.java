package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository @RequiredArgsConstructor
public class KycReviewAdminDirectory {
    private final EntityManager entities;

    public List<UUID> activeAdministrators() {
        var recipients = entities.createQuery("select u.publicId from User u join u.roles r where r.code = 'ADMIN'"
                        + " and u.status = :active and u.deleted = false and u.emailVerifiedAt is not null order by u.id", UUID.class)
                .setParameter("active", UserStatus.ACTIVE).setMaxResults(101).getResultList();
        if (recipients.size() > 100) throw new IllegalStateException("Administrator notification batch exceeds its limit");
        return recipients;
    }

    public void lockApplicantAndReviewer(UUID applicant, UUID reviewer) {
        lockUsers(Arrays.asList(applicant, reviewer));
    }

    /** Acquire all participant locks before any KYC lock, including the reviewer foreign key. */
    public List<UUID> lockApplicantAndAdministrators(UUID applicant) {
        return lockApplicantAndAdministrators(applicant, null);
    }

    public List<UUID> lockApplicantAndAdministrators(UUID applicant, UUID reviewer) {
        List<UUID> recipients = activeAdministrators();
        var ids = new HashSet<>(recipients); ids.add(applicant);
        if (reviewer != null) ids.add(reviewer);
        lockUsers(ids);
        return recipients;
    }

    private void lockUsers(Collection<UUID> participants) {
        var ids = new HashSet<>(participants);
        ids.remove(null);
        if (ids.isEmpty()) throw new IllegalArgumentException("KYC participants are required");
        var users = entities.createQuery("select u from User u where u.publicId in :ids order by u.id", User.class)
                .setParameter("ids", ids).getResultList();
        users.stream().sorted(Comparator.comparing(User::getId))
                .forEach(user -> entities.refresh(user, LockModeType.PESSIMISTIC_WRITE));
    }
}

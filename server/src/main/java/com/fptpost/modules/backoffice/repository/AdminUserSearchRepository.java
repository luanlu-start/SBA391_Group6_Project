package com.fptpost.modules.backoffice.repository;

import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminUserSearchRepository extends Repository<User, Long> {

    String FILTER = """
            from User u left join UserProfile p on p.userId = u.id
            where (:pattern is null or lower(u.email) like :pattern escape '!' or u.phone like :pattern escape '!'
                   or lower(coalesce(p.fullName, '')) like :pattern escape '!'
                   or lower(cast(u.publicId as string)) like :pattern escape '!')
              and (:status is null or u.status = :status)
              and (:role is null or exists (select 1 from User x join x.roles r where x.id = u.id and r.code = :role))
            """;

    @Query(value = "select u " + FILTER + " order by u.createdAt desc, u.id desc", countQuery = "select count(u) " + FILTER)
    Page<User> search(@Param("pattern") String pattern, @Param("status") UserStatus status, @Param("role") String role,
                      Pageable pageable);

    @Query("select count(distinct u) from User u join u.roles r where r.code = :role and u.deleted = false and u.status <> :excluded")
    long countActiveWithRole(@Param("role") String role, @Param("excluded") UserStatus excluded);

    @Query(value = """
            select u from User u where exists (select 1 from User x join x.roles r where x.id = u.id and r.code in ('ADMIN', 'MODERATOR'))
            order by u.createdAt desc, u.id desc
            """, countQuery = """
            select count(u) from User u where exists (select 1 from User x join x.roles r where x.id = u.id and r.code in ('ADMIN', 'MODERATOR'))
            """)
    Page<User> searchStaff(Pageable pageable);
}

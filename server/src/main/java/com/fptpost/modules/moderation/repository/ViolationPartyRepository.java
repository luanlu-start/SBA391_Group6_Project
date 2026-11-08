package com.fptpost.modules.moderation.repository;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.moderation.dto.response.ViolationParty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Projection đọc người dùng cho hồ sơ vi phạm; không trả trường xác thực hay hồ sơ riêng tư. */
public interface ViolationPartyRepository extends Repository<User, Long> {

    @Query("select new com.fptpost.modules.moderation.dto.response.ViolationParty(u.id, u.publicId, p.fullName, u.email) "
            + "from User u left join UserProfile p on p.userId = u.id where u.id in :ids")
    List<ViolationParty> findParties(@Param("ids") Collection<Long> ids);

    @Query("select u.publicId from User u join u.roles r where r.code = 'ADMIN' and u.deleted = false "
            + "and u.status = com.fptpost.modules.identity.enums.UserStatus.ACTIVE")
    List<UUID> findActiveAdministrators();
}

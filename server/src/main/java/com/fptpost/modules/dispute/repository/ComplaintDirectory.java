package com.fptpost.modules.dispute.repository;

import com.fptpost.modules.dispute.dto.response.ComplaintPerson;
import com.fptpost.modules.identity.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Đọc thông tin hiển thị của các bên và danh sách nhân sự nhận cảnh báo; không trả trường xác thực. */
public interface ComplaintDirectory extends Repository<User, Long> {

    @Query("select new com.fptpost.modules.dispute.dto.response.ComplaintPerson(u.id, u.publicId, p.fullName, u.email) "
            + "from User u left join UserProfile p on p.userId = u.id where u.id in :ids")
    List<ComplaintPerson> people(@Param("ids") Collection<Long> ids);

    @Query("select u.publicId from User u where u.id = :id and u.deleted = false "
            + "and u.status <> com.fptpost.modules.identity.enums.UserStatus.DELETED")
    Optional<UUID> reachable(@Param("id") Long id);

    @Query("select u.publicId from User u join u.roles r where r.code = :role and u.deleted = false "
            + "and u.status = com.fptpost.modules.identity.enums.UserStatus.ACTIVE")
    List<UUID> activeStaff(@Param("role") String role);

    @Query("select count(r) > 0 from User u join u.roles r where u.id = :userId and r.code = :role")
    boolean hasRole(@Param("userId") Long userId, @Param("role") String role);
}

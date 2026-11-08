package com.fptpost.modules.jobs.repository;

import com.fptpost.modules.jobs.model.ScheduledJobLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ScheduledJobLockRepository extends JpaRepository<ScheduledJobLock, String> {

    /** Cập nhật có điều kiện là thao tác nguyên tử trên database nên hai instance không cùng giữ khóa. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ScheduledJobLock l set l.lockedUntil = :until, l.lockedBy = :owner, l.lastStartedAt = :now
            where l.jobName = :name and (l.lockedUntil is null or l.lockedUntil < :now)
            """)
    int acquire(@Param("name") String name, @Param("owner") String owner, @Param("now") Instant now, @Param("until") Instant until);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ScheduledJobLock l set l.lockedUntil = null, l.lastFinishedAt = :now
            where l.jobName = :name and l.lockedBy = :owner
            """)
    int release(@Param("name") String name, @Param("owner") String owner, @Param("now") Instant now);
}

package com.fptpost.modules.jobs.repository;

import com.fptpost.modules.jobs.model.ScheduledJobRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ScheduledJobRunRepository extends JpaRepository<ScheduledJobRun, Long> {

    Page<ScheduledJobRun> findByJobNameOrderByStartedAtDescIdDesc(String jobName, Pageable pageable);

    List<ScheduledJobRun> findTop10ByJobNameOrderByStartedAtDescIdDesc(String jobName);

    @Modifying
    @Query("delete from ScheduledJobRun r where r.startedAt < :cutoff")
    int deleteStartedBefore(@Param("cutoff") Instant cutoff);
}

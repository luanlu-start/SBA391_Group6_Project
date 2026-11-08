package com.fptpost.modules.relay.repository;

import com.fptpost.modules.relay.model.MeetingPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingPointRepository extends JpaRepository<MeetingPoint, Long> {

    List<MeetingPoint> findByIsActiveTrue();

    List<MeetingPoint> findByCampusAreaAndIsActiveTrue(String campusArea);
}

package com.group6.project.modules.activity.mapper;

import com.group6.project.modules.activity.dto.ActivityLogResponse;
import com.group6.project.modules.activity.model.ActivityLog;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActivityLogMapper {
    ActivityLogResponse toResponse(ActivityLog log);
}

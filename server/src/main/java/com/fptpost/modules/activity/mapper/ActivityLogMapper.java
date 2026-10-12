package com.fptpost.modules.activity.mapper;

import com.fptpost.modules.activity.dto.ActivityLogResponse;
import com.fptpost.modules.activity.model.ActivityLog;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActivityLogMapper {
    ActivityLogResponse toResponse(ActivityLog log);
}

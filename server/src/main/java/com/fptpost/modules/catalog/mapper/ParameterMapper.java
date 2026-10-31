package com.fptpost.modules.catalog.mapper;

import com.fptpost.modules.catalog.dto.response.ParameterHistoryResponse;
import com.fptpost.modules.catalog.dto.response.ParameterResponse;
import com.fptpost.modules.catalog.model.ParameterHistory;
import com.fptpost.modules.catalog.model.SystemParameter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ParameterMapper {
    ParameterResponse toResponse(SystemParameter parameter);
    @Mapping(target = "id", source = "publicId")
    ParameterHistoryResponse toResponse(ParameterHistory history);
}

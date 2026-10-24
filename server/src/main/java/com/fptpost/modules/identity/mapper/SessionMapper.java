package com.fptpost.modules.identity.mapper;

import com.fptpost.modules.identity.dto.response.SessionResponse;
import com.fptpost.modules.identity.model.AuthSession;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SessionMapper {
    @Mapping(target = "id", source = "session.publicId")
    @Mapping(target = "current", source = "current")
    SessionResponse toResponse(AuthSession session, boolean current);
}

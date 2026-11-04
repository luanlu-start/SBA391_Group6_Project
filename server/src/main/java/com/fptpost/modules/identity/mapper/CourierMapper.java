package com.fptpost.modules.identity.mapper;

import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.dto.response.CourierProfileResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface CourierMapper {
    @Mapping(target = "userId", source = "user.publicId")
    CourierProfileResponse toResponse(CourierProfile profile);
}

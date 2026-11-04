package com.fptpost.modules.identity.mapper;

import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.model.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface KycMapper {
    @Mapping(target = "id", source = "publicId") KycFileResponse toFile(KycFile file);
    @Mapping(target = "id", source = "publicId") @Mapping(target = "files", ignore = true)
    @Mapping(target = "idNumberMasked", expression = "java(\"********\" + request.getIdLastFour())")
    KycRequestResponse toRequest(KycRequest request);
}

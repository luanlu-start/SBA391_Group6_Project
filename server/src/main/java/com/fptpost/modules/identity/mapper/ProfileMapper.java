package com.fptpost.modules.identity.mapper;

import org.mapstruct.*;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.dto.response.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProfileMapper {
    @Mapping(target = "id", source = "publicId") @Mapping(target = "isDefault", source = "defaultAddress")
    AddressResponse toAddressResponse(Address address);
    @Mapping(target = "id", source = "publicId") AccountDataRequestResponse toDataRequestResponse(AccountDataRequest request);
}

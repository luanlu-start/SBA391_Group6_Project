package com.fptpost.modules.catalog.mapper;

import com.fptpost.modules.catalog.dto.request.CategoryRequest;
import com.fptpost.modules.catalog.dto.response.CategoryResponse;
import com.fptpost.modules.catalog.model.ItemCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    CategoryResponse toResponse(ItemCategory entity);

    List<CategoryResponse> toResponses(List<ItemCategory> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ItemCategory toEntity(CategoryRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CategoryRequest request, @MappingTarget ItemCategory entity);
}

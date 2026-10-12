package com.fptpost.modules.order.mapper;

import com.fptpost.modules.order.dto.ShippingOrderRequest;
import com.fptpost.modules.order.dto.ShippingOrderResponse;
import com.fptpost.modules.order.model.ShippingOrder;
import com.fptpost.modules.order.model.ShippingOrderImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "imageUrls", source = "images", qualifiedByName = "mapImagesToUrls")
    ShippingOrderResponse toResponse(ShippingOrder order);

    List<ShippingOrderResponse> toResponses(List<ShippingOrder> orders);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    ShippingOrder toEntity(ShippingOrderRequest request);

    @Named("mapImagesToUrls")
    default List<String> mapImagesToUrls(List<ShippingOrderImage> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .map(ShippingOrderImage::getImageUrl)
                .toList();
    }
}

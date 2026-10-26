package com.fptpost.modules.order.mapper;

import com.fptpost.modules.order.dto.OrderRequest;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.dto.response.MarketplaceOrderResponse;
import com.fptpost.modules.order.dto.response.OrderDetailResponse;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "imageUrls", source = "images", qualifiedByName = "mapImagesToUrls")
    OrderResponse toResponse(Order order);

    List<OrderResponse> toResponses(List<Order> orders);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    Order toEntity(OrderRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderCode", ignore = true)
    @Mapping(target = "senderId", ignore = true)
    @Mapping(target = "courierId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "images", ignore = true)
    void updateEntityFromDto(OrderRequest request, @MappingTarget Order order);

    MarketplaceOrderResponse toMarketplaceOrderResponse(Order order);

    default Page<MarketplaceOrderResponse> toMarketplaceOrderResponses(Page<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Page.empty();
        }
        return orders.map(this::toMarketplaceOrderResponse);
    }

    OrderDetailResponse toOrderDetailResponse(Order order);

    @Named("mapImagesToUrls")
    default List<String> mapImagesToUrls(List<OrderImage> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .map(OrderImage::getImageUrl)
                .toList();
    }
}

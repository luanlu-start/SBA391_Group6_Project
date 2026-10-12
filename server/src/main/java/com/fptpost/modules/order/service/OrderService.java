package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.dto.ShippingOrderRequest;
import com.fptpost.modules.order.dto.ShippingOrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.model.ShippingOrder;
import com.fptpost.modules.order.model.ShippingOrderImage;
import com.fptpost.modules.order.repository.ShippingOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final ShippingOrderRepository shippingOrderRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public ShippingOrderResponse createOrder(ShippingOrderRequest request) {
        String orderCode = request.getOrderCode();
        if (orderCode != null && !orderCode.isBlank()) {
            orderCode = orderCode.trim();
            if (shippingOrderRepository.existsByOrderCode(orderCode)) {
                throw new AppException(OrderErrorCode.ORDER_CODE_ALREADY_EXISTS);
            }
        } else {
            orderCode = generateOrderCode();
        }

        ShippingOrder order = orderMapper.toEntity(request);
        order.setOrderCode(orderCode);
        order.setStatus(OrderStatus.PENDING);

        if (request.getImageUrls() != null) {
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    order.addImage(ShippingOrderImage.builder()
                            .imageUrl(imageUrl.trim())
                            .build());
                }
            }
        }

        ShippingOrder saved = shippingOrderRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    public ShippingOrderResponse getOrderById(Long id) {
        ShippingOrder order = shippingOrderRepository.findById(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        return orderMapper.toResponse(order);
    }

    public List<ShippingOrderResponse> getAllOrders() {
        List<ShippingOrder> orders = shippingOrderRepository.findAll();
        return orderMapper.toResponses(orders);
    }

    private String generateOrderCode() {
        return "FP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}

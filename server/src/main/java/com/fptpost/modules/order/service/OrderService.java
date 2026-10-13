package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.dto.MarketplaceOrderResponse;
import com.fptpost.modules.order.dto.OrderRequest;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        String orderCode = generateUniqueOrderCode();

        Order order = orderMapper.toEntity(request);
        order.setOrderCode(orderCode);
        order.setStatus(OrderStatus.PENDING);

        if (request.getImageUrls() != null) {
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    order.addImage(OrderImage.builder()
                            .imageUrl(imageUrl.trim())
                            .build());
                }
            }
        }

        Order saved = orderRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        return orderMapper.toResponse(order);
    }

    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orderMapper.toResponses(orders);
    }

    private String generateUniqueOrderCode() {
        String code;
        do {
            code = "FP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }

    //Order Marketplace - Quốc An
    public Page<MarketplaceOrderResponse> viewAvailableOrder(Pageable pageable, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return orderMapper.toMarketplaceOrderResponses(orderRepository.findAvailableShippingOrder(pageable));
        } else {
            return orderMapper.toMarketplaceOrderResponses(orderRepository.findAvailableShippingOrderByKeyword(pageable, keyword));
        }
    }
}

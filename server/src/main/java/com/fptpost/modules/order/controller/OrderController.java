package com.fptpost.modules.order.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.order.dto.ShippingOrderRequest;
import com.fptpost.modules.order.dto.ShippingOrderResponse;
import com.fptpost.modules.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Shipping Order Management REST API endpoints")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Tạo mới đơn hàng", description = "Tạo mới một đơn hàng vận chuyển vào hệ thống")
    public ResponseEntity<ApiResponse<ShippingOrderResponse>> createOrder(
            @Valid @RequestBody ShippingOrderRequest request) {
        ShippingOrderResponse created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Order created successfully", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết đơn hàng", description = "Lấy thông tin chi tiết của một đơn hàng theo ID")
    public ResponseEntity<ApiResponse<ShippingOrderResponse>> getOrderById(@PathVariable Long id) {
        ShippingOrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", order));
    }

    @GetMapping
    @Operation(summary = "Xem tất cả đơn hàng", description = "Lấy danh sách tất cả các đơn hàng vận chuyển")
    public ResponseEntity<ApiResponse<List<ShippingOrderResponse>>> getAllOrders() {
        List<ShippingOrderResponse> orders = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", orders));
    }
}

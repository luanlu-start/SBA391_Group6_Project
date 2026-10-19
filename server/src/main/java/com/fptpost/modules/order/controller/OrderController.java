package com.fptpost.modules.order.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.order.dto.response.MarketplaceOrderResponse;
import com.fptpost.modules.order.dto.request.OrderRequest;
import com.fptpost.modules.order.dto.response.OrderResponse;
import com.fptpost.modules.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order Management REST API endpoints")
public class OrderController {

    private final OrderService orderService;
    private final com.fptpost.modules.identity.repository.UserRepository userRepository;

    @PostMapping(value = "/upload-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên hình ảnh đơn hàng", description = "Tải lên một hoặc nhiều hình ảnh cho đơn hàng (hỗ trợ JPG, PNG, WEBP, GIF)")
    public ResponseEntity<ApiResponse<List<String>>> uploadImages(
            @RequestParam("files") List<MultipartFile> files) {
        List<String> imageUrls = orderService.uploadOrderImages(files);
        return ResponseEntity.ok(ApiResponse.success("Upload hình ảnh thành công", imageUrls));
    }

    @PostMapping
    @Operation(summary = "Tạo mới đơn hàng", description = "Tạo mới một đơn hàng vào hệ thống")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request) {
        OrderResponse created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Order created successfully", created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết đơn hàng", description = "Lấy thông tin chi tiết của một đơn hàng theo ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        OrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", order));
    }

    @GetMapping
    @Operation(summary = "Xem tất cả đơn hàng", description = "Lấy danh sách tất cả các đơn hàng")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        List<OrderResponse> orders = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", orders));
    }

    @GetMapping("/marketplace-order")
    @Operation(summary = "Xem tất cả đơn hàng có thể giao", description = "Lấy danh sách tất cả các đơn hàng có thể giao")
    public ResponseEntity<ApiResponse<Page<MarketplaceOrderResponse>>> viewMarketplaceOrders(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable
            ) {
        Page<MarketplaceOrderResponse> marketplaceOrderResponses = orderService.viewAvailableOrder(pageable, keyword);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", marketplaceOrderResponses));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Nhận đơn hàng theo giá niêm yết (UC-17)", description = "Bưu tá nhận đơn hàng khả dụng trên sàn")
    public ResponseEntity<ApiResponse<com.fptpost.modules.order.dto.response.MarketOrderAcceptResponse>> acceptOrder(
            @PathVariable Long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt
    ) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new com.fptpost.common.exception.AppException(com.fptpost.common.exception.GlobalErrorCode.UNAUTHORIZED);
        }
        com.fptpost.modules.identity.model.User courierUser = userRepository.findByPublicId(java.util.UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new com.fptpost.common.exception.AppException(com.fptpost.common.exception.GlobalErrorCode.UNAUTHORIZED));

        var response = orderService.acceptMarketOrder(id, courierUser);
        return ResponseEntity.ok(ApiResponse.success(response.message(), response));
    }
}

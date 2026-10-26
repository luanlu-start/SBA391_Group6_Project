package com.fptpost.modules.order.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.order.dto.OrderCancelRequest;
import com.fptpost.modules.order.dto.OrderRequest;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.payment.dto.OrderPaymentRequest;
import com.fptpost.modules.payment.dto.OrderPaymentResponse;
import com.fptpost.modules.payment.service.OrderPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order Management REST API endpoints")
public class OrderController {

    private final OrderService orderService;
    private final OrderPaymentService orderPaymentService;
    private final UserRepository userRepository;

    @PostMapping(value = "/upload-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SENDER')")
    @Operation(summary = "Tải lên hình ảnh đơn hàng", description = "Tải lên một hoặc nhiều hình ảnh cho đơn hàng (hỗ trợ JPG, PNG, WEBP, GIF)")
    public ResponseEntity<ApiResponse<List<String>>> uploadImages(
            @RequestParam("files") List<MultipartFile> files) {
        List<String> imageUrls = orderService.uploadOrderImages(files);
        return ResponseEntity.ok(ApiResponse.success("Upload hình ảnh thành công", imageUrls));
    }

    @PostMapping
    @PreAuthorize("hasRole('SENDER')")
    @Operation(summary = "Tạo mới đơn hàng", description = "Tạo mới một đơn hàng vào hệ thống")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OrderRequest request) {
        Long senderId = null;
        if (jwt != null && jwt.getSubject() != null) {
            senderId = resolveUserId(jwt);
        } else if (request.getSenderId() != null) {
            senderId = request.getSenderId();
        } else {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        OrderResponse created = orderService.createOrder(senderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Order created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SENDER')")
    @Operation(summary = "Chỉnh sửa đơn hàng (UC-14, API-037)", description = "Cho phép người gửi cập nhật thông tin đơn hàng khi đơn còn ở trạng thái PENDING")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OrderRequest request) {
        Long currentUserId = null;
        if (jwt != null && jwt.getSubject() != null) {
            currentUserId = resolveUserId(jwt);
        } else if (request.getSenderId() != null) {
            currentUserId = request.getSenderId();
        }
        OrderResponse updated = orderService.updateOrder(id, currentUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Order updated successfully", updated));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SENDER')")
    @Operation(summary = "Hủy đơn hàng (UC-15, API-038)", description = "Cho phép người gửi hủy đơn hàng khi đơn chưa có tài xế nhận hoặc đang chờ xử lý")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) OrderCancelRequest request) {
        Long currentUserId = null;
        if (jwt != null && jwt.getSubject() != null) {
            currentUserId = resolveUserId(jwt);
        }
        String reason = request != null ? request.getReason() : null;
        OrderResponse cancelled = orderService.cancelOrder(id, currentUserId, reason);
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công", cancelled));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('SENDER')")
    @Operation(summary = "Xem danh sách đơn hàng của tôi", description = "Lấy danh sách tất cả các đơn hàng do tài khoản hiện tại tạo")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(@AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = resolveUserId(jwt);
        List<OrderResponse> orders = orderService.getOrdersBySenderId(currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", orders));
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
    @PostMapping("/{id}/payment")
    @Operation(summary = "Thanh toán và ký quỹ đơn hàng (UC-35, API-087)", description = "Người gửi thực hiện thanh toán ký quỹ đơn hàng qua Ví nội bộ hoặc Cổng VNPay.")
    public ResponseEntity<ApiResponse<OrderPaymentResponse>> payOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OrderPaymentRequest request,
            HttpServletRequest httpRequest) {
        Long userId = resolveUserId(jwt);
        OrderPaymentResponse response = orderPaymentService.payOrder(userId, id, request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Xử lý thanh toán đơn hàng thành công", response));
    }

    private Long resolveUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        UUID publicId = UUID.fromString(jwt.getSubject());
        return userRepository.findByPublicId(publicId)
                .map(User::getId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
    }
}

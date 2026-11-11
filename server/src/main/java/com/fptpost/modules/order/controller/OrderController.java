package com.fptpost.modules.order.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.order.dto.OrderCancelRequest;
import com.fptpost.modules.order.dto.OrderRequest;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.dto.PricingEstimateRequest;
import com.fptpost.modules.order.dto.PricingEstimateResponse;
import com.fptpost.modules.order.service.OrderService;
import com.fptpost.modules.order.service.PickupConfirmationService;
import com.fptpost.modules.order.service.PricingCalculatorService;
import com.fptpost.modules.order.service.RelayOrderService;
import com.fptpost.modules.order.dto.request.RelayOrderRequest;
import com.fptpost.modules.order.dto.request.RelayPricingEstimateRequest;
import com.fptpost.modules.order.dto.response.OrderLegResponse;
import com.fptpost.modules.order.dto.response.RelayOrderResponse;
import com.fptpost.modules.order.dto.response.RelayPricingEstimateResponse;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order Management REST API endpoints")
public class OrderController {

    private final OrderService orderService;
    private final OrderPaymentService orderPaymentService;
    private final PricingCalculatorService pricingCalculatorService;
    private final UserRepository userRepository;
    private final RelayOrderService relayOrderService;
    private final PickupConfirmationService pickupConfirmationService;

    @PostMapping("/pricing/estimate")
    @Operation(summary = "Ước tính giá và cước phí đơn hàng (UC-11, API-029)",
               description = "Tính toán khoảng cách theo tọa độ OSRM (fallback Haversine) và áp dụng biểu phí FPTPost")
    public ResponseEntity<ApiResponse<PricingEstimateResponse>> estimatePrice(
            @Valid @RequestBody PricingEstimateRequest request) {
        PricingEstimateResponse response = pricingCalculatorService.estimatePrice(request);
        return ResponseEntity.ok(ApiResponse.success("Ước tính cước phí thành công", response));
    }

    @PostMapping("/relay/estimate")
    @Operation(summary = "Ước tính cước và phân chia chặng đơn giao tiếp sức",
               description = "Tính toán khoảng cách và chia cước phí theo từng chặng (BR-R-047)")
    public ResponseEntity<ApiResponse<RelayPricingEstimateResponse>> estimateRelayPricing(
            @Valid @RequestBody RelayPricingEstimateRequest request) {
        RelayPricingEstimateResponse response = relayOrderService.estimateRelayPricing(request);
        return ResponseEntity.ok(ApiResponse.success("Ước tính cước tiếp sức thành công", response));
    }

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

    @PostMapping("/relay")
    @PreAuthorize("hasRole('SENDER')")
    @Operation(summary = "Tạo đơn giao tiếp sức (UC-32, API-082)",
               description = "Tạo mới đơn hàng giao tiếp sức chia theo chặng (BR-F-059, BR-R-047)")
    public ResponseEntity<ApiResponse<RelayOrderResponse>> createRelayOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RelayOrderRequest request) {
        Long senderId = null;
        if (jwt != null && jwt.getSubject() != null) {
            senderId = resolveUserId(jwt);
        } else if (request.getSenderId() != null) {
            senderId = request.getSenderId();
        } else {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        RelayOrderResponse created = relayOrderService.createRelayOrder(senderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đơn tiếp sức đã được tạo thành công", created));
    }

    @GetMapping("/{id}/legs")
    @Operation(summary = "Xem danh sách các chặng của đơn tiếp sức (UC-32, BR-F-062)")
    public ResponseEntity<ApiResponse<List<OrderLegResponse>>> getOrderLegs(@PathVariable Long id) {
        List<OrderLegResponse> legs = relayOrderService.getOrderLegs(id);
        return ResponseEntity.ok(ApiResponse.success(legs));
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

    @GetMapping("/assigned-to-me")
    @PreAuthorize("hasRole('COURIER')")
    @Operation(
            summary = "Danh sách đơn hàng được phân công cho Courier",
            description = "Chỉ trả về các đơn được phân công cho Courier đang đăng nhập"
    )
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAssignedOrders(
            @AuthenticationPrincipal Jwt jwt) {

        Long courierId = resolveUserId(jwt);

        List<OrderResponse> orders =
                orderService.getOrdersByCourierId(courierId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Assigned orders retrieved successfully",
                        orders
                )
        );
    }

    @GetMapping("/{id}/pickup-qr")
    @PreAuthorize("hasRole('SENDER')")
    @Operation(summary = "UC-25 - Lấy QR xác nhận lấy hàng")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPickupQr(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long senderId = resolveUserId(jwt);
        String qrToken = pickupConfirmationService.getPickupQr(id, senderId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Pickup QR generated",
                        Map.of("qrToken", qrToken)
                )
        );
    }

    @PostMapping(
            value = "/{id}/confirm-pickup",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('COURIER')")
    @Operation(summary = "UC-25 - Xác nhận lấy hàng bằng QR và ảnh")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmPickup(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("qrToken") String qrToken,
            @RequestParam("photo") MultipartFile photo) {

        Long courierId = resolveUserId(jwt);

        OrderResponse result = pickupConfirmationService.confirmPickup(
                id, courierId, qrToken, photo
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Pickup confirmed successfully",
                        result
                )
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết đơn hàng", description = "Lấy thông tin chi tiết của một đơn hàng theo ID hoặc mã vận đơn")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable String id) {
        OrderResponse order;
        try {
            order = orderService.getOrderById(Long.parseLong(id));
        } catch (NumberFormatException ex) {
            order = orderService.getOrderByCode(id);
        }
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

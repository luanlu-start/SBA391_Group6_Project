package com.fptpost.modules.batch.controller;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.batch.dto.request.BatchCreateRequest;
import com.fptpost.modules.batch.dto.request.RouteOptimizeRequest;
import com.fptpost.modules.batch.dto.response.DeliveryBatchResponse;
import com.fptpost.modules.batch.dto.response.EligibleBatchOrderResponse;
import com.fptpost.modules.batch.dto.response.RouteOptimizationResponse;
import com.fptpost.modules.batch.model.DeliveryBatchStatus;
import com.fptpost.modules.batch.service.IBatchService;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
@Tag(name = "Batches", description = "Quản lý Gộp đơn và gợi ý lộ trình điểm dừng")
public class BatchController {

    private final IBatchService batchService;
    private final UserRepository userRepository;

    // ==========================================
    // Lấy danh sách đơn hàng đủ điều kiện gộp
    // ==========================================
    @GetMapping("/eligible-orders")
    @Operation(summary = "Lấy danh sách đơn hàng đủ điều kiện để gộp", description = "Trả về danh sách các đơn hàng tài xế đang nhận hoặc đang giao để chọn gộp lộ trình")
    public ResponseEntity<ApiResponse<List<EligibleBatchOrderResponse>>> getEligibleOrders(
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        List<EligibleBatchOrderResponse> responses = batchService.getEligibleOrders(courier);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng đủ điều kiện thành công", responses));
    }

    // ==========================================
    // Gợi ý và tối ưu hóa thứ tự lộ trình điểm dừng
    // ==========================================
    @PostMapping("/optimize-route")
    @Operation(summary = "Gợi ý thứ tự điểm dừng tối ưu", description = "Tính toán lộ trình ngắn nhất ghé qua tất cả điểm lấy và giao hàng của các đơn được chọn (BR-F-058)")
    public ResponseEntity<ApiResponse<RouteOptimizationResponse>> optimizeRoute(
            @Valid @RequestBody RouteOptimizeRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        RouteOptimizationResponse response = batchService.optimizeRoute(request, courier);
        return ResponseEntity.ok(ApiResponse.success("Tính toán và gợi ý lộ trình thành công", response));
    }

    // ==========================================
    // API-080: Gộp đơn thành lượt giao (COURIER)
    // ==========================================
    @PostMapping
    @Operation(summary = "API-080: Gộp đơn thành lượt giao", description = "Tạo một lượt giao gộp gồm tối đa 5 đơn hàng cùng tuyến đường (BR-F-057, BR-R-046)")
    public ResponseEntity<ApiResponse<DeliveryBatchResponse>> createBatch(
            @Valid @RequestBody BatchCreateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        DeliveryBatchResponse response = batchService.createBatch(request, courier);
        return ResponseEntity.ok(ApiResponse.success("Tạo lượt giao gộp đơn hàng thành công", response));
    }

    // ==========================================
    // API-081: Thứ tự điểm dừng gợi ý (COURIER)
    // ==========================================
    @GetMapping("/{id}/route")
    @Operation(summary = "API-081: Thứ tự điểm dừng gợi ý của lượt giao", description = "Lấy danh sách các điểm dừng đã được tối ưu hóa theo thứ tự của lượt giao")
    public ResponseEntity<ApiResponse<RouteOptimizationResponse>> getBatchRoute(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        RouteOptimizationResponse response = batchService.getBatchRoute(id, courier);
        return ResponseEntity.ok(ApiResponse.success("Lấy lộ trình điểm dừng thành công", response));
    }

    // ==========================================
    // Lấy chi tiết lượt giao gộp
    // ==========================================
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết lượt giao gộp", description = "Lấy thông tin chi tiết và tiến độ thực hiện của lượt giao gộp")
    public ResponseEntity<ApiResponse<DeliveryBatchResponse>> getBatchById(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        DeliveryBatchResponse response = batchService.getBatchById(id, courier);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin lượt giao thành công", response));
    }

    // ==========================================
    // Lấy danh sách các lượt giao gộp của người giao
    // ==========================================
    @GetMapping
    @Operation(summary = "Danh sách lượt giao gộp của tôi", description = "Lấy danh sách các đợt giao gộp đã tạo và đang thực hiện")
    public ResponseEntity<ApiResponse<Page<DeliveryBatchResponse>>> getMyBatches(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        Page<DeliveryBatchResponse> page = batchService.getMyBatches(courier, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lượt giao thành công", page));
    }

    // ==========================================
    // Hoàn thành một điểm dừng
    // ==========================================
    @PatchMapping("/{id}/stops/{stopId}/complete")
    @Operation(summary = "Đánh dấu hoàn thành điểm dừng", description = "Cập nhật trạng thái điểm dừng đã lấy hoặc đã giao xong")
    public ResponseEntity<ApiResponse<DeliveryBatchResponse>> completeStop(
            @PathVariable("id") Long id,
            @PathVariable("stopId") Long stopId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        DeliveryBatchResponse response = batchService.completeStop(id, stopId, courier);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hoàn thành điểm dừng thành công", response));
    }

    // ==========================================
    // Cập nhật trạng thái lượt giao gộp
    // ==========================================
    @PatchMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái lượt giao gộp", description = "Chuyển trạng thái lượt giao sang IN_PROGRESS, COMPLETED hoặc CANCELLED")
    public ResponseEntity<ApiResponse<DeliveryBatchResponse>> updateBatchStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") DeliveryBatchStatus status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        User courier = resolveUser(jwt);
        DeliveryBatchResponse response = batchService.updateBatchStatus(id, status, courier);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái lượt giao thành công", response));
    }

    private User resolveUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
        try {
            UUID publicId = UUID.fromString(jwt.getSubject());
            return userRepository.findByPublicId(publicId)
                    .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        } catch (Exception e) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}

package com.fptpost.modules.batch.service;

import com.fptpost.modules.batch.dto.request.BatchCreateRequest;
import com.fptpost.modules.batch.dto.request.RouteOptimizeRequest;
import com.fptpost.modules.batch.dto.response.DeliveryBatchResponse;
import com.fptpost.modules.batch.dto.response.EligibleBatchOrderResponse;
import com.fptpost.modules.batch.dto.response.RouteOptimizationResponse;
import com.fptpost.modules.batch.model.DeliveryBatchStatus;
import com.fptpost.modules.identity.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IBatchService {

    /**
     * Lấy danh sách các đơn hàng đủ điều kiện để gộp của người giao hiện tại.
     */
    List<EligibleBatchOrderResponse> getEligibleOrders(User courier);

    /**
     * Gợi ý và tối ưu hóa thứ tự các điểm dừng (Pickup & Delivery) cho các đơn hàng được chọn.
     * Đảm bảo mọi điểm lấy hàng diễn ra trước điểm giao hàng tương ứng và tổng quãng đường là ngắn nhất.
     */
    RouteOptimizationResponse optimizeRoute(RouteOptimizeRequest request, User courier);

    /**
     * Tạo một lượt giao gộp đơn hàng mới (API-080).
     */
    DeliveryBatchResponse createBatch(BatchCreateRequest request, User courier);

    /**
     * Lấy lộ trình và thứ tự các điểm dừng của một lượt giao gộp (API-081).
     */
    RouteOptimizationResponse getBatchRoute(Long batchId, User courier);

    /**
     * Lấy thông tin chi tiết của một lượt giao gộp.
     */
    DeliveryBatchResponse getBatchById(Long batchId, User courier);

    /**
     * Lấy danh sách các lượt giao gộp của người giao.
     */
    Page<DeliveryBatchResponse> getMyBatches(User courier, Pageable pageable);

    /**
     * Đánh dấu hoàn tất một điểm dừng trong lượt giao gộp.
     */
    DeliveryBatchResponse completeStop(Long batchId, Long stopId, User courier);

    /**
     * Cập nhật trạng thái của lượt giao gộp (IN_PROGRESS, COMPLETED, CANCELLED).
     */
    DeliveryBatchResponse updateBatchStatus(Long batchId, DeliveryBatchStatus status, User courier);
}

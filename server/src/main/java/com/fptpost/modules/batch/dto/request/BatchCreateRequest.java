package com.fptpost.modules.batch.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchCreateRequest {

    @NotEmpty(message = "Danh sách đơn hàng không được để trống")
    @Size(min = 2, max = 5, message = "Số lượng đơn hàng gộp trong một lượt phải từ 2 đến tối đa 5 đơn")
    private List<Long> orderIds;

    private Long tripId;

    private String batchName;

    private String notes;

    // Thứ tự điểm dừng tùy chỉnh (danh sách các key dạng "PICKUP_orderId" hoặc "DELIVERY_orderId", tùy chọn)
    private List<String> customStopOrderKeys;
}

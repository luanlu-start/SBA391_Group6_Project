package com.fptpost.modules.trip.dto.request;

import com.fptpost.modules.identity.enums.VehicleType;
import com.fptpost.modules.trip.model.TripRecurrence;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật thông tin chuyến đi")
public class TripUpdateRequest {

    @NotBlank(message = "Điểm khởi hành không được để trống")
    @Schema(description = "Địa chỉ điểm đi", example = "Đại học FPT Cần Thơ, 600 Nguyễn Văn Cừ")
    private String origin;

    @NotNull(message = "Tọa độ vĩ độ điểm đi không được để trống")
    @Schema(description = "Vĩ độ điểm đi", example = "10.0299")
    private Double originLat;

    @NotNull(message = "Tọa độ kinh độ điểm đi không được để trống")
    @Schema(description = "Kinh độ điểm đi", example = "105.7705")
    private Double originLng;

    @NotBlank(message = "Điểm đến không được để trống")
    @Schema(description = "Địa chỉ điểm đến", example = "Bến Ninh Kiều, Ninh Kiều, Cần Thơ")
    private String destination;

    @NotNull(message = "Tọa độ vĩ độ điểm đến không được để trống")
    @Schema(description = "Vĩ độ điểm đến", example = "10.0337")
    private Double destinationLat;

    @NotNull(message = "Tọa độ kinh độ điểm đến không được để trống")
    @Schema(description = "Kinh độ điểm đến", example = "105.7865")
    private Double destinationLng;

    @NotNull(message = "Thời gian khởi hành không được để trống")
    @Schema(description = "Thời gian khởi hành dự kiến (ISO 8601)", example = "2026-11-01T08:00:00Z")
    private Instant departAt;

    @NotNull(message = "Loại phương tiện không được để trống")
    @Schema(description = "Phương tiện di chuyển (MOTORBIKE, CAR, BICYCLE)", example = "MOTORBIKE")
    private VehicleType vehicleType;

    @NotNull(message = "Sức chứa vận chuyển không được để trống")
    @Positive(message = "Sức chứa phải lớn hơn 0 kg")
    @Schema(description = "Sức chứa / khối lượng tối đa nhận chở (kg)", example = "10.0")
    private Double capacityKg;

    @DecimalMin(value = "0.0", message = "Giá sàn mong muốn không thể âm")
    @Schema(description = "Giá sàn mong muốn cho mỗi đơn (VND), để trống nếu không yêu cầu", example = "30000")
    private BigDecimal floorPrice;

    @Schema(description = "Tùy chọn chuyến đi định kỳ", example = "NONE")
    private TripRecurrence recurrence;

    @Schema(description = "Chuỗi mã hóa polyline hoặc GeoJSON của lộ trình")
    private String routePolyline;

    @Schema(description = "Quãng đường ước tính (km)", example = "8.5")
    private Double distanceKm;

    @Schema(description = "Thời gian di chuyển ước tính (phút)", example = "25")
    private Integer estimatedDurationMinutes;
}

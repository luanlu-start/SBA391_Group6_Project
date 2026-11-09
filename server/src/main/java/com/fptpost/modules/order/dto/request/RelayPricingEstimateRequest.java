package com.fptpost.modules.order.dto.request;

import com.fptpost.modules.order.dto.PricingEstimateRequest;
import com.fptpost.modules.order.dto.RelayPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RelayPricingEstimateRequest extends PricingEstimateRequest {

    @NotEmpty(message = "Cần ít nhất một điểm hẹn tiếp sức trung gian")
    @Size(max = 2, message = "Tối đa 2 điểm hẹn tiếp sức trung gian")
    private List<@Valid RelayPoint> relayPoints = new ArrayList<>();
}

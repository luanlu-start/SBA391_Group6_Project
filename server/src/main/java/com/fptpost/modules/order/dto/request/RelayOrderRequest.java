package com.fptpost.modules.order.dto.request;

import com.fptpost.modules.order.dto.OrderRequest;
import com.fptpost.modules.order.dto.RelayPoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RelayOrderRequest extends OrderRequest {

    @NotEmpty(message = "Đơn tiếp sức cần ít nhất một điểm hẹn bàn giao trung gian")
    @Size(max = 2, message = "Đơn giao tiếp sức có tối đa 2 điểm hẹn trung gian (tương ứng tối đa 3 chặng theo quy định PRM-102)")
    private List<@Valid RelayPoint> relayPoints = new ArrayList<>();
}

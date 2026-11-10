package com.fptpost.modules.order.dto.response;

import com.fptpost.modules.order.dto.OrderResponse;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class RelayOrderResponse extends OrderResponse {

    private List<OrderLegResponse> legs = new ArrayList<>();
}

package com.fptpost.modules.matching.service;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.matching.dto.request.QuickBidRequest;
import com.fptpost.modules.matching.dto.response.OrderCourierMatchResponse;
import com.fptpost.modules.matching.dto.response.TripOrderMatchResponse;
import com.fptpost.modules.order.dto.response.MarketOrderAcceptResponse;
import com.fptpost.modules.order.dto.response.OfferResponse;

import java.util.List;

public interface IMatchingService {

    /**
     * API-053 / UC-21: Gợi ý các đơn hàng tiện đường phù hợp nhất cho một chuyến đi cụ thể.
     * Áp dụng ràng buộc độ lệch <= 3.0 km (PRM-099) và trả tối đa 10 gợi ý (PRM-100).
     */
    List<TripOrderMatchResponse> getOrdersForTrip(Long tripId, User courierUser);

    /**
     * API-054 / UC-21: Gợi ý các chuyến đi / người giao tiện đường phù hợp nhất cho một đơn hàng mở.
     * Hiển thị cho người gửi xếp hạng theo điểm phù hợp (score) và độ lệch tuyến (detour_km).
     */
    List<OrderCourierMatchResponse> getCouriersForOrder(Long orderId, User senderUser);

    /**
     * Nhận đơn nhanh từ danh sách gợi ý của chuyến đi (Flow bước 5 & BR-F-038).
     */
    MarketOrderAcceptResponse quickAcceptOrder(Long tripId, Long orderId, User courierUser);

    /**
     * Chào giá nhanh từ danh sách gợi ý của chuyến đi (Flow bước 5 & BR-F-038).
     */
    OfferResponse quickBidOrder(Long tripId, Long orderId, QuickBidRequest request, User courierUser);
}

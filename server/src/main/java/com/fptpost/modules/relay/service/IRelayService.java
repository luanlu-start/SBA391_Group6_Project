package com.fptpost.modules.relay.service;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.relay.dto.*;

import java.util.List;

public interface IRelayService {

    /**
     * Lấy danh sách điểm hẹn bàn giao an toàn được duyệt quanh campus (BR-F-061).
     */
    List<MeetingPointResponse> getSafeMeetingPoints(String campusArea);

    /**
     * Tạo mã QR bàn giao chặng (hiệu lực 10 phút theo PRM-097).
     * Dành cho người giao chặng hiện tại xuất trình cho người giao chặng tiếp theo.
     */
    HandoverTokenResponse generateHandoverToken(Long orderId, Integer legNo, User courier);

    /**
     * Lấy mã QR bàn giao còn hiệu lực (nếu có).
     */
    HandoverTokenResponse getActiveHandoverToken(Long orderId, Integer legNo, User courier);

    /**
     * API-083: Xác nhận bàn giao chặng bằng mã QR + ảnh hàng (BR-F-060, BR-R-048).
     * Dành cho người giao chặng tiếp theo quét QR/nhập mã và chụp ảnh xác nhận.
     */
    HandoverConfirmResponse confirmHandover(Long orderId, Integer legNo, HandoverConfirmRequest request, User courier);

    /**
     * Báo cáo sự cố khi bàn giao tại điểm hẹn (Luồng ngoại lệ A2).
     */
    HandoverIncidentResponse reportIncident(Long orderId, Integer legNo, HandoverIncidentRequest request, User courier);

    /**
     * Xem thông tin chi tiết đơn hàng tiếp sức và các chặng.
     */
    RelayOrderDetailResponse getRelayOrderDetails(Long orderId, User user);

    /**
     * Xem danh sách các chặng tiếp sức liên quan đến tài xế hiện tại.
     */
    List<OrderLegResponse> getCourierRelayLegs(User courier);

    /**
     * Tạo đơn hàng giao tiếp sức (hỗ trợ UC-32 / chạy thử nghiệm / dữ liệu thực tế).
     */
    RelayOrderDetailResponse createRelayOrder(RelayOrderCreateRequest request, User sender);
}

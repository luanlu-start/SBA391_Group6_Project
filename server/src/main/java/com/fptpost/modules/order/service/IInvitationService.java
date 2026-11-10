package com.fptpost.modules.order.service;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.order.dto.request.InvitationCreateRequest;
import com.fptpost.modules.order.dto.response.CourierSummaryResponse;
import com.fptpost.modules.order.dto.response.FavoriteCourierResponse;
import com.fptpost.modules.order.dto.response.InvitationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interface cho nghiệp vụ UC-20: Mời trực tiếp người giao & quản lý người giao yêu thích.
 */
public interface IInvitationService {

    /**
     * API-051 / UC-20: Người gửi mời trực tiếp một người giao cho đơn hàng.
     */
    InvitationResponse inviteCourier(String orderIdentifier, InvitationCreateRequest request, User senderUser);

    /**
     * Lấy danh sách lời mời của một đơn hàng.
     */
    List<InvitationResponse> getInvitationsForOrder(String orderIdentifier, User senderUser);

    /**
     * Người gửi hủy một lời mời đang chờ.
     */
    InvitationResponse cancelInvitation(Long invitationId, User senderUser);

    /**
     * Lấy danh sách lời mời nhận được dành cho người giao.
     */
    Page<InvitationResponse> getCourierInvitations(User courierUser, String status, Pageable pageable);

    /**
     * Người giao chấp nhận lời mời giao hàng.
     */
    InvitationResponse acceptInvitation(Long invitationId, User courierUser);

    /**
     * Người giao từ chối lời mời giao hàng.
     */
    InvitationResponse rejectInvitation(Long invitationId, User courierUser, String reason);

    /**
     * API-052 / UC-20: Thêm hoặc bỏ người giao yêu thích.
     */
    FavoriteCourierResponse toggleFavorite(Long courierId, String note, User senderUser);

    /**
     * Lấy danh sách người giao yêu thích của tôi.
     */
    List<FavoriteCourierResponse> getMyFavorites(User senderUser);

    /**
     * Xóa người giao khỏi danh sách yêu thích.
     */
    void removeFavorite(Long courierId, User senderUser);

    /**
     * Tìm kiếm danh sách tài xế / người giao để mời vào đơn hàng.
     */
    Page<CourierSummaryResponse> searchCouriers(String keyword, User senderUser, Pageable pageable);
}

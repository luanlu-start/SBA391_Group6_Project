import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type {
    Invitation,
    InvitationCreateRequest,
    FavoriteCourier,
    CourierSummary,
} from '../types/invitation';

export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

export const invitationApi = {
    /**
     * API-051: Mời trực tiếp người giao
     */
    inviteCourier: (orderId: string | number, data: InvitationCreateRequest) => {
        return axiosClient.post<unknown, ApiResponse<Invitation>>(`/orders/${orderId}/invitations`, data);
    },

    /**
     * Lấy danh sách lời mời của đơn hàng (Sender)
     */
    getOrderInvitations: (orderId: string | number) => {
        return axiosClient.get<unknown, ApiResponse<Invitation[]>>(`/orders/${orderId}/invitations`);
    },

    /**
     * Hủy lời mời giao hàng (Sender)
     */
    cancelInvitation: (orderId: string | number, invitationId: number) => {
        return axiosClient.delete<unknown, ApiResponse<Invitation>>(`/orders/${orderId}/invitations/${invitationId}`);
    },

    /**
     * Lấy danh sách lời mời nhận được (Courier)
     */
    getMyInvitations: (params?: { status?: string; page?: number; size?: number }) => {
        return axiosClient.get<unknown, ApiResponse<PageResponse<Invitation>>>('/me/invitations', { params });
    },

    /**
     * Chấp nhận lời mời giao hàng (Courier)
     */
    acceptInvitation: (invitationId: number) => {
        return axiosClient.post<unknown, ApiResponse<Invitation>>(`/invitations/${invitationId}/accept`);
    },

    /**
     * Từ chối lời mời giao hàng (Courier)
     */
    rejectInvitation: (invitationId: number, reason?: string) => {
        return axiosClient.post<unknown, ApiResponse<Invitation>>(`/invitations/${invitationId}/reject`, { reason });
    },

    /**
     * API-052: Thêm/bỏ người giao yêu thích
     */
    toggleFavorite: (courierId: number, note?: string) => {
        return axiosClient.put<unknown, ApiResponse<FavoriteCourier | null>>(`/me/favorites/${courierId}`, { note });
    },

    /**
     * Lấy danh sách người giao yêu thích của tôi
     */
    getMyFavorites: () => {
        return axiosClient.get<unknown, ApiResponse<FavoriteCourier[]>>('/me/favorites');
    },

    /**
     * Xóa bưu tá khỏi danh sách yêu thích
     */
    removeFavorite: (courierId: number) => {
        return axiosClient.delete<unknown, ApiResponse<void>>(`/me/favorites/${courierId}`);
    },

    /**
     * Tìm kiếm người giao hàng để gửi lời mời
     */
    searchCouriers: (params?: { keyword?: string; page?: number; size?: number }) => {
        return axiosClient.get<unknown, ApiResponse<PageResponse<CourierSummary>>>('/couriers/search', { params });
    },
};

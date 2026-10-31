import axiosClient from './axiosClient';
import type { ApiResponse } from '../types/api';
import type {
    Offer,
    OfferCreateRequest,
    OfferCounterRequest,
    OfferAcceptResponse,
} from '../types/offer';

export const offerApi = {
    /**
     * API-044 / UC-18: Gửi chào giá (COURIER)
     */
    createOffer: (orderId: string | number, data: OfferCreateRequest) => {
        return axiosClient.post<unknown, ApiResponse<Offer>>(`/orders/${orderId}/offers`, data);
    },

    /**
     * API-045 / UC-18: Rút chào giá (COURIER)
     */
    withdrawOffer: (offerId: number) => {
        return axiosClient.delete<unknown, ApiResponse<Offer>>(`/offers/${offerId}`);
    },

    /**
     * API-046 / UC-18: Chào giá của tôi (COURIER)
     */
    getMyOffers: () => {
        return axiosClient.get<unknown, ApiResponse<Offer[]>>('/me/offers');
    },

    /**
     * API-047 / UC-19: Danh sách chào giá của một đơn hàng (SENDER / COURIER)
     */
    getOffersForOrder: (orderId: string | number) => {
        return axiosClient.get<unknown, ApiResponse<Offer[]>>(`/orders/${orderId}/offers`);
    },

    /**
     * API-048 / UC-19: Chấp nhận chào giá (SENDER)
     */
    acceptOffer: (offerId: number) => {
        return axiosClient.post<unknown, ApiResponse<OfferAcceptResponse>>(`/offers/${offerId}/accept`);
    },

    /**
     * API-049 / UC-19: Từ chối chào giá (SENDER)
     */
    rejectOffer: (offerId: number) => {
        return axiosClient.post<unknown, ApiResponse<Offer>>(`/offers/${offerId}/reject`);
    },

    /**
     * API-050 / UC-19: Phản giá (SENDER hoặc COURIER)
     */
    counterOffer: (offerId: number, data: OfferCounterRequest) => {
        return axiosClient.post<unknown, ApiResponse<Offer>>(`/offers/${offerId}/counter`, data);
    },
};

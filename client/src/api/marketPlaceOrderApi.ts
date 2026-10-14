import axiosClient from './axiosClient';
import type {MarketPlaceOrder, MarketplaceOrderParams, SpringPageResponse} from "../types/order";
import type { ApiResponse} from '../types/api';

export const marketPlaceOrderApi = {
    getAll: (params: MarketplaceOrderParams = {}) => {
        return axiosClient.get<unknown, ApiResponse<SpringPageResponse<MarketPlaceOrder>>>('/orders/marketplace-order', { params });
    }
}

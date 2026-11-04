package com.fptpost.modules.order.service;

import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.order.dto.request.OfferCounterRequest;
import com.fptpost.modules.order.dto.request.OfferCreateRequest;
import com.fptpost.modules.order.dto.response.OfferAcceptResponse;
import com.fptpost.modules.order.dto.response.OfferResponse;

import java.util.List;

public interface IOfferService {

    /**
     * API-044 / UC-18: Gửi chào giá cho đơn hàng.
     */
    OfferResponse createOffer(String orderIdentifier, OfferCreateRequest request, User courierUser);

    /**
     * API-045 / UC-18: Rút chào giá.
     */
    OfferResponse withdrawOffer(Long offerId, User courierUser);

    /**
     * API-046 / UC-18: Lấy danh sách chào giá của bưu tá hiện tại.
     */
    List<OfferResponse> getMyOffers(User courierUser);

    /**
     * API-047 / UC-19: Lấy danh sách chào giá của một đơn hàng (cho người gửi hoặc bưu tá xem chào giá của mình).
     */
    List<OfferResponse> getOffersForOrder(String orderIdentifier, User currentUser);

    /**
     * API-048 / UC-19: Người gửi chấp nhận chào giá.
     */
    OfferAcceptResponse acceptOffer(Long offerId, User senderUser);

    /**
     * API-049 / UC-19: Người gửi từ chối chào giá.
     */
    OfferResponse rejectOffer(Long offerId, User senderUser);

    /**
     * API-050 / UC-19: Phản giá (thương lượng tối đa 3 vòng giữa người gửi và bưu tá).
     */
    OfferResponse counterOffer(Long offerId, OfferCounterRequest request, User currentUser);
}


package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PickupConfirmationService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @Value("${app.pickup.qr-secret:}")
    private String qrSecret;

    @Transactional(readOnly = true)
    public String getPickupQr(Long orderId, Long senderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(
                        OrderErrorCode.ORDER_NOT_FOUND));

        if (!Objects.equals(order.getSenderId(), senderId)) {
            throw new AppException(
                    OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() != OrderStatus.SHIPPER_ASSIGNED) {
            throw new AppException(
                    OrderErrorCode.PICKUP_INVALID_STATUS);
        }

        return sign(order);
    }

    @Transactional
    public OrderResponse confirmPickup(
            Long orderId,
            Long courierId,
            String qrToken,
            MultipartFile photo) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(
                        OrderErrorCode.ORDER_NOT_FOUND));

        if (!Objects.equals(order.getCourierId(), courierId)) {
            throw new AppException(
                    OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        if (order.getStatus() == OrderStatus.IN_DELIVERY) {
            throw new AppException(
                    OrderErrorCode.PICKUP_ALREADY_CONFIRMED);
        }

        if (order.getStatus() != OrderStatus.SHIPPER_ASSIGNED) {
            throw new AppException(
                    OrderErrorCode.PICKUP_INVALID_STATUS);
        }

        if (qrToken == null || !verify(order, qrToken)) {
            throw new AppException(
                    OrderErrorCode.PICKUP_INVALID_QR);
        }

        if (photo == null || photo.isEmpty()) {
            throw new AppException(
                    OrderErrorCode.INVALID_IMAGE_FILE);
        }

        List<String> urls = orderService.uploadOrderImages(
                List.of(photo));

        order.addImage(OrderImage.builder()
                .imageUrl(urls.get(0))
                .build());

        order.setStatus(OrderStatus.IN_DELIVERY);

        Order saved = orderRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    private String sign(Order order) {
        try {
            if (qrSecret == null || qrSecret.length() < 32) {
                throw new IllegalStateException(
                        "PICKUP_QR_SECRET must be configured");
            }

            String payload = "PICKUP:" + order.getId()
                    + ":" + order.getOrderCode();

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    qrSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));

            byte[] signature = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(signature);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot generate pickup QR", e);
        }
    }

    private boolean verify(Order order, String token) {
        try {
            byte[] supplied = Base64.getUrlDecoder()
                    .decode(token.trim());

            byte[] expected = Base64.getUrlDecoder()
                    .decode(sign(order));

            return MessageDigest.isEqual(expected, supplied);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

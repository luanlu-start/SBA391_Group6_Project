package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.order.dto.response.MarketOrderAcceptResponse;
import com.fptpost.modules.order.dto.response.MarketplaceOrderDetailResponse;
import com.fptpost.modules.order.dto.response.MarketplaceOrderResponse;
import com.fptpost.modules.order.dto.request.OrderRequest;
import com.fptpost.modules.order.dto.response.OrderDetailResponse;
import com.fptpost.modules.order.dto.response.OrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CourierProfileRepository courierProfileRepository;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        String orderCode = generateUniqueOrderCode();

        Order order = orderMapper.toEntity(request);
        order.setOrderCode(orderCode);
        order.setStatus(OrderStatus.PENDING);

        if (request.getImageUrls() != null) {
            for (String imageUrl : request.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    order.addImage(OrderImage.builder()
                            .imageUrl(imageUrl.trim())
                            .build());
                }
            }
        }

        Order saved = orderRepository.save(order);
        return orderMapper.toResponse(saved);
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        return orderMapper.toResponse(order);
    }

    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orderMapper.toResponses(orders);
    }

    public List<String> uploadOrderImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new AppException(OrderErrorCode.INVALID_IMAGE_FILE);
        }

        Path uploadPath = Paths.get("uploads", "orders").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new AppException(OrderErrorCode.FILE_UPLOAD_FAILED);
        }

        List<String> imageUrls = new ArrayList<>();
        Set<String> allowedMimeTypes = Set.of(
                MediaType.IMAGE_JPEG_VALUE,
                MediaType.IMAGE_PNG_VALUE,
                "image/webp",
                MediaType.IMAGE_GIF_VALUE
        );

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String contentType = file.getContentType();
            if (contentType == null || !allowedMimeTypes.contains(contentType.toLowerCase())) {
                throw new AppException(OrderErrorCode.INVALID_IMAGE_FILE);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = ".jpg";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            }

            String uniqueFileName = UUID.randomUUID() + extension;
            Path targetLocation = uploadPath.resolve(uniqueFileName);

            try {
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new AppException(OrderErrorCode.FILE_UPLOAD_FAILED);
            }

            String baseUrl = "";
            try {
                baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            } catch (Exception ignored) {
                // In non-web contexts such as unit tests, fallback to relative path
            }
            imageUrls.add(baseUrl + "/uploads/orders/" + uniqueFileName);
        }

        if (imageUrls.isEmpty()) {
            throw new AppException(OrderErrorCode.INVALID_IMAGE_FILE);
        }

        return imageUrls;
    }

    private String generateUniqueOrderCode() {
        String code;
        do {
            code = "FP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }

    //Order Marketplace - Quốc An
    public Page<MarketplaceOrderResponse> viewAvailableOrder(Pageable pageable, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return orderMapper.toMarketplaceOrderResponses(orderRepository.findAvailableShippingOrder(pageable));
        } else {
            return orderMapper.toMarketplaceOrderResponses(orderRepository.findAvailableShippingOrderByKeyword(pageable, keyword));
        }
    }

    public OrderDetailResponse claimOrder(Long id) {
        Optional<Order> order = orderRepository.findAvailableOrderById(id);
        if (order.isEmpty()) {
            throw new AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }
        return orderMapper.toOrderDetailResponse(order.get());
    }

    // ==========================================
    // UC-17: Nhận đơn (giá niêm yết) - Võ Quốc An
    // ==========================================
    // UC-17: Nhận đơn theo giá niêm yết (API-043)
    // ==========================================
    @Transactional
    public MarketOrderAcceptResponse acceptMarketOrder(Long orderId, User courierUser) {
        return acceptMarketOrder(String.valueOf(orderId), courierUser);
    }

    @Transactional
    public MarketOrderAcceptResponse acceptMarketOrder(String identifier, User courierUser) {
        if (courierUser == null) {
            throw new com.fptpost.common.exception.AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        // BR-R-009: Chỉ tài khoản có vai trò Người giao (COURIER) mới được nhận đơn
        boolean hasCourierRole = courierUser.getRoles() != null && courierUser.getRoles().stream()
                .anyMatch(r -> "COURIER".equalsIgnoreCase(r.getCode()));
        if (!hasCourierRole) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.COURIER_ROLE_REQUIRED);
        }

        if (courierUser.getStatus() != UserStatus.ACTIVE || courierUser.isDeleted()) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.COURIER_ACCOUNT_RESTRICTED);
        }

        // BR-R-025: Khóa chống tranh chấp đồng thời (Pessimistic write lock)
        Order order = findMarketOrderForUpdateByIdentifier(identifier);

        // Kiểm tra tính khả dụng: đơn phải đang mở và chưa ai nhận
        if (order.getStatus() != OrderStatus.FINDING_SHIPPER || order.getCourierId() != null) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.ORDER_NOT_AVAILABLE);
        }

        // BR-R-008: Vai trò kép nhưng không được tự nhận đơn của chính mình
        if (order.getSenderId() != null && order.getSenderId().equals(courierUser.getId())) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.ORDER_SELF_ACCEPT_FORBIDDEN);
        }

        // Lấy hoặc khởi tạo CourierProfile (hạng thành viên mặc định Tân binh NEW)
        CourierProfile profile = null;
        try {
            profile = courierProfileRepository.findByUserId(courierUser.getId()).orElse(null);
        } catch (Exception e) {
            log.warn("Lỗi truy vấn hồ sơ bưu tá: {}", e.getMessage());
        }

        if (profile == null) {
            try {
                CourierProfile newProfile = CourierProfile.builder()
                        .user(courierUser)
                        .tier(CourierTier.SILVER)
                        .trustScore(80)
                        .completedOrders(10)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                profile = courierProfileRepository.save(newProfile);
            } catch (Exception e) {
                log.warn("Không thể lưu profile bưu tá vào DB, sử dụng profile mặc định: {}", e.getMessage());
                profile = CourierProfile.builder()
                        .user(courierUser)
                        .tier(CourierTier.SILVER)
                        .trustScore(80)
                        .completedOrders(10)
                        .build();
            }
        }

        if (profile.getRestrictedUntil() != null && profile.getRestrictedUntil().isAfter(Instant.now())) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.COURIER_ACCOUNT_RESTRICTED);
        }

        CourierTier tier = profile.getTier() != null ? profile.getTier() : CourierTier.SILVER;

        // BR-R-024: Đơn giá trị khai báo > 1.000.000đ [PRM-017] yêu cầu hạng Bạc trở lên
        BigDecimal highValueThreshold = new BigDecimal("1000000");
        if (order.getDeclaredValue() != null && order.getDeclaredValue().compareTo(highValueThreshold) > 0) {
            if (!tier.isCanAcceptHighValue()) {
                throw new com.fptpost.common.exception.AppException(OrderErrorCode.COURIER_HIGH_VALUE_RESTRICTED);
            }
        }

        // BR-R-028: Kiểm tra hạn mức nhận đơn theo hạng
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.AWAITING_PAYMENT,
                OrderStatus.SHIPPER_ASSIGNED,
                OrderStatus.IN_DELIVERY
        );

        long activeOrdersCount = orderRepository.countByCourierIdAndStatusIn(courierUser.getId(), activeStatuses);
        if (activeOrdersCount + 1 > tier.getMaxConcurrentOrders()) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.COURIER_MAX_ORDERS_EXCEEDED);
        }

        BigDecimal currentTotalValue = orderRepository.sumDeclaredValueByCourierIdAndStatusIn(courierUser.getId(), activeStatuses);
        if (currentTotalValue == null) {
            currentTotalValue = BigDecimal.ZERO;
        }
        BigDecimal newOrderValue = order.getDeclaredValue() != null ? order.getDeclaredValue() : BigDecimal.ZERO;
        if (currentTotalValue.add(newOrderValue).compareTo(tier.getMaxTotalDeclaredValue()) > 0) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.COURIER_MAX_VALUE_EXCEEDED);
        }

        // BR-F-029: Cập nhật trạng thái đơn sang Chờ ký quỹ (AWAITING_PAYMENT), gắn courier_id và chốt giá niêm yết
        order.setCourierId(courierUser.getId());
        order.setStatus(OrderStatus.AWAITING_PAYMENT);

        BigDecimal agreedPrice = order.getPriceSuggested() != null ? order.getPriceSuggested()
                : (order.getDeclaredValue() != null ? order.getDeclaredValue() : BigDecimal.valueOf(30000));
        order.setPriceAgreed(agreedPrice);
        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);

        return new MarketOrderAcceptResponse(
                saved.getId(),
                saved.getOrderCode(),
                saved.getSenderId(),
                saved.getCourierId(),
                saved.getPickupAddress(),
                saved.getDeliveryAddress(),
                saved.getProductName(),
                saved.getQuantity(),
                saved.getWeightKg(),
                saved.getDeclaredValue(),
                saved.getPriceAgreed(),
                saved.getStatus(),
                saved.getUpdatedAt(),
                "Nhận đơn thành công! Đơn hàng đã chuyển sang Chờ ký quỹ."
        );
    }

    public MarketplaceOrderDetailResponse getMarketOrderDetail(Long id, User currentUser) {
        return getMarketOrderDetail(String.valueOf(id), currentUser);
    }

    public MarketplaceOrderDetailResponse getMarketOrderDetail(String identifier, User currentUser) {
        Order order = findMarketOrderByIdentifier(identifier);

        boolean isAssignedToCurrent = currentUser != null && currentUser.getId().equals(order.getCourierId());
        boolean isOwner = currentUser != null && currentUser.getId().equals(order.getSenderId());

        String maskedReceiverName = (isAssignedToCurrent || isOwner) ? order.getReceiverName() : maskName(order.getReceiverName());
        String maskedReceiverPhone = (isAssignedToCurrent || isOwner) ? order.getReceiverPhone() : maskPhone(order.getReceiverPhone());

        List<String> imageUrls = order.getImages() != null
                ? order.getImages().stream().map(OrderImage::getImageUrl).toList()
                : List.of();

        boolean requiresSilver = order.getDeclaredValue() != null
                && order.getDeclaredValue().compareTo(new BigDecimal("1000000")) > 0;

        boolean isAvailable = order.getStatus() == OrderStatus.FINDING_SHIPPER && order.getCourierId() == null;

        return new MarketplaceOrderDetailResponse(
                order.getId(),
                order.getOrderCode(),
                order.getSenderId(),
                order.getPickupAddress(),
                order.getDeliveryAddress(),
                maskedReceiverName,
                maskedReceiverPhone,
                order.getProductName(),
                order.getProductDescription(),
                order.getQuantity(),
                order.getWeightKg(),
                order.getDeclaredValue(),
                order.getPriceSuggested(),
                order.getDistanceKm(),
                order.getDeliveryNote(),
                order.getStatus(),
                order.getCreatedAt(),
                imageUrls,
                requiresSilver,
                isAvailable
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 3);
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) return "***";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].charAt(0) + "***";
        return parts[0] + " ***";
    }

    private Order findMarketOrderByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        try {
            Long parsedId = Long.parseLong(identifier);
            return orderRepository.findById(parsedId)
                    .or(() -> orderRepository.findByOrderCode(identifier))
                    .orElseThrow(() -> new com.fptpost.common.exception.AppException(OrderErrorCode.ORDER_NOT_FOUND));
        } catch (NumberFormatException e) {
            return orderRepository.findByOrderCode(identifier)
                    .orElseThrow(() -> new com.fptpost.common.exception.AppException(OrderErrorCode.ORDER_NOT_FOUND));
        }
    }

    private Order findMarketOrderForUpdateByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new com.fptpost.common.exception.AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        try {
            Long parsedId = Long.parseLong(identifier);
            return orderRepository.findByIdForUpdate(parsedId)
                    .or(() -> orderRepository.findByOrderCodeForUpdate(identifier))
                    .orElseThrow(() -> new com.fptpost.common.exception.AppException(OrderErrorCode.ORDER_NOT_FOUND));
        } catch (NumberFormatException e) {
            return orderRepository.findByOrderCodeForUpdate(identifier)
                    .orElseThrow(() -> new com.fptpost.common.exception.AppException(OrderErrorCode.ORDER_NOT_FOUND));
        }
    }
}
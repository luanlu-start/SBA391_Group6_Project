package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.dto.MarketplaceOrderResponse;
import com.fptpost.modules.order.dto.OrderRequest;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

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
}
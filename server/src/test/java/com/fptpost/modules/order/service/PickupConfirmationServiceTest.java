package com.fptpost.modules.order.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure mocks: no application startup, database, real upload, or real order IDs. */
@ExtendWith(MockitoExtension.class)
class PickupConfirmationServiceTest {
    @Mock OrderRepository orders;
    @Mock OrderService uploads;
    PickupConfirmationService service;
    Order order;
    MultipartFile photo = new MockMultipartFile("photo", "proof.jpg", "image/jpeg", new byte[]{1});

    @BeforeEach
    void setup() {
        service = new PickupConfirmationService(orders, uploads,
                org.mapstruct.factory.Mappers.getMapper(OrderMapper.class));
        ReflectionTestUtils.setField(service, "qrSecret", "uc25-test-only-key-0123456789abcdef");
        order = Order.builder().id(25001L).orderCode("UC25-MOCK-001")
                .senderId(251L).courierId(252L).status(OrderStatus.SHIPPER_ASSIGNED).build();
    }

    String token() {
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        return service.getPickupQr(order.getId(), order.getSenderId());
    }

    void lockOrder() {
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    void rejected(OrderErrorCode code, Long courier, String token, MultipartFile file) {
        lockOrder();
        var error = assertThrows(AppException.class,
                () -> service.confirmPickup(order.getId(), courier, token, file));
        assertEquals(code, error.getErrorCode());
        verifyNoInteractions(uploads);
        verify(orders, never()).save(any());
        assertTrue(order.getImages().isEmpty());
    }

    @Test void ownerCanGenerateStableToken() {
        assertEquals(token(), service.getPickupQr(order.getId(), 251L));
    }

    @Test void nonOwnerCannotGetQr() {
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        assertEquals(OrderErrorCode.ORDER_ACCESS_DENIED,
                assertThrows(AppException.class, () -> service.getPickupQr(order.getId(), 999L)).getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "SHIPPER_ASSIGNED", mode = EnumSource.Mode.EXCLUDE)
    void qrUnavailableInOtherStates(OrderStatus status) {
        order.setStatus(status);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        assertEquals(OrderErrorCode.PICKUP_INVALID_STATUS,
                assertThrows(AppException.class, () -> service.getPickupQr(order.getId(), 251L)).getErrorCode());
    }

    @Test void wrongQr() { rejected(OrderErrorCode.PICKUP_INVALID_QR, 252L, "wrong-token", photo); }
    @Test void malformedQr() { rejected(OrderErrorCode.PICKUP_INVALID_QR, 252L, "%%%", photo); }
    @Test void absentQr() { rejected(OrderErrorCode.PICKUP_INVALID_QR, 252L, null, photo); }
    @Test void anotherOrdersQr() {
        String otherToken = token();
        order.setId(25002L);
        order.setOrderCode("UC25-MOCK-002");
        rejected(OrderErrorCode.PICKUP_INVALID_QR, 252L, otherToken, photo);
    }
    @Test void unassignedCourier() { rejected(OrderErrorCode.ORDER_ACCESS_DENIED, 999L, "any", photo); }
    @Test void missingPhoto() { rejected(OrderErrorCode.INVALID_IMAGE_FILE, 252L, token(), null); }
    @Test void emptyPhoto() {
        rejected(OrderErrorCode.INVALID_IMAGE_FILE, 252L, token(),
                new MockMultipartFile("photo", new byte[0]));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPER_ASSIGNED", "IN_DELIVERY"}, mode = EnumSource.Mode.EXCLUDE)
    void invalidStatus(OrderStatus status) {
        order.setStatus(status);
        rejected(OrderErrorCode.PICKUP_INVALID_STATUS, 252L, "any", photo);
        assertEquals(status, order.getStatus());
    }

    @Test void successLinksPhotoAndRejectsSecondConfirmation() {
        String qr = token();
        lockOrder();
        when(uploads.uploadOrderImages(List.of(photo))).thenReturn(List.of("/uploads/orders/test.jpg"));
        when(orders.save(order)).thenReturn(order);
        var response = service.confirmPickup(order.getId(), 252L, " " + qr + " ", photo);
        assertEquals(OrderStatus.IN_DELIVERY, response.getStatus());
        assertEquals(List.of("/uploads/orders/test.jpg"), response.getImageUrls());
        assertSame(order, order.getImages().get(0).getOrder());
        assertEquals(OrderErrorCode.PICKUP_ALREADY_CONFIRMED,
                assertThrows(AppException.class,
                        () -> service.confirmPickup(order.getId(), 252L, qr, photo)).getErrorCode());
        verify(uploads, times(1)).uploadOrderImages(any());
        verify(orders, times(1)).save(order);
        assertEquals(1, order.getImages().size());
    }

    @Test void uploadFailureDoesNotChangeOrder() {
        String qr = token();
        lockOrder();
        when(uploads.uploadOrderImages(any())).thenThrow(new AppException(OrderErrorCode.FILE_UPLOAD_FAILED));
        assertEquals(OrderErrorCode.FILE_UPLOAD_FAILED,
                assertThrows(AppException.class,
                        () -> service.confirmPickup(order.getId(), 252L, qr, photo)).getErrorCode());
        assertEquals(OrderStatus.SHIPPER_ASSIGNED, order.getStatus());
        assertTrue(order.getImages().isEmpty());
        verify(orders, never()).save(any());
    }
}

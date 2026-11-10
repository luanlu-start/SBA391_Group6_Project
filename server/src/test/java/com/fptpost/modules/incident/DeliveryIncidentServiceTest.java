package com.fptpost.modules.incident;

import com.fptpost.common.exception.*;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.exception.FileErrorCode;
import com.fptpost.common.file.service.*;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.incident.dto.*;
import com.fptpost.modules.incident.exception.IncidentErrorCode;
import com.fptpost.modules.incident.model.*;
import com.fptpost.modules.incident.repository.*;
import com.fptpost.modules.incident.service.DeliveryIncidentService;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.*;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockMultipartFile;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All repositories, file storage and notification outbox are mocked; never connects to a database. */
@ExtendWith(MockitoExtension.class)
class DeliveryIncidentServiceTest {
    @Mock DeliveryIncidentRepository incidents;
    @Mock OrderRepository orders;
    @Mock IdentityUserAccess access;
    @Mock UserRepository users;
    @Mock IncidentRecipientRepository recipients;
    @Mock ImageValidator images;
    @Mock PrivateFileStorage storage;
    @Mock NotificationPublisher notifications;
    DeliveryIncidentService service;
    final UUID actorId = UUID.randomUUID(), senderId = UUID.randomUUID(), moderatorId = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-11T00:00:00Z");
    User courier;
    Order order;
    IncidentRequest request = new IncidentRequest(IncidentType.TRAFFIC_JAM, "  Kẹt xe tại điểm giao  ");

    @BeforeEach void setup() {
        service = new DeliveryIncidentService(incidents, orders, access, users, recipients, images, storage,
                notifications, Clock.fixed(now, ZoneOffset.UTC));
        courier = User.builder().id(29L).publicId(actorId).roles(Set.of(Role.builder().code("COURIER").build())).build();
        order = Order.builder().id(290L).orderCode("UC29-TEST").senderId(28L).courierId(29L)
                .status(OrderStatus.IN_DELIVERY).build();
    }

    void actor() { when(access.read(actorId)).thenReturn(courier); }
    void writable() { actor(); when(orders.findByIdForUpdate(290L)).thenReturn(Optional.of(order)); }
    void readable() { actor(); when(orders.findById(290L)).thenReturn(Optional.of(order)); }
    void saving() {
        when(incidents.save(any())).thenAnswer(call -> { DeliveryIncident i = call.getArgument(0); i.setId(1L); return i; });
        when(users.findById(28L)).thenReturn(Optional.of(User.builder().publicId(senderId).build()));
        when(recipients.findActiveModerators()).thenReturn(List.of(moderatorId));
    }
    void rejects(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertSame(expected, assertThrows(AppException.class, action).getErrorCode());
        verify(incidents, never()).save(any());
        verifyNoInteractions(storage, notifications);
    }

    @ParameterizedTest @EnumSource(IncidentType.class)
    void recordsEachTypeAndNotifiesPartiesWithoutChangingOrder(IncidentType type) {
        writable(); saving();
        var response = service.report(actorId, 290L, new IncidentRequest(type, request.description()), null);
        assertEquals(type, response.type()); assertEquals("Kẹt xe tại điểm giao", response.description());
        assertEquals(29L, response.reporterId()); assertEquals(now, response.createdAt());
        assertFalse(response.hasEvidence()); assertEquals(OrderStatus.IN_DELIVERY, order.getStatus());
        verify(orders, never()).save(any());
        for (var recipient : List.of(senderId, moderatorId))
            verify(notifications).publish(eq(recipient), eq(NotificationCategory.ORDER), eq("INCIDENT:1"),
                    anyString(), anyString(), eq("/orders/290/incidents"));
        verifyNoInteractions(images, storage);
    }

    @Test void persistsOptionalValidatedPhoto() {
        writable(); saving();
        var photo = new MockMultipartFile("photo", "test.png", "image/png", new byte[]{1});
        var key = UUID.randomUUID();
        when(images.validate(photo)).thenReturn(new ValidatedImage(new byte[]{2}, "image/png"));
        when(storage.put(new byte[]{2})).thenReturn(key);
        assertTrue(service.report(actorId, 290L, request, photo).hasEvidence());
        var saved = ArgumentCaptor.forClass(DeliveryIncident.class);
        verify(incidents).save(saved.capture());
        assertEquals(key, saved.getValue().getEvidenceKey());
        assertEquals("image/png", saved.getValue().getEvidenceMediaType());
    }

    @Test void nonCourierCannotReport() {
        actor(); courier.setRoles(Set.of(Role.builder().code("SENDER").build()));
        rejects(IncidentErrorCode.INCIDENT_ACCESS_DENIED, () -> service.report(actorId, 290L, request, null));
        verifyNoInteractions(orders);
    }
    @Test void otherCourierCannotReport() {
        writable(); order.setCourierId(999L);
        rejects(IncidentErrorCode.INCIDENT_ACCESS_DENIED, () -> service.report(actorId, 290L, request, null));
    }
    @Test void unassignedOrderRejected() {
        writable(); order.setCourierId(null);
        rejects(IncidentErrorCode.INCIDENT_ACCESS_DENIED, () -> service.report(actorId, 290L, request, null));
    }
    @Test void missingOrderRejected() {
        actor(); when(orders.findByIdForUpdate(290L)).thenReturn(Optional.empty());
        rejects(OrderErrorCode.ORDER_NOT_FOUND, () -> service.report(actorId, 290L, request, null));
    }
    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = "IN_DELIVERY", mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryNonDeliveryStatus(OrderStatus status) {
        writable(); order.setStatus(status);
        rejects(IncidentErrorCode.INCIDENT_INVALID_STATUS, () -> service.report(actorId, 290L, request, null));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\n\t"})
    void descriptionRequired(String description) {
        writable();
        rejects(GlobalErrorCode.INVALID_REQUEST, () -> service.report(actorId, 290L,
                new IncidentRequest(IncidentType.TRAFFIC_JAM, description), null));
    }
    @Test void longDescriptionRejected() {
        writable();
        rejects(GlobalErrorCode.INVALID_REQUEST, () -> service.report(actorId, 290L,
                new IncidentRequest(IncidentType.TRAFFIC_JAM, "x".repeat(1001)), null));
    }
    @Test void typeRequired() {
        writable(); rejects(GlobalErrorCode.INVALID_REQUEST, () -> service.report(actorId, 290L,
                new IncidentRequest(null, "test"), null));
    }
    @Test void invalidPhotoRejectedBeforeSaving() {
        writable(); var photo = new MockMultipartFile("photo", new byte[]{1});
        when(images.validate(photo)).thenThrow(new AppException(FileErrorCode.INVALID_IMAGE));
        rejects(FileErrorCode.INVALID_IMAGE, () -> service.report(actorId, 290L, request, photo));
    }
    @Test void unauthenticatedActorRejected() {
        when(access.read(actorId)).thenThrow(new AppException(GlobalErrorCode.UNAUTHORIZED));
        rejects(GlobalErrorCode.UNAUTHORIZED, () -> service.report(actorId, 290L, request, null));
    }
    @ParameterizedTest @ValueSource(strings = {"SENDER", "COURIER", "MODERATOR", "ADMIN"})
    void partiesAndStaffCanReadHistoryAfterDelivery(String role) {
        readable(); courier.setRoles(Set.of(Role.builder().code(role).build()));
        if (role.equals("SENDER")) courier.setId(28L);
        if (role.equals("MODERATOR") || role.equals("ADMIN")) courier.setId(999L);
        order.setStatus(OrderStatus.COMPLETED);
        when(incidents.findByOrderIdOrderByCreatedAtDescIdDesc(eq(290L), any()))
                .thenReturn(Page.empty());
        assertTrue(service.history(actorId, 290L, 0, 20).isEmpty());
    }
    @Test void unrelatedUserCannotReadHistoryOrEvidence() {
        readable(); courier.setId(999L);
        rejects(IncidentErrorCode.INCIDENT_ACCESS_DENIED, () -> service.history(actorId, 290L, 0, 20));
        rejects(IncidentErrorCode.INCIDENT_ACCESS_DENIED, () -> service.evidence(actorId, 290L, 1L));
        verifyNoInteractions(incidents);
    }
    @Test void rejectsInvalidPagination() {
        readable();
        for (int[] params : List.of(new int[]{-1, 20}, new int[]{0, 0}, new int[]{0, 51}))
            rejects(GlobalErrorCode.INVALID_REQUEST, () -> service.history(actorId, 290L, params[0], params[1]));
    }
    @Test void evidenceMustBelongToOrder() {
        readable(); when(incidents.findByIdAndOrderId(1L, 290L)).thenReturn(Optional.empty());
        rejects(IncidentErrorCode.INCIDENT_NOT_FOUND, () -> service.evidence(actorId, 290L, 1L));
    }
    @Test void readsPrivateEvidenceOnlyAfterAuthorization() {
        readable(); var key = UUID.randomUUID();
        when(incidents.findByIdAndOrderId(1L, 290L)).thenReturn(Optional.of(DeliveryIncident.builder()
                .evidenceKey(key).evidenceMediaType("image/png").build()));
        when(storage.read(key)).thenReturn(new byte[]{1, 2});
        var image = service.evidence(actorId, 290L, 1L);
        assertArrayEquals(new byte[]{1, 2}, image.bytes()); assertEquals("image/png", image.mediaType());
    }
}

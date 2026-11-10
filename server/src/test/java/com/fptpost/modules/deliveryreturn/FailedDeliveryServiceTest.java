package com.fptpost.modules.deliveryreturn;

import com.fptpost.common.exception.*;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.*;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.deliveryreturn.dto.*;
import com.fptpost.modules.deliveryreturn.exception.*;
import com.fptpost.modules.deliveryreturn.model.*;
import com.fptpost.modules.deliveryreturn.repository.*;
import com.fptpost.modules.deliveryreturn.service.FailedDeliveryService;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.fptpost.modules.deliveryreturn.exception.DeliveryReturnErrorCode.*;

/** Pure mock tests; never runs the application, contacts real users, or connects to SQL Server. */
@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class FailedDeliveryServiceTest {
    @Mock OrderRepository orders;
    @Mock DeliveryReturnRepository cases;
    @Mock DeliveryContactAttemptRepository contacts;
    @Mock DeliveryReturnHistoryRepository history;
    @Mock IdentityUserAccess access;
    @Mock UserRepository users;
    @Mock ParameterService parameters;
    @Mock ImageValidator images;
    @Mock PrivateFileStorage storage;
    @Mock NotificationPublisher notifications;
    @Mock Clock clock;
    FailedDeliveryService service;
    UUID actorId = UUID.randomUUID();
    User actor;
    Order order;
    DeliveryReturnCase active;
    List<DeliveryContactAttempt> calls;
    List<DeliveryReturnHistory> events;
    AtomicReference<Instant> now;
    MockMultipartFile photo = new MockMultipartFile("photo", "calls.png", "image/png", new byte[]{1});
    ReturnRequests.Failure failure = new ReturnRequests.Failure(FailureReason.RECIPIENT_UNREACHABLE, "Không nghe máy");

    @BeforeEach void setup() {
        now = new AtomicReference<>(Instant.parse("2026-11-11T01:00:00Z"));
        when(clock.instant()).thenAnswer(i -> now.get());
        actor = user(302L, "COURIER");
        order = Order.builder().id(300L).orderCode("UC30-TEST").senderId(301L).courierId(302L)
                .priceAgreed(BigDecimal.valueOf(40000)).priceSuggested(BigDecimal.valueOf(30000)).status(OrderStatus.IN_DELIVERY).build();
        calls = new ArrayList<>(); events = new ArrayList<>();
        service = new FailedDeliveryService(orders, cases, contacts, history, access, users, parameters, images, storage, notifications, clock);
        when(access.read(actorId)).thenAnswer(i -> actor);
        when(orders.findByIdForUpdate(300L)).thenReturn(Optional.of(order));
        when(orders.findById(300L)).thenReturn(Optional.of(order));
        when(parameters.getSnapshot()).thenReturn(snapshot("3 lần gọi trong 30 phút", "0.5"));
        when(contacts.findByOrderIdAndCourierIdAndReturnCaseIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(anyLong(), anyLong(), any()))
                .thenAnswer(i -> calls.stream().filter(c -> c.getReturnCase() == null && !c.getCreatedAt().isBefore(i.getArgument(2, Instant.class))).toList());
        when(contacts.findByOrderIdAndRequestId(anyLong(), any())).thenAnswer(i -> calls.stream().filter(c -> c.getRequestId().equals(i.getArgument(1))).findFirst());
        when(contacts.save(any())).thenAnswer(i -> { var c = i.getArgument(0, DeliveryContactAttempt.class); c.setId((long) calls.size() + 1); calls.add(c); return c; });
        when(cases.save(any())).thenAnswer(i -> { active = i.getArgument(0); active.setId(1L); return active; });
        when(cases.findTopByOrderIdOrderByIdDesc(300L)).thenAnswer(i -> Optional.ofNullable(active));
        when(cases.findByIdAndOrderId(1L, 300L)).thenAnswer(i -> Optional.ofNullable(active));
        when(history.save(any())).thenAnswer(i -> { var e = i.getArgument(0, DeliveryReturnHistory.class); e.setId((long) events.size() + 1); events.add(e); return e; });
        when(history.findTop50ByOrderIdOrderByIdDesc(300L)).thenAnswer(i -> events.reversed());
        when(images.validate(photo)).thenReturn(new ValidatedImage(new byte[]{1}, "image/png"));
        when(storage.put(any())).thenReturn(UUID.randomUUID());
        when(users.findById(301L)).thenReturn(Optional.of(user(301L, "SENDER")));
        when(users.findById(302L)).thenReturn(Optional.of(user(302L, "COURIER")));
    }
    static User user(long id, String role) { return User.builder().id(id).publicId(UUID.randomUUID()).status(UserStatus.ACTIVE)
            .roles(Set.of(Role.builder().code(role).build())).build(); }
    static ParameterSnapshot snapshot(String contact, String rate) { return new ParameterSnapshot(
            Map.of("CONTACT_TRIES", contact, "RETURN_FEE", rate), Map.of("CONTACT_TRIES", 1L, "RETURN_FEE", 2L), Instant.EPOCH); }
    void enoughCalls() { for (int minute : List.of(25, 15, 5)) calls.add(DeliveryContactAttempt.builder().id((long) minute).orderId(300L).courierId(302L)
            .requestId(UUID.randomUUID()).note("Không nghe máy").createdAt(now.get().minusSeconds(minute * 60L)).build()); }
    void startReturn() { enoughCalls(); service.report(actorId, 300L, failure, photo); }
    void sender() { actor = user(301L, "SENDER"); }
    void rejects(com.fptpost.common.exception.ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isSameAs(code));
    }

    @Test void contactIsTimestampedByServerAndRequestReplayDoesNotCountTwice() {
        UUID requestId = UUID.randomUUID(); var request = new ReturnRequests.Contact(requestId, " Không nghe máy ");
        service.contact(actorId, 300L, request); service.contact(actorId, 300L, request);
        assertThat(calls).hasSize(1); assertThat(calls.getFirst().getCreatedAt()).isEqualTo(now.get());
        assertThat(calls.getFirst().getNote()).isEqualTo("Không nghe máy"); assertThat(events).hasSize(1);
    }
    @Test void reportSnapshotsFeesConsumesProofAndRecordsBothTransitions() {
        startReturn();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNING);
        assertThat(active.getReturnFee()).isEqualByComparingTo("20000"); assertThat(active.getDeliveryFee()).isEqualByComparingTo("40000");
        assertThat(active.getFeePolicyVersion()).isEqualTo(2L);
        assertThat(calls).allMatch(c -> c.getReturnCase() == active);
        assertThat(events).extracting(DeliveryReturnHistory::getToStatus).containsExactly(OrderStatus.DELIVERY_FAILED, OrderStatus.RETURNING);
        verify(notifications, times(2)).publish(any(), any(), anyString(), anyString(), anyString(), eq("/orders/300/return"));
        assertThat(service.detail(actorId, 300L).latestCase().financialSettlementPending()).isTrue();
    }
    @Test void fallbackUsesSuggestedFeeAndRoundsVnd() {
        enoughCalls(); order.setPriceAgreed(null); order.setPriceSuggested(new BigDecimal("30001"));
        service.report(actorId, 300L, failure, photo); assertThat(active.getReturnFee()).isEqualByComparingTo("15001");
    }
    @Test void policyValuesAreReadRatherThanHardcoded() {
        when(parameters.getSnapshot()).thenReturn(snapshot("2 lần gọi trong 10 phút", "0.25"));
        enoughCalls(); calls.get(1).setCreatedAt(now.get().minusSeconds(9 * 60L)); service.report(actorId, 300L, failure, photo);
        assertThat(active.getReturnFee()).isEqualByComparingTo("10000"); assertThat(active.getRequiredCalls()).isEqualTo(2);
    }
    @Test void insufficientCallsOrExpiredCallsCannotFailDelivery() {
        rejects(RETURN_CONTACT_REQUIRED, () -> service.report(actorId, 300L, failure, photo));
        enoughCalls(); calls.forEach(c -> c.setCreatedAt(now.get().minusSeconds(1801)));
        rejects(RETURN_CONTACT_REQUIRED, () -> service.report(actorId, 300L, failure, photo)); verifyNoInteractions(storage);
    }
    @Test void imageRequiredAndValidatedBeforeStateChange() {
        enoughCalls(); rejects(RETURN_EVIDENCE_REQUIRED, () -> service.report(actorId, 300L, failure, null));
        when(images.validate(photo)).thenThrow(new AppException(GlobalErrorCode.INVALID_REQUEST));
        rejects(GlobalErrorCode.INVALID_REQUEST, () -> service.report(actorId, 300L, failure, photo));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY); verify(cases, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings = {"SENDER", "MODERATOR", "ADMIN"})
    void nonCourierCannotReport(String role) { actor = user(302L, role); rejects(RETURN_ACCESS_DENIED, () -> service.report(actorId, 300L, failure, photo)); }
    @Test void wrongCourierRejected() { actor = user(999L, "COURIER"); rejects(RETURN_ACCESS_DENIED, () -> service.report(actorId, 300L, failure, photo)); }
    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = "IN_DELIVERY", mode = EnumSource.Mode.EXCLUDE)
    void otherStatusesCannotReport(OrderStatus status) { order.setStatus(status); rejects(RETURN_INVALID_STATUS, () -> service.report(actorId, 300L, failure, photo)); }
    @Test void duplicateReportDoesNotUploadOrNotifyAgain() {
        startReturn(); rejects(RETURN_INVALID_STATUS, () -> service.report(actorId, 300L, failure, photo));
        verify(storage, times(1)).put(any()); verify(cases, times(1)).save(any());
    }
    @Test void missingOrderAndInvalidDataRejected() {
        when(orders.findByIdForUpdate(300L)).thenReturn(Optional.empty());
        rejects(OrderErrorCode.ORDER_NOT_FOUND, () -> service.report(actorId, 300L, failure, photo));
        when(orders.findByIdForUpdate(300L)).thenReturn(Optional.of(order));
        rejects(GlobalErrorCode.INVALID_REQUEST, () -> service.report(actorId, 300L, new ReturnRequests.Failure(null, "test"), photo));
        rejects(GlobalErrorCode.INVALID_REQUEST, () -> service.contact(actorId, 300L, new ReturnRequests.Contact(UUID.randomUUID(), " ")));
    }
    @Test void resumeInvalidatesCodeAndConsumedCallsCannotBeReused() {
        startReturn(); sender(); service.issueCode(actorId, 300L); actor = user(302L, "COURIER");
        service.resume(actorId, 300L, new ReturnRequests.Resume("Người nhận đã gọi lại"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_DELIVERY); assertThat(active.getState()).isEqualTo(ReturnState.RESUMED);
        assertThat(active.getCodeHash()).isNull();
        rejects(RETURN_CONTACT_REQUIRED, () -> service.report(actorId, 300L, failure, photo));
    }
    @Test void senderConfirmsOnceWithOwnedOneTimeCode() {
        startReturn(); sender(); var issued = service.issueCode(actorId, 300L);
        assertThat(active.getCodeHash()).isNotEqualTo(issued.code());
        var response = service.confirm(actorId, 300L, new ReturnRequests.Confirm(issued.code(), true));
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.RETURNED); assertThat(order.getEndedAt()).isEqualTo(now.get());
        assertThat(active.getState()).isEqualTo(ReturnState.RETURNED); assertThat(active.getCodeHash()).isNull();
        rejects(RETURN_INVALID_STATUS, () -> service.confirm(actorId, 300L, new ReturnRequests.Confirm(issued.code(), true)));
        actor = user(302L, "COURIER"); rejects(RETURN_INVALID_STATUS, () -> service.resume(actorId, 300L, new ReturnRequests.Resume("late")));
    }
    @Test void wrongSenderOrCourierCannotConfirmOrIssueCode() {
        startReturn(); rejects(RETURN_ACCESS_DENIED, () -> service.issueCode(actorId, 300L));
        actor = user(999L, "SENDER"); rejects(RETURN_ACCESS_DENIED, () -> service.issueCode(actorId, 300L));
        rejects(RETURN_ACCESS_DENIED, () -> service.confirm(actorId, 300L, new ReturnRequests.Confirm("123456", true)));
    }
    @Test void fiveWrongCodesLockChallengeWithoutChangingOrder() {
        startReturn(); sender(); var code = service.issueCode(actorId, 300L).code();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) rejects(RETURN_CODE_INVALID, () -> service.confirm(actorId, 300L, new ReturnRequests.Confirm(wrong, true)));
        assertThat(active.getCodeFailures()).isEqualTo(5); assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNING);
        rejects(RETURN_CODE_LOCKED, () -> service.confirm(actorId, 300L, new ReturnRequests.Confirm(code, true)));
    }
    @Test void expiryCooldownAndRotationAreEnforced() {
        startReturn(); sender(); var old = service.issueCode(actorId, 300L);
        rejects(RETURN_CODE_COOLDOWN, () -> service.issueCode(actorId, 300L));
        now.set(now.get().plusSeconds(600));
        rejects(RETURN_CODE_EXPIRED, () -> service.confirm(actorId, 300L, new ReturnRequests.Confirm(old.code(), true)));
        var fresh = service.issueCode(actorId, 300L);
        assertThat(active.getCodeFailures()).isZero();
        service.confirm(actorId, 300L, new ReturnRequests.Confirm(fresh.code(), true));
    }
    @Test void explicitPhysicalReceiptAcknowledgementRequired() {
        startReturn(); sender(); var issued = service.issueCode(actorId, 300L);
        rejects(GlobalErrorCode.INVALID_REQUEST, () -> service.confirm(actorId, 300L, new ReturnRequests.Confirm(issued.code(), false)));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURNING);
    }
    @Test void invalidPolicyFailsClosed() {
        when(parameters.getSnapshot()).thenReturn(snapshot("invalid", "0.5"));
        rejects(RETURN_POLICY_UNAVAILABLE, () -> service.detail(actorId, 300L));
    }
    @Test void unrelatedUserCannotReadAndStaffCannotMutate() {
        actor = user(999L, "SENDER"); rejects(RETURN_ACCESS_DENIED, () -> service.detail(actorId, 300L));
        actor = user(999L, "MODERATOR"); assertThat(service.detail(actorId, 300L).canReport()).isFalse();
        rejects(RETURN_ACCESS_DENIED, () -> service.resume(actorId, 300L, new ReturnRequests.Resume("test")));
    }
    @Test void assignedCourierCanOpenReturnPageBeforeAnyReturnCaseExists() {
        var response = service.detail(actorId, 300L);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
        assertThat(response.latestCase()).isNull();
        assertThat(response.contacts()).isEmpty();
        assertThat(response.history()).isEmpty();
        assertThat(response.canReport()).isTrue();
        verifyNoInteractions(storage, notifications);
    }
    @Test void senderCanOpenEmptyReturnHistoryWithoutBeingAllowedToReport() {
        sender();
        var response = service.detail(actorId, 300L);
        assertThat(response.latestCase()).isNull();
        assertThat(response.canReport()).isFalse();
        assertThat(response.canConfirm()).isFalse();
        assertThat(response.canResume()).isFalse();
    }
    @Test void privateEvidenceChecksOrderAndMembership() {
        startReturn(); when(storage.read(active.getEvidenceKey())).thenReturn(new byte[]{1});
        assertThat(service.evidence(actorId, 300L, 1L).mediaType()).isEqualTo("image/png");
        actor = user(999L, "COURIER"); rejects(RETURN_ACCESS_DENIED, () -> service.evidence(actorId, 300L, 1L));
    }
}

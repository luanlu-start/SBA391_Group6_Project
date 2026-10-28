package com.fptpost;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.OfferCounterRequest;
import com.fptpost.modules.order.dto.request.OfferCreateRequest;
import com.fptpost.modules.order.dto.response.OfferAcceptResponse;
import com.fptpost.modules.order.dto.response.OfferResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Offer;
import com.fptpost.modules.order.model.OfferStatus;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OfferRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.OfferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OfferServiceTest {

    private OrderRepository orderRepository;
    private OfferRepository offerRepository;
    private UserRepository userRepository;
    private UserProfileRepository userProfileRepository;
    private CourierProfileRepository courierProfileRepository;
    private OfferService offerService;

    private User courierUser;
    private User senderUser;
    private Order sampleOrder;
    private CourierProfile courierProfile;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        offerRepository = mock(OfferRepository.class);
        userRepository = mock(UserRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        courierProfileRepository = mock(CourierProfileRepository.class);

        offerService = new OfferService(
                orderRepository,
                offerRepository,
                userRepository,
                userProfileRepository,
                courierProfileRepository
        );

        Role courierRole = Role.builder().id(2L).code("COURIER").build();
        courierUser = User.builder()
                .id(20L)
                .email("courier@fptpost.vn")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(courierRole))
                .build();

        Role senderRole = Role.builder().id(1L).code("SENDER").build();
        senderUser = User.builder()
                .id(10L)
                .email("sender@fptpost.vn")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(senderRole))
                .build();

        sampleOrder = Order.builder()
                .id(100L)
                .orderCode("FP100")
                .senderId(10L)
                .status(OrderStatus.FINDING_SHIPPER)
                .priceSuggested(new BigDecimal("50000"))
                .declaredValue(new BigDecimal("200000"))
                .pickupAddress("Da Nang")
                .deliveryAddress("Hue")
                .build();

        courierProfile = CourierProfile.builder()
                .userId(20L)
                .user(courierUser)
                .tier(CourierTier.SILVER)
                .trustScore(new BigDecimal("85"))
                .completedOrders(25)
                .commitmentVersion("1.0")
                .registeredAt(java.time.Instant.now())
                .vehicleType(com.fptpost.modules.identity.enums.VehicleType.MOTORBIKE)
                .build();

        when(courierProfileRepository.findByUserId(20L)).thenReturn(Optional.of(courierProfile));
    }

    // ==========================================
    // UC-18 Tests: createOffer
    // ==========================================

    @Test
    void createOffer_shouldSucceed_whenValidRequest() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        OfferCreateRequest request = OfferCreateRequest.builder()
                .price(new BigDecimal("55000"))
                .message("Em tiện đường giao luôn ạ")
                .etaPickup(Instant.now().plusSeconds(1800))
                .build();

        OfferResponse response = offerService.createOffer("100", request, courierUser);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getPrice()).isEqualTo(new BigDecimal("55000"));
        assertThat(response.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(response.getRoundNo()).isEqualTo(1);
        verify(offerRepository).save(any(Offer.class));
    }

    @Test
    void createOffer_shouldThrowException_whenSenderSelfOffers() {
        sampleOrder.setSenderId(20L); // courier is also sender
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));

        OfferCreateRequest request = OfferCreateRequest.builder()
                .price(new BigDecimal("50000"))
                .build();

        assertThatThrownBy(() -> offerService.createOffer("100", request, courierUser))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.OFFER_SELF_FORBIDDEN));
    }

    @Test
    void createOffer_shouldThrowException_whenPriceBelow70Percent() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));

        // 50,000 * 0.7 = 35,000 -> 30,000 is too low
        OfferCreateRequest request = OfferCreateRequest.builder()
                .price(new BigDecimal("30000"))
                .build();

        assertThatThrownBy(() -> offerService.createOffer("100", request, courierUser))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.OFFER_PRICE_OUT_OF_RANGE));
    }

    @Test
    void createOffer_shouldThrowException_whenPriceAbove150Percent() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));

        // 50,000 * 1.5 = 75,000 -> 80,000 is too high
        OfferCreateRequest request = OfferCreateRequest.builder()
                .price(new BigDecimal("80000"))
                .build();

        assertThatThrownBy(() -> offerService.createOffer("100", request, courierUser))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.OFFER_PRICE_OUT_OF_RANGE));
    }

    @Test
    void createOffer_shouldThrowException_whenHighValueOrderAndLowTier() {
        sampleOrder.setDeclaredValue(new BigDecimal("1500000")); // > 1,000,000
        courierProfile.setTier(CourierTier.BRONZE); // Bronze cannot accept high value

        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));

        OfferCreateRequest request = OfferCreateRequest.builder()
                .price(new BigDecimal("50000"))
                .build();

        assertThatThrownBy(() -> offerService.createOffer("100", request, courierUser))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.COURIER_HIGH_VALUE_RESTRICTED));
    }

    @Test
    void createOffer_shouldWithdrawOldOffer_whenCourierAlreadyHadOffer() {
        Offer oldOffer = Offer.builder()
                .id(50L)
                .order(sampleOrder)
                .courier(courierUser)
                .status(OfferStatus.PENDING)
                .price(new BigDecimal("45000"))
                .build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(offerRepository.findByOrderIdAndCourierIdAndStatusIn(eq(100L), eq(20L), any()))
                .thenReturn(List.of(oldOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OfferCreateRequest request = OfferCreateRequest.builder()
                .price(new BigDecimal("52000"))
                .build();

        offerService.createOffer("100", request, courierUser);

        assertThat(oldOffer.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    // ==========================================
    // UC-18 Tests: withdrawOffer
    // ==========================================

    @Test
    void withdrawOffer_shouldSucceed_whenOwner() {
        Offer offer = Offer.builder()
                .id(1L)
                .order(sampleOrder)
                .courier(courierUser)
                .status(OfferStatus.PENDING)
                .price(new BigDecimal("48000"))
                .build();

        when(offerRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponse response = offerService.withdrawOffer(1L, courierUser);

        assertThat(response.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    @Test
    void withdrawOffer_shouldThrowException_whenNotOwner() {
        User otherCourier = User.builder().id(99L).build();
        Offer offer = Offer.builder()
                .id(1L)
                .order(sampleOrder)
                .courier(otherCourier)
                .status(OfferStatus.PENDING)
                .build();

        when(offerRepository.findById(1L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerService.withdrawOffer(1L, courierUser))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.OFFER_FORBIDDEN));
    }

    // ==========================================
    // UC-19 Tests: acceptOffer
    // ==========================================

    @Test
    void acceptOffer_shouldSucceed_whenSenderAccepts() {
        Offer offer = Offer.builder()
                .id(1L)
                .order(sampleOrder)
                .courier(courierUser)
                .status(OfferStatus.PENDING)
                .price(new BigDecimal("48000"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Offer otherOffer = Offer.builder()
                .id(2L)
                .order(sampleOrder)
                .courier(User.builder().id(30L).build())
                .status(OfferStatus.PENDING)
                .price(new BigDecimal("52000"))
                .build();

        when(offerRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(offerRepository.findByOrderIdAndStatusInOrderByCreatedAtDesc(eq(100L), any()))
                .thenReturn(List.of(offer, otherOffer));

        OfferAcceptResponse response = offerService.acceptOffer(1L, senderUser);

        assertThat(response).isNotNull();
        assertThat(response.getOfferId()).isEqualTo(1L);
        assertThat(response.getCourierId()).isEqualTo(20L);
        assertThat(response.getPriceAgreed()).isEqualTo(new BigDecimal("48000"));
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

        // Verification of state mutations (BR-R-031, BR-R-055)
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(otherOffer.getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(sampleOrder.getCourierId()).isEqualTo(20L);
        assertThat(sampleOrder.getPriceAgreed()).isEqualTo(new BigDecimal("48000"));
        assertThat(sampleOrder.getFeeRateLocked()).isEqualTo(new BigDecimal("0.06")); // Silver tier 6%
    }

    @Test
    void acceptOffer_shouldThrowException_whenCallerNotSender() {
        Offer offer = Offer.builder()
                .id(1L)
                .order(sampleOrder)
                .courier(courierUser)
                .status(OfferStatus.PENDING)
                .build();

        when(offerRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleOrder));

        User stranger = User.builder().id(999L).build();

        assertThatThrownBy(() -> offerService.acceptOffer(1L, stranger))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.OFFER_FORBIDDEN));
    }

    // ==========================================
    // UC-19 Tests: rejectOffer
    // ==========================================

    @Test
    void rejectOffer_shouldSucceed_whenSenderRejects() {
        Offer offer = Offer.builder()
                .id(1L)
                .order(sampleOrder)
                .courier(courierUser)
                .status(OfferStatus.PENDING)
                .price(new BigDecimal("50000"))
                .build();

        when(offerRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponse response = offerService.rejectOffer(1L, senderUser);

        assertThat(response.getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.REJECTED);
    }

    // ==========================================
    // UC-19 Tests: counterOffer
    // ==========================================

    @Test
    void counterOffer_shouldSucceed_whenValidSenderCounter() {
        Offer offer = Offer.builder()
                .id(1L)
                .order(sampleOrder)
                .courier(courierUser)
                .status(OfferStatus.PENDING)
                .roundNo(1)
                .createdBySender(false)
                .price(new BigDecimal("60000"))
                .build();

        when(offerRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer o = invocation.getArgument(0);
            if (o.getId() == null) o.setId(2L);
            return o;
        });

        OfferCounterRequest request = OfferCounterRequest.builder()
                .price(new BigDecimal("45000"))
                .message("45k được không bạn?")
                .build();

        OfferResponse response = offerService.counterOffer(1L, request, senderUser);

        assertThat(response).isNotNull();
        assertThat(response.getRoundNo()).isEqualTo(2);
        assertThat(response.getPrice()).isEqualTo(new BigDecimal("45000"));
        assertThat(response.isCreatedBySender()).isTrue();
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.COUNTERED);
    }

    @Test
    void counterOffer_shouldThrowException_whenExceeded3Rounds() {
        Offer offer = Offer.builder()
                .id(1L)
                .order(sampleOrder)
                .courier(courierUser)
                .status(OfferStatus.PENDING)
                .roundNo(3) // Round 3 already!
                .createdBySender(false)
                .price(new BigDecimal("50000"))
                .build();

        when(offerRepository.findById(1L)).thenReturn(Optional.of(offer));

        OfferCounterRequest request = OfferCounterRequest.builder()
                .price(new BigDecimal("45000"))
                .build();

        assertThatThrownBy(() -> offerService.counterOffer(1L, request, senderUser))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(OrderErrorCode.OFFER_EXCEEDED_ROUNDS));
    }
}

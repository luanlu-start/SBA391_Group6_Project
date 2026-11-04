package com.fptpost;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.enums.CourierTier;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.enums.VehicleType;
import com.fptpost.modules.identity.model.CourierProfile;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.CourierProfileRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.InvitationCreateRequest;
import com.fptpost.modules.order.dto.response.FavoriteCourierResponse;
import com.fptpost.modules.order.dto.response.InvitationResponse;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.*;
import com.fptpost.modules.order.repository.FavoriteCourierRepository;
import com.fptpost.modules.order.repository.InvitationRepository;
import com.fptpost.modules.order.repository.OfferRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.InvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvitationServiceTest {

    private InvitationRepository invitationRepository;
    private FavoriteCourierRepository favoriteCourierRepository;
    private OrderRepository orderRepository;
    private OfferRepository offerRepository;
    private UserRepository userRepository;
    private UserProfileRepository userProfileRepository;
    private CourierProfileRepository courierProfileRepository;
    private InvitationService invitationService;

    private User senderUser;
    private User courierUser;
    private Order sampleOrder;
    private CourierProfile courierProfile;

    @BeforeEach
    void setUp() {
        invitationRepository = mock(InvitationRepository.class);
        favoriteCourierRepository = mock(FavoriteCourierRepository.class);
        orderRepository = mock(OrderRepository.class);
        offerRepository = mock(OfferRepository.class);
        userRepository = mock(UserRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        courierProfileRepository = mock(CourierProfileRepository.class);

        invitationService = new InvitationService(
                invitationRepository,
                favoriteCourierRepository,
                orderRepository,
                offerRepository,
                userRepository,
                userProfileRepository,
                courierProfileRepository
        );

        senderUser = User.builder()
                .id(1L)
                .publicId(UUID.randomUUID())
                .email("sender@fptpost.vn")
                .phone("+84901234567")
                .status(UserStatus.ACTIVE)
                .build();

        Role courierRole = new Role();
        courierRole.setId(2L);
        courierRole.setCode("COURIER");

        courierUser = User.builder()
                .id(2L)
                .publicId(UUID.randomUUID())
                .email("courier@fptpost.vn")
                .phone("+84902345678")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(courierRole))
                .build();

        sampleOrder = Order.builder()
                .id(100L)
                .orderCode("FPT-261014-000100")
                .senderId(senderUser.getId())
                .status(OrderStatus.FINDING_SHIPPER)
                .productName("Hồ sơ tài liệu")
                .weightKg(new BigDecimal("1.5"))
                .pickupAddress("Cổng Đại học FPT, Khu CNC Hòa Lạc")
                .deliveryAddress("Tòa nhà FPT Cầu Giấy, Hà Nội")
                .declaredValue(new BigDecimal("200000"))
                .priceSuggested(new BigDecimal("50000"))
                .build();

        courierProfile = CourierProfile.builder()
                .userId(courierUser.getId())
                .user(courierUser)
                .tier(CourierTier.SILVER)
                .trustScore(new BigDecimal("85"))
                .completedOrders(25)
                .vehicleType(VehicleType.MOTORBIKE)
                .build();
    }

    @Test
    void testInviteCourier_Success() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(2L)).thenReturn(Optional.of(courierUser));
        when(courierProfileRepository.findByUserId(2L)).thenReturn(Optional.of(courierProfile));
        when(invitationRepository.existsByOrderIdAndCourierIdAndStatus(100L, 2L, InvitationStatus.PENDING)).thenReturn(false);
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> {
            Invitation inv = invocation.getArgument(0);
            inv.setId(501L);
            inv.setCreatedAt(Instant.now());
            inv.setUpdatedAt(Instant.now());
            return inv;
        });

        InvitationCreateRequest req = InvitationCreateRequest.builder()
                .courierId(2L)
                .message("Nhờ bạn giao giúp mình kiện này")
                .expiryHours(24)
                .build();

        InvitationResponse response = invitationService.inviteCourier("100", req, senderUser);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(501L);
        assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(response.getOrderId()).isEqualTo(100L);
        assertThat(response.getCourierId()).isEqualTo(2L);
        verify(invitationRepository, times(1)).save(any(Invitation.class));
    }

    @Test
    void testInviteCourier_SelfInvite_ThrowsException() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));

        InvitationCreateRequest req = InvitationCreateRequest.builder()
                .courierId(1L)
                .build();

        assertThatThrownBy(() -> invitationService.inviteCourier("100", req, senderUser))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(OrderErrorCode.INVITATION_SELF_FORBIDDEN.getCode());
    }

    @Test
    void testInviteCourier_AlreadyPending_ThrowsException() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(2L)).thenReturn(Optional.of(courierUser));
        when(courierProfileRepository.findByUserId(2L)).thenReturn(Optional.of(courierProfile));
        when(invitationRepository.existsByOrderIdAndCourierIdAndStatus(100L, 2L, InvitationStatus.PENDING)).thenReturn(true);

        InvitationCreateRequest req = InvitationCreateRequest.builder()
                .courierId(2L)
                .build();

        assertThatThrownBy(() -> invitationService.inviteCourier("100", req, senderUser))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(OrderErrorCode.INVITATION_ALREADY_EXISTS.getCode());
    }

    @Test
    void testInviteCourier_HighValueRestricted_ThrowsException() {
        sampleOrder.setDeclaredValue(new BigDecimal("2500000")); // > 1.000.000
        courierProfile.setTier(CourierTier.BRONZE); // Hạng Đồng không được nhận đơn > 1M

        when(orderRepository.findById(100L)).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(2L)).thenReturn(Optional.of(courierUser));
        when(courierProfileRepository.findByUserId(2L)).thenReturn(Optional.of(courierProfile));
        when(invitationRepository.existsByOrderIdAndCourierIdAndStatus(100L, 2L, InvitationStatus.PENDING)).thenReturn(false);

        InvitationCreateRequest req = InvitationCreateRequest.builder()
                .courierId(2L)
                .build();

        assertThatThrownBy(() -> invitationService.inviteCourier("100", req, senderUser))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(OrderErrorCode.COURIER_HIGH_VALUE_RESTRICTED.getCode());
    }

    @Test
    void testAcceptInvitation_Success() {
        Invitation invitation = Invitation.builder()
                .id(501L)
                .order(sampleOrder)
                .sender(senderUser)
                .courier(courierUser)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(invitationRepository.findById(501L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        InvitationResponse response = invitationService.acceptInvitation(501L, courierUser);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(sampleOrder.getCourierId()).isEqualTo(courierUser.getId());
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        verify(orderRepository, times(1)).save(sampleOrder);
    }

    @Test
    void testAcceptInvitation_Expired_ThrowsException() {
        Invitation invitation = Invitation.builder()
                .id(501L)
                .order(sampleOrder)
                .sender(senderUser)
                .courier(courierUser)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(100)) // Đã hết hạn
                .createdAt(Instant.now().minusSeconds(2000))
                .updatedAt(Instant.now().minusSeconds(2000))
                .build();

        when(invitationRepository.findById(501L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.acceptInvitation(501L, courierUser))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(OrderErrorCode.INVITATION_EXPIRED.getCode());

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void testRejectInvitation_Success() {
        Invitation invitation = Invitation.builder()
                .id(501L)
                .order(sampleOrder)
                .sender(senderUser)
                .courier(courierUser)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(invitationRepository.findById(501L)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));

        InvitationResponse response = invitationService.rejectInvitation(501L, courierUser, "Bận việc đột xuất");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(InvitationStatus.REJECTED);
        verify(invitationRepository, times(1)).save(invitation);
    }

    @Test
    void testToggleFavorite_AddAndRemove() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(courierUser));
        when(favoriteCourierRepository.findBySenderIdAndCourierId(1L, 2L)).thenReturn(Optional.empty());
        when(favoriteCourierRepository.save(any(FavoriteCourier.class))).thenAnswer(i -> {
            FavoriteCourier fav = i.getArgument(0);
            fav.setId(99L);
            fav.setCreatedAt(Instant.now());
            return fav;
        });

        // Add to favorite
        FavoriteCourierResponse added = invitationService.toggleFavorite(2L, "Tài xế quen", senderUser);
        assertThat(added).isNotNull();
        assertThat(added.getCourierId()).isEqualTo(2L);

        // Remove from favorite
        FavoriteCourier existingFav = FavoriteCourier.builder().id(99L).sender(senderUser).courier(courierUser).build();
        when(favoriteCourierRepository.findBySenderIdAndCourierId(1L, 2L)).thenReturn(Optional.of(existingFav));

        FavoriteCourierResponse removed = invitationService.toggleFavorite(2L, null, senderUser);
        assertThat(removed).isNull();
        verify(favoriteCourierRepository, times(1)).delete(existingFav);
    }
}

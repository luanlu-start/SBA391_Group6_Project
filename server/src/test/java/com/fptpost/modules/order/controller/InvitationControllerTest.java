package com.fptpost.modules.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.request.InvitationCreateRequest;
import com.fptpost.modules.order.dto.response.FavoriteCourierResponse;
import com.fptpost.modules.order.dto.response.InvitationResponse;
import com.fptpost.modules.order.model.InvitationStatus;
import com.fptpost.modules.order.service.IInvitationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationController Web Layer Tests (UC-20, API-051, API-052)")
class InvitationControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private IInvitationService invitationService;

    @Mock
    private UserRepository userRepository;

    private User sampleUser;
    private UUID samplePublicId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InvitationController(invitationService, userRepository))
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();

        samplePublicId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(1L)
                .publicId(samplePublicId)
                .email("sender@fptpost.vn")
                .phone("+84901234567")
                .build();

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject(samplePublicId.toString())
                .claim("email", "sender@fptpost.vn")
                .build();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testInviteCourier_Success() throws Exception {
        when(userRepository.findByPublicId(samplePublicId)).thenReturn(Optional.of(sampleUser));

        InvitationResponse response = InvitationResponse.builder()
                .id(101L)
                .orderId(1L)
                .orderCode("FPT-261014-000001")
                .courierId(2L)
                .courierName("Trần Văn Shipper")
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        when(invitationService.inviteCourier(eq("1"), any(InvitationCreateRequest.class), any(User.class)))
                .thenReturn(response);

        InvitationCreateRequest request = InvitationCreateRequest.builder()
                .courierId(2L)
                .message("Nhờ bạn giao giúp")
                .expiryHours(24)
                .build();

        mockMvc.perform(post("/api/v1/orders/1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.orderCode").value("FPT-261014-000001"))
                .andExpect(jsonPath("$.data.courierName").value("Trần Văn Shipper"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void testGetOrderInvitations_Success() throws Exception {
        when(userRepository.findByPublicId(samplePublicId)).thenReturn(Optional.of(sampleUser));

        InvitationResponse response = InvitationResponse.builder()
                .id(101L)
                .orderId(1L)
                .orderCode("FPT-261014-000001")
                .courierId(2L)
                .courierName("Trần Văn Shipper")
                .status(InvitationStatus.PENDING)
                .build();

        when(invitationService.getInvitationsForOrder(eq("1"), any(User.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/orders/1/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(101));
    }

    @Test
    void testGetMyInvitations_Success() throws Exception {
        when(userRepository.findByPublicId(samplePublicId)).thenReturn(Optional.of(sampleUser));

        InvitationResponse response = InvitationResponse.builder()
                .id(101L)
                .orderId(1L)
                .orderCode("FPT-261014-000001")
                .status(InvitationStatus.PENDING)
                .build();

        when(invitationService.getCourierInvitations(any(User.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/me/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(101));
    }

    @Test
    void testAcceptInvitation_Success() throws Exception {
        when(userRepository.findByPublicId(samplePublicId)).thenReturn(Optional.of(sampleUser));

        InvitationResponse response = InvitationResponse.builder()
                .id(101L)
                .status(InvitationStatus.ACCEPTED)
                .build();

        when(invitationService.acceptInvitation(eq(101L), any(User.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/invitations/101/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void testToggleFavorite_Success() throws Exception {
        when(userRepository.findByPublicId(samplePublicId)).thenReturn(Optional.of(sampleUser));

        FavoriteCourierResponse fav = FavoriteCourierResponse.builder()
                .id(10L)
                .courierId(2L)
                .fullName("Trần Văn Shipper")
                .build();

        when(invitationService.toggleFavorite(eq(2L), any(), any(User.class)))
                .thenReturn(fav);

        mockMvc.perform(put("/api/v1/me/favorites/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.courierId").value(2));
    }
}

package com.fptpost.modules.order.controller;

import com.fptpost.common.exception.GlobalExceptionHandler;
import com.fptpost.common.exception.ProblemResponseFactory;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.service.*;
import com.fptpost.modules.payment.service.OrderPaymentService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC binding/advice and method security; mocked services and authenticated JWT principal.
 * Does not exercise bearer signature validation or the production servlet filter chain. */
class PickupConfirmationControllerTest {
    AnnotationConfigApplicationContext context;
    MockMvc mvc;
    PickupConfirmationService service;
    UserRepository users;
    static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000025252");
    static final String URL = "/api/v1/orders/25001/confirm-pickup";
    MockMultipartFile photo = new MockMultipartFile("photo", "proof.jpg", "image/jpeg", new byte[]{1});

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean PickupConfirmationService pickup() { return mock(PickupConfirmationService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean OrderController controller(PickupConfirmationService pickup, UserRepository users) {
            return new OrderController(mock(OrderService.class), mock(OrderPaymentService.class),
                    mock(PricingCalculatorService.class), users, mock(RelayOrderService.class), pickup);
        }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        service = context.getBean(PickupConfirmationService.class);
        users = context.getBean(UserRepository.class);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages/errors");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(OrderController.class))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(new ProblemResponseFactory(messages))).build();
    }

    void login(String role) {
        Jwt jwt = Jwt.withTokenValue("test-only").header("alg", "none").subject(PUBLIC_ID.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test void multipartBindsPhotoTokenAndJwtUser() throws Exception {
        login("COURIER");
        when(users.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(User.builder().id(252L).build()));
        when(service.confirmPickup(25001L, 252L, "test-qr", photo))
                .thenReturn(OrderResponse.builder().id(25001L).status(OrderStatus.IN_DELIVERY).build());
        mvc.perform(multipart(URL).file(photo).param("qrToken", "test-qr"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_DELIVERY"));
        verify(service).confirmPickup(25001L, 252L, "test-qr", photo);
    }

    @Test void jsonContentTypeRejectedWith415() throws Exception {
        login("COURIER");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("SYS_UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(service, users);
    }

    @Test void missingPhotoRejectedWith400() throws Exception {
        login("COURIER");
        mvc.perform(multipart(URL).param("qrToken", "test-qr")).andExpect(status().isBadRequest());
        verifyNoInteractions(service, users);
    }

    @Test void missingQrRejectedWith400() throws Exception {
        login("COURIER");
        mvc.perform(multipart(URL).file(photo)).andExpect(status().isBadRequest());
        verifyNoInteractions(service, users);
    }

    @Test void senderCannotConfirm() throws Exception {
        login("SENDER");
        mvc.perform(multipart(URL).file(photo).param("qrToken", "test-qr"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service, users);
    }

    @Test void courierCannotGetSenderQr() throws Exception {
        login("COURIER");
        mvc.perform(get("/api/v1/orders/25001/pickup-qr")).andExpect(status().isForbidden());
        verifyNoInteractions(service, users);
    }

    @Test void senderQrResolvesJwtSubject() throws Exception {
        login("SENDER");
        when(users.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(User.builder().id(251L).build()));
        when(service.getPickupQr(25001L, 251L)).thenReturn("test-qr");
        mvc.perform(get("/api/v1/orders/25001/pickup-qr"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.qrToken").value("test-qr"));
        verify(service).getPickupQr(25001L, 251L);
    }
}

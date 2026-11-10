package com.fptpost.modules.deliveryreturn;

import com.fptpost.common.exception.*;
import com.fptpost.modules.deliveryreturn.controller.FailedDeliveryController;
import com.fptpost.modules.deliveryreturn.dto.*;
import com.fptpost.modules.deliveryreturn.exception.DeliveryReturnErrorCode;
import com.fptpost.modules.deliveryreturn.service.FailedDeliveryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real validation/advice/method security, mocked service; no database, network, real JWT or uploads. */
class FailedDeliveryControllerTest {
    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000003030");
    static final String BASE = "/api/v1/orders/300";
    AnnotationConfigApplicationContext context;
    FailedDeliveryService service;
    MockMvc mvc;
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean FailedDeliveryService service() { return mock(FailedDeliveryService.class); }
        @Bean FailedDeliveryController controller(FailedDeliveryService service) { return new FailedDeliveryController(service); }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class); service = context.getBean(FailedDeliveryService.class);
        var messages = new ResourceBundleMessageSource(); messages.setBasename("messages/errors"); messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(FailedDeliveryController.class))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(new ProblemResponseFactory(messages))).build();
        login("COURIER");
    }
    void login(String role) {
        var jwt = Jwt.withTokenValue("test-only").header("alg", "none").subject(ACTOR.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    MockMultipartFile request(String json) { return new MockMultipartFile("request", "", "application/json", json.getBytes(StandardCharsets.UTF_8)); }
    MockMultipartFile valid() { return request("{\"reason\":\"RECIPIENT_UNREACHABLE\",\"description\":\"Không nghe máy\"}"); }
    MockMultipartFile photo() { return new MockMultipartFile("photo", "calls.png", "image/png", new byte[]{1}); }

    @Test void multipartFailurePassesJwtOrderAndEvidenceToService() throws Exception {
        mvc.perform(multipart(BASE + "/delivery-failed").file(valid()).file(photo())).andExpect(status().isCreated());
        verify(service).report(eq(ACTOR), eq(300L), argThat(r -> r.description().equals("Không nghe máy")), any());
    }
    @ParameterizedTest @ValueSource(strings = {"SENDER", "MODERATOR", "ADMIN"})
    void nonCourierCannotFailDelivery(String role) throws Exception {
        login(role); mvc.perform(multipart(BASE + "/delivery-failed").file(valid()).file(photo())).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"reason\":\"RECIPIENT_UNREACHABLE\",\"description\":\" \"}",
            "{\"reason\":\"OTHER\",\"description\":\"test\"}"})
    void invalidFailureDataRejected(String json) throws Exception {
        mvc.perform(multipart(BASE + "/delivery-failed").file(request(json)).file(photo())).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void missingImageRejected() throws Exception {
        mvc.perform(multipart(BASE + "/delivery-failed").file(valid())).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void jsonCannotReplaceMultipartFailure() throws Exception {
        mvc.perform(post(BASE + "/delivery-failed").contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType()); verifyNoInteractions(service);
    }
    @Test void contactRequiresRequestIdAndNote() throws Exception {
        mvc.perform(post(BASE + "/delivery-contact-attempts").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void senderCanConfirmPhysicalReceipt() throws Exception {
        login("SENDER");
        mvc.perform(post(BASE + "/return/confirm").contentType("application/json").content("{\"code\":\"123456\",\"received\":true}"))
                .andExpect(status().isOk()); verify(service).confirm(ACTOR, 300L, new ReturnRequests.Confirm("123456", true));
    }
    @ParameterizedTest @ValueSource(strings = {"COURIER", "MODERATOR", "ADMIN"})
    void othersCannotConfirmOrIssueCode(String role) throws Exception {
        login(role);
        mvc.perform(post(BASE + "/return/code")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/return/confirm").contentType("application/json").content("{\"code\":\"123456\",\"received\":true}"))
                .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"code\":\"123\",\"received\":true}", "{\"code\":\"123456\",\"received\":false}",
            "{\"code\":\"123456\"}"})
    void codeAndReceiptMustBeValid(String json) throws Exception {
        login("SENDER"); mvc.perform(post(BASE + "/return/confirm").contentType("application/json").content(json))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void duplicateOrWrongStatusIs409() throws Exception {
        when(service.report(any(), anyLong(), any(), any())).thenThrow(new AppException(DeliveryReturnErrorCode.RETURN_INVALID_STATUS));
        mvc.perform(multipart(BASE + "/delivery-failed").file(valid()).file(photo())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_INVALID_STATUS"));
    }
    @Test void readAndResumeUseCorrectRoutes() throws Exception {
        mvc.perform(get(BASE + "/return")).andExpect(status().isOk()); verify(service).detail(ACTOR, 300L);
        mvc.perform(post(BASE + "/return/resume").contentType("application/json").content("{\"description\":\"Người nhận đã liên lạc lại\"}"))
                .andExpect(status().isOk()); verify(service).resume(eq(ACTOR), eq(300L), any());
    }
    @Test void orderFiveWithoutReturnCaseHasSuccessfulEmptyState() throws Exception {
        when(service.detail(ACTOR, 5L)).thenReturn(new ReturnResponse(5L,
                com.fptpost.modules.order.model.OrderStatus.IN_DELIVERY, true, false, false,
                3, 30, List.of(), null, List.of()));
        mvc.perform(get("/api/v1/orders/5/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(5))
                .andExpect(jsonPath("$.data.orderStatus").value("IN_DELIVERY"))
                .andExpect(jsonPath("$.data.canReport").value(true))
                .andExpect(jsonPath("$.data.latestCase").doesNotExist())
                .andExpect(jsonPath("$.data.contacts").isEmpty())
                .andExpect(jsonPath("$.data.history").isEmpty());
    }
    @Test void anonymousCannotRead() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("uc30", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        mvc.perform(get(BASE + "/return")).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
}

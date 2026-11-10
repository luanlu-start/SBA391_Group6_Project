package com.fptpost.modules.incident;

import com.fptpost.common.exception.*;
import com.fptpost.modules.incident.controller.DeliveryIncidentController;
import com.fptpost.modules.incident.dto.*;
import com.fptpost.modules.incident.exception.IncidentErrorCode;
import com.fptpost.modules.incident.model.IncidentType;
import com.fptpost.modules.incident.service.DeliveryIncidentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC validation, advice and method security, no application startup, DB or external delivery. */
class DeliveryIncidentControllerTest {
    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000002929");
    static final String URL = "/api/v1/orders/290/incidents";
    AnnotationConfigApplicationContext context;
    DeliveryIncidentService service;
    MockMvc mvc;

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean DeliveryIncidentService service() { return mock(DeliveryIncidentService.class); }
        @Bean DeliveryIncidentController controller(DeliveryIncidentService service) { return new DeliveryIncidentController(service); }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(DeliveryIncidentService.class);
        var messages = new ResourceBundleMessageSource(); messages.setBasename("messages/errors"); messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(DeliveryIncidentController.class))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(new ProblemResponseFactory(messages))).build();
        login("COURIER");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    void login(String role) {
        var jwt = Jwt.withTokenValue("test-only").header("alg", "none").subject(ACTOR.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    MockMultipartFile request(String json) {
        return new MockMultipartFile("request", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
    MockMultipartFile valid() { return request("{\"type\":\"TRAFFIC_JAM\",\"description\":\"Kẹt xe\"}"); }

    @Test void createsReportWithJwtIdentityAndPhoto() throws Exception {
        var photo = new MockMultipartFile("photo", "proof.png", "image/png", new byte[]{1});
        when(service.report(eq(ACTOR), eq(290L), any(), eq(photo))).thenReturn(new IncidentResponse(
                1L, 290L, 29L, IncidentType.TRAFFIC_JAM, "Kẹt xe", true, Instant.parse("2026-11-11T00:00:00Z")));
        mvc.perform(multipart(URL).file(valid()).file(photo)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.type").value("TRAFFIC_JAM"))
                .andExpect(jsonPath("$.data.hasEvidence").value(true));
        verify(service).report(ACTOR, 290L, new IncidentRequest(IncidentType.TRAFFIC_JAM, "Kẹt xe"), photo);
    }
    @Test void photoOptional() throws Exception {
        mvc.perform(multipart(URL).file(valid())).andExpect(status().isCreated());
        verify(service).report(eq(ACTOR), eq(290L), any(), isNull());
    }
    @ParameterizedTest @ValueSource(strings = {"SENDER", "MODERATOR", "ADMIN"})
    void onlyCourierCanReport(String role) throws Exception {
        login(role);
        mvc.perform(multipart(URL).file(valid())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {
            "{}", "{\"type\":\"TRAFFIC_JAM\"}", "{\"description\":\"test\"}",
            "{\"type\":\"TRAFFIC_JAM\",\"description\":\"   \"}",
            "{\"type\":\"UNKNOWN\",\"description\":\"test\"}"})
    void invalidDataRejected(String json) throws Exception {
        mvc.perform(multipart(URL).file(request(json))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void longDescriptionRejected() throws Exception {
        mvc.perform(multipart(URL).file(request("{\"type\":\"TRAFFIC_JAM\",\"description\":\"" + "x".repeat(1001) + "\"}")))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void missingRequestPartRejected() throws Exception {
        mvc.perform(multipart(URL)).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void jsonInsteadOfMultipartRejected() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType()); verifyNoInteractions(service);
    }
    @Test void assignmentErrorUsesProblemResponse() throws Exception {
        when(service.report(any(), anyLong(), any(), any())).thenThrow(new AppException(IncidentErrorCode.INCIDENT_ACCESS_DENIED));
        mvc.perform(multipart(URL).file(valid())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INCIDENT_ACCESS_DENIED"));
    }
    @Test void invalidOrderStatusUses409() throws Exception {
        when(service.report(any(), anyLong(), any(), any())).thenThrow(new AppException(IncidentErrorCode.INCIDENT_INVALID_STATUS));
        mvc.perform(multipart(URL).file(valid())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCIDENT_INVALID_STATUS"));
    }
    @Test void historyUsesJwtAndStablePageEnvelope() throws Exception {
        when(service.history(ACTOR, 290L, 0, 20)).thenReturn(Page.empty());
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        verify(service).history(ACTOR, 290L, 0, 20);
    }
    @Test void anonymousCannotRead() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("uc29-test",
                "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        mvc.perform(get(URL)).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
}

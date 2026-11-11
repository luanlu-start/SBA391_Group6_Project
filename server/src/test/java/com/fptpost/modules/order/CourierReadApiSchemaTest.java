package com.fptpost.modules.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.*;
import com.fptpost.config.SecurityConfig;
import com.fptpost.modules.batch.controller.BatchController;
import com.fptpost.modules.catalog.dto.internal.ParameterSnapshot;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.reputation.repository.TrustScoreRepository;
import com.fptpost.modules.batch.repository.*;
import com.fptpost.modules.batch.service.BatchService;
import com.fptpost.modules.batch.service.routing.ChCrpRoutingEngine;
import com.fptpost.modules.identity.config.AuthenticationProperties;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.order.controller.*;
import com.fptpost.modules.order.mapper.OrderMapper;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import com.fptpost.modules.order.model.OrderLeg;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.*;
import com.fptpost.modules.payment.controller.CourierEarningsController;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.service.*;
import com.fptpost.modules.trip.repository.TripRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.mock.web.MockServletContext;
import org.springframework.orm.jpa.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Hibernate/repository/service/mapper/MVC and security chain, only an isolated in-memory H2 DB.
 * Never starts Spring Boot, seeders, scheduled jobs or real database connections. JWT decoder and unrelated
 * repositories are test doubles; no real account, token, upload or network service is used. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourierReadApiSchemaTest {
    static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000004444");
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    Long normalId, relayId;

    @Configuration @EnableWebMvc @EnableWebSecurity @EnableTransactionManagement(proxyTargetClass = true) @Import(SecurityConfig.class)
    static class Config {
        @Bean(destroyMethod = "close") SessionFactory entityManagerFactory() {
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                    .applySetting("hibernate.connection.url", "jdbc:h2:mem:courier_schema_" + UUID.randomUUID() + ";MODE=MSSQLServer")
                    .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                    .applySetting("hibernate.show_sql", "false").build();
            return new MetadataSources(registry).addAnnotatedClass(Order.class).addAnnotatedClass(OrderLeg.class)
                    .addAnnotatedClass(OrderImage.class).buildMetadata().buildSessionFactory();
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean OrderRepository orders(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(OrderRepository.class);
        }
        @Bean UserRepository users() {
            var users = mock(UserRepository.class);
            when(users.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(User.builder().id(44L).publicId(PUBLIC_ID).build()));
            return users;
        }
        @Bean CourierProfileRepository profiles() { return mock(CourierProfileRepository.class); }
        @Bean EscrowRepository escrows() { return mock(EscrowRepository.class); }
        @Bean OrderService ordersService(OrderRepository orders, CourierProfileRepository profiles) {
            return new OrderService(orders, Mappers.getMapper(OrderMapper.class), mock(PricingCalculatorService.class),
                    profiles, mock(OrderSubmissionScreening.class));
        }
        @Bean CourierEarningsService earningsService(OrderRepository orders, EscrowRepository escrows, CourierProfileRepository profiles) {
            Map<String, String> tiers = new LinkedHashMap<>();
            tiers.put("TRUST_INIT", "50");
            tiers.put("W_ONTIME", "0.35");
            tiers.put("W_RATING", "0.3");
            tiers.put("W_COMPLETE", "0.2");
            tiers.put("W_COMPLAINT", "0.15");
            tiers.put("TRUST_RESTRICT", "30");
            tiers.put("TRUST_REVIEW", "20");
            tiers.put("CANCEL_PENALTY", "5");
            tiers.put("CANCEL_LIMIT", "3");
            tiers.put("CANCEL_BAN", "3");
            tiers.put("TIER_BRONZE", "≥ 55 điểm & ≥ 5 đơn");
            tiers.put("TIER_SILVER", "≥ 70 điểm & ≥ 20 đơn");
            tiers.put("TIER_GOLD", "≥ 85 điểm & ≥ 60 đơn");
            tiers.put("TIER_DIAMOND", "≥ 95 điểm & ≥ 150 đơn");
            Map<String, Long> versions = new LinkedHashMap<>();
            tiers.keySet().forEach(key -> versions.put(key, 1L));
            ParameterService parameters = mock(ParameterService.class);
            when(parameters.getSnapshot()).thenReturn(new ParameterSnapshot(tiers, versions, Instant.EPOCH));
            return new CourierEarningsService(orders, escrows, profiles, parameters, mock(TrustScoreRepository.class));
        }
        @Bean BatchService batchService(OrderRepository orders) {
            return new BatchService(mock(DeliveryBatchRepository.class), mock(DeliveryBatchStopRepository.class), orders,
                    mock(TripRepository.class), new ChCrpRoutingEngine());
        }
        @Bean MarketController marketController(OrderService orders, UserRepository users) { return new MarketController(orders, users); }
        @Bean BatchController batchController(BatchService batches, UserRepository users) { return new BatchController(batches, users); }
        @Bean CourierEarningsController earningsController(CourierEarningsService earnings, UserRepository users) {
            return new CourierEarningsController(earnings, users);
        }
        @Bean OrderController orderController(OrderService orders, UserRepository users) {
            return new OrderController(orders, mock(OrderPaymentService.class), mock(PricingCalculatorService.class),
                    users, mock(RelayOrderService.class), mock(PickupConfirmationService.class));
        }
        @Bean JwtDecoder decoder() {
            var decoder = mock(JwtDecoder.class);
            when(decoder.decode("test-valid")).thenReturn(Jwt.withTokenValue("test-only").header("alg", "none")
                    .subject(PUBLIC_ID.toString()).claim("roles", List.of("COURIER")).build());
            when(decoder.decode("test-expired")).thenThrow(new BadJwtException("Expired test credential"));
            return decoder;
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean AuthenticationProperties properties() { return new AuthenticationProperties(); }
        @Bean CorsFilter corsFilter() { return new CorsFilter(new UrlBasedCorsConfigurationSource()); }
        @Bean ProblemResponseFactory problems() {
            var messages = new ResourceBundleMessageSource(); messages.setBasename("messages/errors"); messages.setDefaultEncoding("UTF-8");
            return new ProblemResponseFactory(messages);
        }
        @Bean GlobalExceptionHandler advice(ProblemResponseFactory problems) { return new GlobalExceptionHandler(problems); }
        @Bean WebMvcConfigurer argumentResolvers() {
            return new WebMvcConfigurer() {
                @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new AuthenticationPrincipalArgumentResolver());
                    resolvers.add(new PageableHandlerMethodArgumentResolver());
                }
            };
        }
    }

    @BeforeAll void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        var factory = context.getBean(EntityManagerFactory.class);
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> {
            var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
            // Model the deployed schema that has order_legs but no redundant is_relay column.
            // With the old @Column mapping, dropping this column makes every Order read fail.
            em.createNativeQuery("alter table orders drop column if exists is_relay").executeUpdate();
            var normal = order("NORMAL", OrderStatus.SHIPPER_ASSIGNED);
            normal.addImage(OrderImage.builder().imageUrl("/test-proof.png").build());
            var relay = order("RELAY", OrderStatus.FINDING_SHIPPER);
            relay.setIsRelay(true);
            for (int legNo = 1; legNo <= 2; legNo++) relay.addLeg(OrderLeg.builder().legNo(legNo)
                    .pickupAddress("A").deliveryAddress("B").status(OrderStatus.FINDING_SHIPPER).build());
            em.persist(normal); em.persist(relay); em.flush();
            normalId = normal.getId(); relayId = relay.getId();
        });
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    Order order(String code, OrderStatus status) {
        return Order.builder().orderCode(code).senderId(43L).courierId(44L).pickupAddress("A").deliveryAddress("B")
                .receiverName("Test receiver").receiverPhone("0000000000").productName("Test parcel")
                .quantity(1).weightKg(BigDecimal.ONE).priceSuggested(BigDecimal.valueOf(30000)).status(status).build();
    }
    @AfterEach void cleanupSecurity() { SecurityContextHolder.clearContext(); }
    @AfterAll void cleanup() { if (context != null) context.close(); }

    @Test void marketplaceReadsLegacySchema() throws Exception {
        mvc.perform(get("/api/v1/market/orders").param("page", "0").param("size", "6").param("sort", "createdAt,desc")
                .header("Authorization", "Bearer test-valid")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderCode").value("RELAY"));
    }
    @Test void assignedOrdersReadsImagesAndRelayFlag() throws Exception {
        mvc.perform(get("/api/v1/orders/assigned-to-me").header("Authorization", "Bearer test-valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        var responses = context.getBean(OrderService.class).getOrdersByCourierId(44L);
        assertThat(responses).filteredOn(o -> o.getId().equals(normalId)).singleElement().satisfies(o -> {
            assertThat(o.getIsRelay()).isFalse(); assertThat(o.getImageUrls()).containsExactly("/test-proof.png");
        });
        assertThat(responses).filteredOn(o -> o.getId().equals(relayId)).singleElement().satisfies(o -> assertThat(o.getIsRelay()).isTrue());
    }
    @Test void eligibleBatchOrdersReadsLegacySchema() throws Exception {
        mvc.perform(get("/api/v1/batches/eligible-orders").header("Authorization", "Bearer test-valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }
    @Test void earningsReadsLegacySchema() throws Exception {
        mvc.perform(get("/api/v1/me/earnings").param("period", "MONTH").header("Authorization", "Bearer test-valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pendingEscrowAmount").value(60000));
    }
    @Test void statsReadsLegacySchema() throws Exception {
        mvc.perform(get("/api/v1/me/stats").header("Authorization", "Bearer test-valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAssignedOrders").value(2));
    }
    @Test void marketplaceStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/market/orders")).andExpect(status().isUnauthorized());
    }
    @Test void expiredCredentialRejectedThenValidCredentialReadsData() throws Exception {
        mvc.perform(get("/api/v1/market/orders").header("Authorization", "Bearer test-expired"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/market/orders").header("Authorization", "Bearer test-valid"))
                .andExpect(status().isOk());
    }
    @Test void repositorySearchAndMissingOrderAlsoWorkWithoutColumn() {
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); tx.setReadOnly(true);
        tx.executeWithoutResult(status -> {
            var repository = context.getBean(OrderRepository.class);
            assertThat(repository.findAvailableShippingOrderByKeyword(PageRequest.of(0, 6), "A").getTotalElements()).isEqualTo(1);
            assertThat(repository.findById(0L)).isEmpty();
        });
    }
}

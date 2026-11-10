package com.fptpost.modules.deliveryreturn;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.*;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.deliveryreturn.dto.*;
import com.fptpost.modules.deliveryreturn.exception.*;
import com.fptpost.modules.deliveryreturn.model.*;
import com.fptpost.modules.deliveryreturn.repository.*;
import com.fptpost.modules.deliveryreturn.service.FailedDeliveryService;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.*;
import com.fptpost.modules.order.repository.OrderRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.*;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real JPA locks/transactions on a random H2 in-memory database. No Boot, real database, file or notification channel. */
class FailedDeliveryTransactionTest {
    static final UUID COURIER = UUID.fromString("00000000-0000-0000-0000-000000003002"), SENDER = UUID.fromString("00000000-0000-0000-0000-000000003001");
    AnnotationConfigApplicationContext context;
    FailedDeliveryService service;
    Long orderId;
    final MockMultipartFile photo = new MockMultipartFile("photo", "proof.png", "image/png", new byte[]{1});
    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") SessionFactory entityManagerFactory() {
            var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                    .applySetting("hibernate.connection.url", "jdbc:h2:mem:uc30_" + UUID.randomUUID() + ";MODE=MSSQLServer;LOCK_TIMEOUT=10000")
                    .applySetting("hibernate.hbm2ddl.auto", "create-drop").build();
            return new MetadataSources(registry).addAnnotatedClass(Order.class).addAnnotatedClass(OrderLeg.class).addAnnotatedClass(OrderImage.class)
                    .addAnnotatedClass(DeliveryReturnCase.class).addAnnotatedClass(DeliveryContactAttempt.class).addAnnotatedClass(DeliveryReturnHistory.class)
                    .buildMetadata().buildSessionFactory();
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        <T> T repository(EntityManagerFactory factory, Class<T> type) { return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(type); }
        @Bean OrderRepository orders(EntityManagerFactory f) { return repository(f, OrderRepository.class); }
        @Bean DeliveryReturnRepository cases(EntityManagerFactory f) { return repository(f, DeliveryReturnRepository.class); }
        @Bean DeliveryContactAttemptRepository contacts(EntityManagerFactory f) { return repository(f, DeliveryContactAttemptRepository.class); }
        @Bean DeliveryReturnHistoryRepository history(EntityManagerFactory f) { return repository(f, DeliveryReturnHistoryRepository.class); }
        @Bean IdentityUserAccess access() {
            var mock = mock(IdentityUserAccess.class); when(mock.read(COURIER)).thenReturn(FailedDeliveryServiceTest.user(302L, "COURIER"));
            when(mock.read(SENDER)).thenReturn(FailedDeliveryServiceTest.user(301L, "SENDER")); return mock;
        }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean ParameterService parameters() { var mock = mock(ParameterService.class); when(mock.getSnapshot()).thenReturn(FailedDeliveryServiceTest.snapshot("3 lần gọi trong 30 phút", "0.5")); return mock; }
        @Bean ImageValidator images() { var mock = mock(ImageValidator.class); when(mock.validate(any())).thenReturn(new ValidatedImage(new byte[]{1}, "image/png")); return mock; }
        @Bean PrivateFileStorage storage() { var mock = mock(PrivateFileStorage.class); when(mock.put(any())).thenReturn(UUID.randomUUID()); return mock; }
        @Bean NotificationPublisher notifications() { return mock(NotificationPublisher.class); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneOffset.UTC); }
        @Bean FailedDeliveryService service(OrderRepository orders, DeliveryReturnRepository cases, DeliveryContactAttemptRepository contacts,
                DeliveryReturnHistoryRepository history, IdentityUserAccess access, UserRepository users, ParameterService parameters,
                ImageValidator images, PrivateFileStorage storage, NotificationPublisher notifications, Clock clock) {
            return new FailedDeliveryService(orders, cases, contacts, history, access, users, parameters, images, storage, notifications, clock);
        }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class); service = context.getBean(FailedDeliveryService.class);
        tx().executeWithoutResult(status -> {
            var em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
            var order = Order.builder().orderCode("UC30-TRANSACTION").senderId(301L).courierId(302L).tripId(100L).pickupAddress("A").deliveryAddress("B")
                    .receiverName("Test").receiverPhone("0000000000").productName("Test").quantity(1).weightKg(BigDecimal.ONE)
                    .priceAgreed(BigDecimal.valueOf(40000)).status(OrderStatus.IN_DELIVERY).build(); em.persist(order); em.flush(); orderId = order.getId();
        });
        for (int i = 0; i < 3; i++) service.contact(COURIER, orderId, new ReturnRequests.Contact(UUID.randomUUID(), "Không nghe máy"));
    }
    @AfterEach void cleanup() { if (context != null) context.close(); }
    TransactionTemplate tx() { return new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); }
    ReturnRequests.Failure failure() { return new ReturnRequests.Failure(FailureReason.RECIPIENT_UNREACHABLE, "Không liên lạc được"); }

    @Test void notificationFailureRollsBackOrderCaseConsumedCallsAndHistory() {
        var userRepo = context.getBean(UserRepository.class);
        when(userRepo.findById(301L)).thenReturn(Optional.of(FailedDeliveryServiceTest.user(301L, "SENDER")));
        NotificationPublisher notificationMock = AopTestUtils.getUltimateTargetObject(context.getBean(NotificationPublisher.class));
        doThrow(new IllegalStateException("Simulated outbox failure")).when(notificationMock).publish(any(), any(), anyString(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> service.report(COURIER, orderId, failure(), photo)).isInstanceOf(IllegalStateException.class);
        tx().executeWithoutResult(status -> {
            assertThat(context.getBean(OrderRepository.class).findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.IN_DELIVERY);
            assertThat(context.getBean(DeliveryReturnRepository.class).count()).isZero();
            assertThat(context.getBean(DeliveryReturnHistoryRepository.class).count()).isEqualTo(3);
            assertThat(context.getBean(DeliveryContactAttemptRepository.class).findAll()).allMatch(c -> c.getReturnCase() == null);
        });
    }
    @Test void invalidCodeCountersCommitWhileSuccessfulConfirmationIsSingleUse() {
        service.report(COURIER, orderId, failure(), photo);
        var issued = service.issueCode(SENDER, orderId);
        String wrong = issued.code().equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> service.confirm(SENDER, orderId, new ReturnRequests.Confirm(wrong, true))).isInstanceOf(ReturnCodeRejectedException.class);
        tx().executeWithoutResult(status -> assertThat(context.getBean(DeliveryReturnRepository.class)
                .findTopByOrderIdOrderByIdDesc(orderId).orElseThrow().getCodeFailures()).isEqualTo(1));
        service.confirm(SENDER, orderId, new ReturnRequests.Confirm(issued.code(), true));
        assertThatThrownBy(() -> service.confirm(SENDER, orderId, new ReturnRequests.Confirm(issued.code(), true)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(DeliveryReturnErrorCode.RETURN_INVALID_STATUS));
        assertThat(service.detail(SENDER, orderId).orderStatus()).isEqualTo(OrderStatus.RETURNED);
        tx().executeWithoutResult(status -> assertThat(context.getBean(OrderRepository.class).countActiveOrdersByTripId(100L)).isZero());
    }
    @Test void simultaneousReportsAreSerializedByOrderRowLock() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> { start.await(); try { service.report(COURIER, orderId, failure(), photo); return "OK"; }
                catch (AppException ex) { return ex.getErrorCode().getCode(); } };
            var first = executor.submit(task); var second = executor.submit(task); start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "RETURN_INVALID_STATUS");
        }
        tx().executeWithoutResult(status -> assertThat(context.getBean(DeliveryReturnRepository.class).count()).isEqualTo(1));
        verify(context.getBean(PrivateFileStorage.class), times(1)).put(any());
    }
}

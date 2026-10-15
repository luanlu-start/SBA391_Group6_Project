package com.fptpost.modules.payment.config;

import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentDemoSeeder Unit Tests")
class PaymentDemoSeederTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Should seed sample orders and transactions when not existing")
    void testSeedDemoData_WhenEmpty() {
        PaymentDemoSeeder seeder = new PaymentDemoSeeder(
                paymentTransactionRepository,
                Optional.of(orderRepository),
                Optional.empty(),
                Optional.empty()
        );

        when(orderRepository.existsByOrderCode(any())).thenReturn(false);
        when(paymentTransactionRepository.existsByTxnRef(any())).thenReturn(false);

        seeder.seedDemoData();

        verify(orderRepository, times(4)).save(any(Order.class));
        verify(paymentTransactionRepository, times(3)).save(any(PaymentTransaction.class));
    }

    @Test
    @DisplayName("Should not duplicate sample data when already existing (Idempotent)")
    void testSeedDemoData_WhenAlreadyExists() {
        PaymentDemoSeeder seeder = new PaymentDemoSeeder(
                paymentTransactionRepository,
                Optional.of(orderRepository),
                Optional.empty(),
                Optional.empty()
        );

        when(orderRepository.existsByOrderCode(any())).thenReturn(true);
        when(paymentTransactionRepository.existsByTxnRef(any())).thenReturn(true);

        seeder.seedDemoData();

        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
    }
}

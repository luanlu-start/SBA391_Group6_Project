package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.dto.RefundToSourceRequest;
import com.fptpost.modules.payment.dto.RefundToSourceResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.Escrow;
import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.model.RefundKind;
import com.fptpost.modules.payment.model.RefundRequest;
import com.fptpost.modules.payment.model.RefundStatus;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.repository.RefundRequestRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaymentService Refund Tests (UC-37)")
class OrderRefundServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private EscrowRepository escrowRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private RefundRequestRepository refundRequestRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private OrderPaymentService orderPaymentService;

    private static final Long ORDER_ID = 100L;
    private static final Long SENDER_ID = 10L;
    private static final Long COURIER_ID = 20L;

    private Order testOrder;
    private Wallet senderWallet;
    private Wallet courierWallet;

    @BeforeEach
    void setUp() {
        testOrder = new Order();
        testOrder.setId(ORDER_ID);
        testOrder.setOrderCode("FP-TEST-100");
        testOrder.setSenderId(SENDER_ID);
        testOrder.setStatus(OrderStatus.CANCELLED);

        senderWallet = Wallet.builder()
                .id(1L)
                .userId(SENDER_ID)
                .available(50_000L)
                .escrowHeld(200_000L)
                .pendingWithdraw(0L)
                .build();

        courierWallet = Wallet.builder()
                .id(2L)
                .userId(COURIER_ID)
                .available(10_000L)
                .escrowHeld(0L)
                .pendingWithdraw(0L)
                .build();
    }

    @Test
    @DisplayName("UC-37 / BR-R-022: Hủy đơn trước khi có người giao -> hoàn 100% ký quỹ vào ví người gửi")
    void refundEscrowOnCancel_BeforeCourierAssigned_FullRefund() {
        Escrow escrow = Escrow.builder()
                .id(50L)
                .orderId(ORDER_ID)
                .senderId(SENDER_ID)
                .amount(200_000L)
                .status(EscrowStatus.HELD)
                .paymentMethod(EscrowPaymentMethod.WALLET)
                .frozen(false)
                .build();

        when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
        when(walletRepository.findByUserIdForUpdate(SENDER_ID)).thenReturn(Optional.of(senderWallet));

        Optional<OrderPaymentService.EscrowRefundResult> resultOpt =
                orderPaymentService.refundEscrowIfHeldOnCancel(ORDER_ID, false, "Hủy trước khi có bưu tá");

        assertTrue(resultOpt.isPresent());
        OrderPaymentService.EscrowRefundResult result = resultOpt.get();
        assertEquals(200_000L, result.refundedAmount());
        assertEquals(0L, result.cancellationFee());
        assertEquals(EscrowStatus.REFUNDED, result.escrowStatus());
        assertEquals(250_000L, senderWallet.getAvailable());
        assertEquals(0L, senderWallet.getEscrowHeld());
        verify(ledgerService, times(1)).recordEscrowRefund(eq(ORDER_ID), eq(SENDER_ID), eq(200_000L), anyString());
    }

    @Test
    @DisplayName("UC-37 / BR-R-022: Hủy đơn sau khi đã gán bưu tá -> trừ 10% phí hủy trả bưu tá, hoàn 90% cho người gửi")
    void refundEscrowOnCancel_AfterCourierAssigned_Deducts10PercentCancelFee() {
        testOrder.setCourierId(COURIER_ID);
        Escrow escrow = Escrow.builder()
                .id(51L)
                .orderId(ORDER_ID)
                .senderId(SENDER_ID)
                .amount(200_000L)
                .status(EscrowStatus.HELD)
                .paymentMethod(EscrowPaymentMethod.WALLET)
                .frozen(false)
                .build();

        when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
        when(walletRepository.findByUserIdForUpdate(SENDER_ID)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserIdForUpdate(COURIER_ID)).thenReturn(Optional.of(courierWallet));

        Optional<OrderPaymentService.EscrowRefundResult> resultOpt =
                orderPaymentService.refundEscrowIfHeldOnCancel(ORDER_ID, false, "Hủy sau khi đã gán bưu tá");

        assertTrue(resultOpt.isPresent());
        OrderPaymentService.EscrowRefundResult result = resultOpt.get();
        assertEquals(180_000L, result.refundedAmount());
        assertEquals(20_000L, result.cancellationFee());
        assertEquals(EscrowStatus.PARTIALLY_REFUNDED, result.escrowStatus());
        assertEquals(230_000L, senderWallet.getAvailable());
        assertEquals(30_000L, courierWallet.getAvailable());
        verify(ledgerService, times(1)).recordEscrowRelease(eq(ORDER_ID), eq(SENDER_ID), eq(COURIER_ID), eq(20_000L), anyString());
        verify(ledgerService, times(1)).recordEscrowRefund(eq(ORDER_ID), eq(SENDER_ID), eq(180_000L), anyString());
    }

    @Test
    @DisplayName("API-091: Hoàn tiền về nguồn VNPay thành công khi đơn đã hoàn ký quỹ vào ví")
    void requestRefundToSource_VNPaySuccess() {
        senderWallet.setAvailable(300_000L);
        Escrow escrow = Escrow.builder()
                .id(52L)
                .orderId(ORDER_ID)
                .senderId(SENDER_ID)
                .amount(200_000L)
                .status(EscrowStatus.REFUNDED)
                .paymentMethod(EscrowPaymentMethod.VNPAY)
                .frozen(false)
                .build();

        PaymentTransaction paymentTxn = PaymentTransaction.builder()
                .id(900L)
                .orderId(ORDER_ID)
                .build();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
        when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));
        when(refundRequestRepository.existsByOrderIdAndStatusIn(eq(ORDER_ID), eq(Set.of(RefundStatus.SUCCESS, RefundStatus.PENDING))))
                .thenReturn(false);
        when(paymentTransactionRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(paymentTxn));
        when(walletRepository.findByUserIdForUpdate(SENDER_ID)).thenReturn(Optional.of(senderWallet));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> {
            RefundRequest r = inv.getArgument(0);
            r.setId(700L);
            return r;
        });

        RefundToSourceResponse response = orderPaymentService.requestRefundToSource(
                ORDER_ID,
                SENDER_ID,
                RefundToSourceRequest.builder().reason("Hoàn về thẻ VNPay").build()
        );

        assertNotNull(response);
        assertEquals(700L, response.getId());
        assertEquals(200_000L, response.getAmount());
        assertEquals(RefundKind.FULL, response.getKind());
        assertEquals(RefundStatus.SUCCESS, response.getStatus());
        assertNotNull(response.getVnpayRefundRef());
        assertEquals(100_000L, senderWallet.getAvailable());
        verify(ledgerService, times(1)).recordVNPaySourceRefund(eq(ORDER_ID), eq(SENDER_ID), eq(200_000L), anyString());
    }

    @Test
    @DisplayName("API-091 A1: Khi VNPay từ chối hoàn về nguồn -> giữ nguyên tiền ở ví người gửi và lưu trạng thái FAILED")
    void requestRefundToSource_ProviderRejected_KeepsMoneyInWallet() {
        senderWallet.setAvailable(300_000L);
        Escrow escrow = Escrow.builder()
                .id(53L)
                .orderId(ORDER_ID)
                .senderId(SENDER_ID)
                .amount(200_000L)
                .status(EscrowStatus.REFUNDED)
                .paymentMethod(EscrowPaymentMethod.VNPAY)
                .frozen(false)
                .build();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
        when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));
        when(refundRequestRepository.existsByOrderIdAndStatusIn(any(), any())).thenReturn(false);
        when(walletRepository.findByUserIdForUpdate(SENDER_ID)).thenReturn(Optional.of(senderWallet));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(inv -> {
            RefundRequest r = inv.getArgument(0);
            r.setId(701L);
            return r;
        });

        RefundToSourceResponse response = orderPaymentService.requestRefundToSource(
                ORDER_ID,
                SENDER_ID,
                RefundToSourceRequest.builder().simulateProviderRejection(true).build()
        );

        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals(300_000L, senderWallet.getAvailable()); // Tiền vẫn giữ nguyên trong ví
        verify(ledgerService, never()).recordVNPaySourceRefund(any(), any(), any(), any());
    }

    @Test
    @DisplayName("API-091 A2: Từ chối hoàn về nguồn VNPay nếu giao dịch ký quỹ gốc thanh toán bằng Ví (WALLET)")
    void requestRefundToSource_WalletPayment_ThrowsNotVNPay() {
        Escrow escrow = Escrow.builder()
                .id(54L)
                .orderId(ORDER_ID)
                .senderId(SENDER_ID)
                .amount(200_000L)
                .status(EscrowStatus.REFUNDED)
                .paymentMethod(EscrowPaymentMethod.WALLET)
                .frozen(false)
                .build();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));
        when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));

        AppException ex = assertThrows(AppException.class, () ->
                orderPaymentService.requestRefundToSource(ORDER_ID, SENDER_ID, new RefundToSourceRequest()));

        assertEquals(PaymentErrorCode.REFUND_SOURCE_NOT_VNPAY, ex.getErrorCode());
    }
}

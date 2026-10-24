package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.model.Role;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.EscrowReleaseResponse;
import com.fptpost.modules.payment.dto.EscrowResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.*;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaymentService & Escrow Service Unit Tests (UC-36)")
class OrderPaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private EscrowRepository escrowRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private VNPayConfig vnPayConfig;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @InjectMocks
    private OrderPaymentService orderPaymentService;

    private LedgerService realLedgerService;

    private static final Long ORDER_ID = 10L;
    private static final String ORDER_CODE = "ORD-20261024-001";
    private static final Long SENDER_ID = 100L;
    private static final Long COURIER_ID = 200L;
    private static final Long ADMIN_ID = 999L;
    private static final Long OTHER_USER_ID = 555L;
    private static final Long ESCROW_AMOUNT = 150_000L;

    @BeforeEach
    void setUp() {
        realLedgerService = new LedgerService(ledgerTransactionRepository);
    }

    @Nested
    @DisplayName("1. Logic giải ngân tiền ký quỹ (releaseEscrow)")
    class ReleaseEscrowTests {

        @Test
        @DisplayName("Giải ngân thành công từ Escrow WALLET: tiền về ví shipper, giảm escrowHeld sender, status RELEASED, gọi sổ cái")
        void releaseEscrow_Success_FromWallet() {
            // Arrange
            Order order = Order.builder()
                    .id(ORDER_ID)
                    .orderCode(ORDER_CODE)
                    .senderId(SENDER_ID)
                    .courierId(COURIER_ID)
                    .status(OrderStatus.COMPLETED)
                    .declaredValue(BigDecimal.valueOf(ESCROW_AMOUNT))
                    .build();

            Escrow escrow = Escrow.builder()
                    .id(1L)
                    .orderId(ORDER_ID)
                    .senderId(SENDER_ID)
                    .amount(ESCROW_AMOUNT)
                    .status(EscrowStatus.HELD)
                    .paymentMethod(EscrowPaymentMethod.WALLET)
                    .frozen(false)
                    .build();

            Wallet senderWallet = Wallet.builder()
                    .id(1L)
                    .userId(SENDER_ID)
                    .available(500_000L)
                    .escrowHeld(ESCROW_AMOUNT)
                    .build();

            Wallet courierWallet = Wallet.builder()
                    .id(2L)
                    .userId(COURIER_ID)
                    .available(50_000L)
                    .escrowHeld(0L)
                    .build();

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));
            when(walletRepository.findByUserIdForUpdate(SENDER_ID)).thenReturn(Optional.of(senderWallet));
            when(walletRepository.findByUserIdForUpdate(COURIER_ID)).thenReturn(Optional.of(courierWallet));

            // Act
            EscrowReleaseResponse response = orderPaymentService.releaseEscrow(ORDER_ID, ADMIN_ID, true);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(response.getOrderCode()).isEqualTo(ORDER_CODE);
            assertThat(response.getCourierId()).isEqualTo(COURIER_ID);
            assertThat(response.getAmountReleased()).isEqualTo(ESCROW_AMOUNT);
            assertThat(response.getStatus()).isEqualTo(EscrowStatus.RELEASED);
            assertThat(response.getReleasedAt()).isNotNull();

            // Kiểm tra số dư ví sender bị trừ escrowHeld
            assertThat(senderWallet.getEscrowHeld()).isEqualTo(0L);
            verify(walletRepository).save(senderWallet);

            // Kiểm tra số dư ví shipper được cộng available
            assertThat(courierWallet.getAvailable()).isEqualTo(200_000L);
            verify(walletRepository).save(courierWallet);

            // Kiểm tra trạng thái Escrow chuyển sang RELEASED
            assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);
            assertThat(escrow.getReleaseAt()).isNotNull();
            verify(escrowRepository).save(escrow);

            // Kiểm tra gọi sổ cái kép ghi nhận ESCROW_RELEASE
            verify(ledgerService).recordEscrowRelease(eq(ORDER_ID), eq(SENDER_ID), eq(COURIER_ID), eq(ESCROW_AMOUNT), eq("ESCROW_ORD_" + ORDER_ID));
        }

        @Test
        @DisplayName("Giải ngân thành công từ Escrow VNPAY: tiền về ví shipper, không trừ escrowHeld ví sender")
        void releaseEscrow_Success_FromVNPay() {
            // Arrange
            Order order = Order.builder()
                    .id(ORDER_ID)
                    .orderCode(ORDER_CODE)
                    .senderId(SENDER_ID)
                    .courierId(COURIER_ID)
                    .status(OrderStatus.COMPLETED)
                    .build();

            Escrow escrow = Escrow.builder()
                    .id(1L)
                    .orderId(ORDER_ID)
                    .senderId(SENDER_ID)
                    .amount(ESCROW_AMOUNT)
                    .status(EscrowStatus.HELD)
                    .paymentMethod(EscrowPaymentMethod.VNPAY)
                    .frozen(false)
                    .build();

            Wallet courierWallet = Wallet.builder()
                    .id(2L)
                    .userId(COURIER_ID)
                    .available(100_000L)
                    .build();

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));
            when(walletRepository.findByUserIdForUpdate(COURIER_ID)).thenReturn(Optional.of(courierWallet));

            // Act
            EscrowReleaseResponse response = orderPaymentService.releaseEscrow(ORDER_ID, ADMIN_ID, true);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(EscrowStatus.RELEASED);
            assertThat(courierWallet.getAvailable()).isEqualTo(250_000L);
            verify(walletRepository, never()).findByUserIdForUpdate(SENDER_ID);
            verify(escrowRepository).save(escrow);
            verify(ledgerService).recordEscrowRelease(ORDER_ID, SENDER_ID, COURIER_ID, ESCROW_AMOUNT, "ESCROW_ORD_" + ORDER_ID);
        }

        @Test
        @DisplayName("Lỗi: Ký quỹ bị phong tỏa (frozen = true) do khiếu nại -> từ chối giải ngân (ESCROW_FROZEN)")
        void releaseEscrow_Failed_WhenEscrowFrozen() {
            Order order = Order.builder().id(ORDER_ID).courierId(COURIER_ID).senderId(SENDER_ID).build();
            Escrow escrow = Escrow.builder()
                    .id(1L)
                    .orderId(ORDER_ID)
                    .status(EscrowStatus.HELD)
                    .frozen(true)
                    .build();

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));

            assertThatThrownBy(() -> orderPaymentService.releaseEscrow(ORDER_ID, ADMIN_ID, true))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.ESCROW_FROZEN);

            verify(walletRepository, never()).save(any());
            verify(ledgerService, never()).recordEscrowRelease(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Lỗi: Đơn hàng chưa gán shipper (courierId = null) -> từ chối giải ngân (ORDER_COURIER_NOT_ASSIGNED)")
        void releaseEscrow_Failed_WhenCourierNotAssigned() {
            Order order = Order.builder().id(ORDER_ID).courierId(null).senderId(SENDER_ID).build();

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderPaymentService.releaseEscrow(ORDER_ID, ADMIN_ID, true))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.ORDER_COURIER_NOT_ASSIGNED);

            verify(escrowRepository, never()).save(any());
            verify(ledgerService, never()).recordEscrowRelease(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Lỗi: Giải ngân lặp lại khi đã RELEASED trước đó -> ném ESCROW_ALREADY_RELEASED")
        void releaseEscrow_Failed_WhenAlreadyReleased() {
            Order order = Order.builder().id(ORDER_ID).courierId(COURIER_ID).senderId(SENDER_ID).build();
            Escrow escrow = Escrow.builder()
                    .id(1L)
                    .orderId(ORDER_ID)
                    .status(EscrowStatus.RELEASED)
                    .frozen(false)
                    .build();

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));

            assertThatThrownBy(() -> orderPaymentService.releaseEscrow(ORDER_ID, ADMIN_ID, true))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.ESCROW_ALREADY_RELEASED);

            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lỗi: Trạng thái Escrow không phải HELD (ví dụ REFUNDED) -> ném ESCROW_STATUS_INVALID")
        void releaseEscrow_Failed_WhenStatusNotHeld() {
            Order order = Order.builder().id(ORDER_ID).courierId(COURIER_ID).senderId(SENDER_ID).build();
            Escrow escrow = Escrow.builder()
                    .id(1L)
                    .orderId(ORDER_ID)
                    .status(EscrowStatus.REFUNDED)
                    .frozen(false)
                    .build();

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(escrow));

            assertThatThrownBy(() -> orderPaymentService.releaseEscrow(ORDER_ID, ADMIN_ID, true))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.ESCROW_STATUS_INVALID);
        }

        @Test
        @DisplayName("Lỗi: Không tìm thấy đơn hàng -> ném ORDER_NOT_FOUND")
        void releaseEscrow_Failed_WhenOrderNotFound() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderPaymentService.releaseEscrow(ORDER_ID, ADMIN_ID, true))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("Lỗi: Không tìm thấy khoản ký quỹ Escrow -> ném ESCROW_NOT_FOUND")
        void releaseEscrow_Failed_WhenEscrowNotFound() {
            Order order = Order.builder().id(ORDER_ID).courierId(COURIER_ID).senderId(SENDER_ID).build();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderPaymentService.releaseEscrow(ORDER_ID, ADMIN_ID, true))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.ESCROW_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("2. Tra cứu thông tin ký quỹ đơn hàng (getEscrowByOrderId)")
    class GetEscrowTests {

        private Order sampleOrder;
        private Escrow sampleEscrow;

        @BeforeEach
        void initData() {
            sampleOrder = Order.builder()
                    .id(ORDER_ID)
                    .orderCode(ORDER_CODE)
                    .senderId(SENDER_ID)
                    .courierId(COURIER_ID)
                    .build();

            sampleEscrow = Escrow.builder()
                    .id(1L)
                    .orderId(ORDER_ID)
                    .senderId(SENDER_ID)
                    .amount(ESCROW_AMOUNT)
                    .status(EscrowStatus.HELD)
                    .paymentMethod(EscrowPaymentMethod.WALLET)
                    .releaseAt(Instant.now().plusSeconds(3600))
                    .frozen(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
        }

        @Test
        @DisplayName("Người gửi (Sender) xem ký quỹ đơn của mình -> thành công 200")
        void getEscrow_Success_ForSender() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(sampleEscrow));

            EscrowResponse response = orderPaymentService.getEscrowByOrderId(ORDER_ID, SENDER_ID, false);

            assertThat(response).isNotNull();
            assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(response.getSenderId()).isEqualTo(SENDER_ID);
            assertThat(response.getCourierId()).isEqualTo(COURIER_ID);
            assertThat(response.getAmount()).isEqualTo(ESCROW_AMOUNT);
            assertThat(response.getStatus()).isEqualTo(EscrowStatus.HELD);
        }

        @Test
        @DisplayName("Người giao (Courier) xem ký quỹ đơn mình nhận giao -> thành công 200")
        void getEscrow_Success_ForCourier() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(sampleEscrow));

            EscrowResponse response = orderPaymentService.getEscrowByOrderId(ORDER_ID, COURIER_ID, false);

            assertThat(response).isNotNull();
            assertThat(response.getCourierId()).isEqualTo(COURIER_ID);
        }

        @Test
        @DisplayName("Quản trị viên (Admin) xem ký quỹ bất kỳ đơn nào -> thành công 200")
        void getEscrow_Success_ForAdmin() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(sampleEscrow));

            EscrowResponse response = orderPaymentService.getEscrowByOrderId(ORDER_ID, ADMIN_ID, true);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Người dùng lạ không phải sender/courier/admin -> ném ESCROW_ACCESS_DENIED (403)")
        void getEscrow_Failed_WhenAccessDenied() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(sampleOrder));
            when(escrowRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(sampleEscrow));
            when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(User.builder().id(OTHER_USER_ID).roles(Set.of(Role.builder().code("SENDER").build())).build()));

            assertThatThrownBy(() -> orderPaymentService.getEscrowByOrderId(ORDER_ID, OTHER_USER_ID, false))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PaymentErrorCode.ESCROW_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("3. Sổ cái kép (Double-Entry Ledger) - recordEscrowRelease")
    class LedgerServiceTests {

        @Test
        @DisplayName("Ghi sổ cái giải ngân ghi đúng 2 dòng Nợ/Có (Debit ESCROW_ORDER, Credit USER_WALLET) và cân bằng Nợ/Có")
        void recordEscrowRelease_WritesCorrectDebitCreditEntries() {
            when(ledgerTransactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(ledgerTransactionRepository.save(any(LedgerTransaction.class))).thenAnswer(i -> i.getArgument(0));

            LedgerTransaction txn = realLedgerService.recordEscrowRelease(ORDER_ID, SENDER_ID, COURIER_ID, ESCROW_AMOUNT, "ESCROW_ORD_" + ORDER_ID);

            assertThat(txn).isNotNull();
            assertThat(txn.getType()).isEqualTo(LedgerTransactionType.ESCROW_RELEASE);
            assertThat(txn.getStatus()).isEqualTo(LedgerTransactionStatus.COMPLETED);
            assertThat(txn.getAmount()).isEqualTo(ESCROW_AMOUNT);
            assertThat(txn.getIdempotencyKey()).isEqualTo("ESCROW_RELEASE_" + ORDER_ID + "_ESCROW_ORD_" + ORDER_ID);

            // Kiểm tra 2 dòng bút toán Nợ/Có
            assertThat(txn.getEntries()).hasSize(2);

            // Bút toán Nợ (Debit): ESCROW_ORDER_{orderId} (Giảm nghĩa vụ ký quỹ giam giữ)
            LedgerEntry debitEntry = txn.getEntries().stream()
                    .filter(e -> ("ESCROW_ORDER_" + ORDER_ID).equals(e.getAccountCode()))
                    .findFirst().orElseThrow();
            assertThat(debitEntry.getAccountType()).isEqualTo("LIABILITY");
            assertThat(debitEntry.getDebit()).isEqualTo(ESCROW_AMOUNT);
            assertThat(debitEntry.getCredit()).isEqualTo(0L);

            // Bút toán Có (Credit): USER_WALLET_{courierId} (Tăng nghĩa vụ ví khả dụng của courier)
            LedgerEntry creditEntry = txn.getEntries().stream()
                    .filter(e -> ("USER_WALLET_" + COURIER_ID).equals(e.getAccountCode()))
                    .findFirst().orElseThrow();
            assertThat(creditEntry.getAccountType()).isEqualTo("LIABILITY");
            assertThat(creditEntry.getDebit()).isEqualTo(0L);
            assertThat(creditEntry.getCredit()).isEqualTo(ESCROW_AMOUNT);

            // Nguyên tắc kế toán kép: Tổng Nợ == Tổng Có
            long totalDebit = txn.getEntries().stream().mapToLong(LedgerEntry::getDebit).sum();
            long totalCredit = txn.getEntries().stream().mapToLong(LedgerEntry::getCredit).sum();
            assertThat(totalDebit).isEqualTo(totalCredit).isEqualTo(ESCROW_AMOUNT);
        }

        @Test
        @DisplayName("Chống ghi nhận trùng sổ cái (Idempotency Key)")
        void recordEscrowRelease_Idempotency() {
            String idemKey = "ESCROW_RELEASE_" + ORDER_ID + "_ESCROW_ORD_" + ORDER_ID;
            LedgerTransaction existing = LedgerTransaction.builder()
                    .id(50L)
                    .idempotencyKey(idemKey)
                    .amount(ESCROW_AMOUNT)
                    .build();

            when(ledgerTransactionRepository.existsByIdempotencyKey(idemKey)).thenReturn(true);
            when(ledgerTransactionRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.of(existing));

            LedgerTransaction result = realLedgerService.recordEscrowRelease(ORDER_ID, SENDER_ID, COURIER_ID, ESCROW_AMOUNT, "ESCROW_ORD_" + ORDER_ID);

            assertThat(result).isSameAs(existing);
            verify(ledgerTransactionRepository, never()).save(any());
        }
    }
}

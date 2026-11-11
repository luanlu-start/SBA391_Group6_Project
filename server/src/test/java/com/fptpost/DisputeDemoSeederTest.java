package com.fptpost;

import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import com.fptpost.modules.chat.repository.ChatMessageRepository;
import com.fptpost.modules.chat.repository.ChatRoomRepository;
import com.fptpost.modules.dispute.config.DemoAccountLookup;
import com.fptpost.modules.dispute.config.DisputeDemoSeeder;
import com.fptpost.modules.dispute.enums.ComplaintStatus;
import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.dispute.repository.ComplaintEvidenceRepository;
import com.fptpost.modules.dispute.repository.ComplaintMessageRepository;
import com.fptpost.modules.dispute.repository.ComplaintRepository;
import com.fptpost.modules.dispute.service.ComplaintLifecycle;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.notification.repository.NotificationDeliveryRepository;
import com.fptpost.modules.notification.repository.NotificationEventRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.model.LedgerEntry;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.service.LedgerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DisputeDemoSeederTest extends IdentityFeatureHttpTest {
    @Autowired DemoAccountLookup lookup;
    @Autowired OrderRepository orders;
    @Autowired EscrowRepository escrows;
    @Autowired WalletRepository wallets;
    @Autowired LedgerService ledgerService;
    @Autowired LedgerTransactionRepository ledger;
    @Autowired ComplaintLifecycle lifecycle;
    @Autowired ComplaintRepository complaints;
    @Autowired ComplaintMessageRepository complaintMessages;
    @Autowired ComplaintEvidenceRepository complaintEvidence;
    @Autowired NotificationEventRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;
    ChatRoomRepository chatRooms = mock(ChatRoomRepository.class);
    ChatMessageRepository chatMessages = mock(ChatMessageRepository.class);
    User sender;

    @BeforeEach void demoAccounts() {
        clean();
        when(chatRooms.save(any())).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId("room-1");
            return room;
        });
        deliveries.deleteAll(); notifications.deleteAll();
        sender = account("sender@fptpost.vn", "SENDER", 1);
        account("courier@fptpost.vn", "COURIER", 2);
        account("moderator@fptpost.vn", "MODERATOR", 3);
    }

    @AfterEach void clean() {
        complaintEvidence.deleteAll(); complaintMessages.deleteAll(); complaints.deleteAll();
        ledger.deleteAll(); escrows.deleteAll(); wallets.deleteAll(); orders.deleteAll();
    }

    @Test void demoDataCoversTheComplaintAndCompensationFlowsAndIsNotDuplicated() {
        seeder().seed();

        List<Order> seeded = orders.findAll();
        assertThat(seeded).filteredOn(order -> order.getOrderCode().startsWith("DEMO-HT-"))
                .hasSize(6).allMatch(order -> order.getStatus() == OrderStatus.COMPLETED && order.getEndedAt() != null);
        Order open = seeded.stream().filter(order -> order.getOrderCode().startsWith("DEMO-KN-")).findFirst().orElseThrow();
        assertThat(open.getStatus()).isEqualTo(OrderStatus.DISPUTED);
        assertThat(escrows.findByOrderId(open.getId()).orElseThrow().isFrozen()).isTrue();

        Order compensated = orders.findByOrderCode("DEMO-BT-01").orElseThrow();
        assertThat(compensated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(escrows.findByOrderId(compensated.getId()).orElseThrow().getStatus()).isEqualTo(EscrowStatus.REFUNDED);
        assertThat(wallets.findByUserId(sender.getId()).orElseThrow().getAvailable()).isEqualTo(90_000);

        List<Complaint> created = complaints.findAll();
        assertThat(created).extracting(Complaint::getStatus).containsExactlyInAnyOrder(ComplaintStatus.OPEN, ComplaintStatus.CLOSED);
        assertThat(created).filteredOn(item -> item.getStatus() == ComplaintStatus.CLOSED)
                .singleElement().satisfies(item -> assertThat(item.getCompensationAmount()).isEqualTo(400_000));
        assertThat(complaintEvidence.count()).isEqualTo(2);
        Boolean balanced = new TransactionTemplate(transactions).execute(status -> ledger.findAll().stream().allMatch(transaction ->
                transaction.getEntries().stream().mapToLong(LedgerEntry::getDebit).sum()
                        == transaction.getEntries().stream().mapToLong(LedgerEntry::getCredit).sum()));
        assertThat(balanced).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChatMessage>> chat = ArgumentCaptor.forClass(Iterable.class);
        verify(chatMessages).saveAll(chat.capture());
        assertThat(chat.getValue()).hasSize(4).allMatch(message -> "room-1".equals(message.getRoomId()));
        assertThat(chat.getValue()).extracting(ChatMessage::getAuthorId).contains(sender.getId(), open.getCourierId());

        seeder().seed();
        assertThat(orders.count()).isEqualTo(8);
        assertThat(complaints.count()).isEqualTo(2);
        verify(chatRooms, times(1)).save(any());
    }

    @Test void chatStoreFailureDoesNotUndoTheComplaintData() {
        doThrow(new IllegalStateException("chat store down")).when(chatRooms).save(any());

        seeder().seed();

        assertThat(complaints.count()).isEqualTo(2);
        verifyNoInteractions(chatMessages);
    }

    private DisputeDemoSeeder seeder() {
        return new DisputeDemoSeeder(lookup, orders, escrows, ledgerService, lifecycle, transactions, identityClock,
                chatRooms, chatMessages);
    }

    private User account(String email, String role, int offset) {
        User result = createUser(email, "+847" + String.format(Locale.ROOT, "%08d", sequence.incrementAndGet() + offset));
        Set<com.fptpost.modules.identity.model.Role> assigned = new HashSet<>(Set.of(roles.findByCode(role).orElseThrow()));
        result.setRoles(assigned);
        return users.saveAndFlush(result);
    }
}

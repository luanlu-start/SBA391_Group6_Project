package com.fptpost.modules.dispute.dto.response;

import com.fptpost.modules.dispute.enums.*;
import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import com.fptpost.modules.payment.model.EscrowStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ComplaintDetailResponse {

    private UUID id;
    private String code;
    private ComplaintStatus status;
    private ComplaintReason reason;
    private String description;
    private ComplaintRole openerRole;
    private ComplaintPerson openedBy;
    private ComplaintPerson sender;
    private ComplaintPerson courier;
    private ComplaintPerson assignee;
    private OrderView order;
    private EscrowView escrow;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant firstResponseAt;
    private Instant firstResponseDueAt;
    private Instant resolveDueAt;
    private boolean responseOverdue;
    private boolean resolutionOverdue;
    private RulingView ruling;
    private AppealView appeal;
    private SettlementView settlement;
    private List<MessageView> messages;
    private ComplaintRole viewerRole;
    private Permissions permissions;

    public record OrderView(Long id, String orderCode, String productName, String status, BigDecimal declaredValue,
                            BigDecimal priceAgreed, boolean insured) {
    }

    public record EscrowView(long amount, EscrowStatus status, boolean frozen, EscrowPaymentMethod paymentMethod) {
    }

    public record RulingView(ComplaintOutcome outcome, Integer senderPercent, long refundAmount, long courierAmount,
                             long compensationAmount, String reason, ComplaintPerson ruledBy, Instant ruledAt,
                             Instant appealDeadline) {
    }

    public record AppealView(ComplaintPerson appealedBy, String reason, Instant appealedAt, AppealDecision decision,
                             String note, ComplaintPerson reviewedBy, Instant reviewedAt) {
    }

    public record SettlementView(long clawbackAmount, long unrecoveredAmount, Instant closedAt) {
    }

    public record MessageView(UUID id, ComplaintRole authorRole, ComplaintPerson author, String body,
                              Instant createdAt, List<UUID> evidenceIds) {
    }

    public record Permissions(boolean claim, boolean message, boolean requestInfo, boolean rule, boolean appeal,
                              boolean reviewAppeal) {
    }
}

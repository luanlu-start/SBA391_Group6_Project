package com.fptpost.modules.deliveryreturn.dto;

import com.fptpost.modules.deliveryreturn.model.*;
import com.fptpost.modules.order.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReturnResponse(Long orderId, OrderStatus orderStatus, boolean canReport, boolean canConfirm, boolean canResume,
                             int requiredCalls, int contactWindowMinutes, List<Contact> contacts,
                             Case latestCase, List<History> history) {
    public record Contact(Long id, String note, Instant createdAt) {
        public static Contact from(DeliveryContactAttempt value) { return new Contact(value.getId(), value.getNote(), value.getCreatedAt()); }
    }
    public record Case(Long id, ReturnState state, FailureReason reason, String description, Instant createdAt,
                       Instant resolvedAt, BigDecimal deliveryFee, BigDecimal returnFee, BigDecimal returnRate,
                       boolean financialSettlementPending) {
        public static Case from(DeliveryReturnCase value) {
            return new Case(value.getId(), value.getState(), value.getReason(), value.getDescription(), value.getCreatedAt(),
                    value.getResolvedAt(), value.getDeliveryFee(), value.getReturnFee(), value.getReturnRate(), value.getState() != ReturnState.RESUMED);
        }
    }
    public record History(Long id, Long returnCaseId, String action, OrderStatus fromStatus, OrderStatus toStatus,
                          String note, Instant createdAt) {
        public static History from(DeliveryReturnHistory value) { return new History(value.getId(), value.getReturnCaseId(), value.getAction(),
                value.getFromStatus(), value.getToStatus(), value.getNote(), value.getCreatedAt()); }
    }
    public record Code(String code, Instant expiresAt) {
        @Override public String toString() { return "ReturnCode[redacted]"; }
    }
}

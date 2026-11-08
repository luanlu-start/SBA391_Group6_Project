package com.fptpost.modules.payment.service.payout;

public record PayoutResult(Outcome outcome, String reference, String failureReason) {

    public enum Outcome {
        /** Chờ Admin chuyển khoản thủ công rồi nhập mã tham chiếu hoặc xuất lệnh chi. */
        AWAITING_MANUAL_TRANSFER,
        /** Nhà cung cấp đã nhận lệnh, kết quả về sau. */
        SUBMITTED,
        PAID,
        FAILED
    }

    public static PayoutResult awaitingManualTransfer() {
        return new PayoutResult(Outcome.AWAITING_MANUAL_TRANSFER, null, null);
    }

    public static PayoutResult submitted() {
        return new PayoutResult(Outcome.SUBMITTED, null, null);
    }

    public static PayoutResult paid(String reference) {
        return new PayoutResult(Outcome.PAID, reference, null);
    }

    public static PayoutResult failed(String reason) {
        return new PayoutResult(Outcome.FAILED, null, reason);
    }
}

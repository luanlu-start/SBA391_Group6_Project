package com.fptpost.modules.identity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter @RequiredArgsConstructor
public enum KycRejectionReason {
    UNREADABLE_DOCUMENT("Ảnh giấy tờ không rõ", false),
    INFORMATION_MISMATCH("Thông tin không khớp", false),
    EXPIRED_DOCUMENT("Giấy tờ đã hết hạn", false),
    INVALID_SELFIE("Ảnh chân dung không hợp lệ", false),
    OTHER("Lý do khác", true);

    private final String label;
    private final boolean noteRequired;
}

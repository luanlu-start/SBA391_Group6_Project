package com.fptpost.modules.dispute.enums;

import java.util.EnumSet;
import java.util.Set;

/** Nhóm trạng thái hiển thị trên hàng đợi kiểm duyệt. */
public enum ComplaintQueueFilter {
    ACTIVE(ComplaintStatus.ACTIVE),
    OPEN(EnumSet.of(ComplaintStatus.OPEN)),
    INVESTIGATING(EnumSet.of(ComplaintStatus.INVESTIGATING)),
    AWAITING_INFO(EnumSet.of(ComplaintStatus.AWAITING_INFO)),
    RESOLVED(EnumSet.of(ComplaintStatus.RESOLVED)),
    APPEALED(EnumSet.of(ComplaintStatus.APPEALED)),
    CLOSED(EnumSet.of(ComplaintStatus.CLOSED)),
    ALL(EnumSet.allOf(ComplaintStatus.class));

    private final Set<ComplaintStatus> statuses;

    ComplaintQueueFilter(Set<ComplaintStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<ComplaintStatus> statuses() {
        return statuses;
    }
}

package com.fptpost.modules.dispute.enums;

import java.util.EnumSet;
import java.util.Set;

/** Trạng thái khiếu nại theo bảng trạng thái của đặc tả (DSP). */
public enum ComplaintStatus {
    OPEN,
    INVESTIGATING,
    AWAITING_INFO,
    RESOLVED,
    APPEALED,
    CLOSED;

    public static final Set<ComplaintStatus> ACTIVE = EnumSet.of(OPEN, INVESTIGATING, AWAITING_INFO);
    public static final Set<ComplaintStatus> UNSETTLED = EnumSet.of(OPEN, INVESTIGATING, AWAITING_INFO, RESOLVED, APPEALED);
}

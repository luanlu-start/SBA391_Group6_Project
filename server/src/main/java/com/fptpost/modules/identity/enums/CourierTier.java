package com.fptpost.modules.identity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * Hạng thành viên của Người giao (Courier Tier).
 * Quy định theo PRM-079..084 và các Business Rules (BR-R-024, BR-R-028).
 */
@Getter
@RequiredArgsConstructor
public enum CourierTier {
    NEW("Tân binh", 3, new BigDecimal("3000000"), false, new BigDecimal("0.08")),
    BRONZE("Đồng", 5, new BigDecimal("6000000"), false, new BigDecimal("0.07")),
    SILVER("Bạc", 8, new BigDecimal("10000000"), true, new BigDecimal("0.06")),
    GOLD("Vàng", 12, new BigDecimal("15000000"), true, new BigDecimal("0.05")),
    DIAMOND("Kim cương", 15, new BigDecimal("20000000"), true, new BigDecimal("0.04"));

    private final String displayName;
    private final int maxConcurrentOrders;          // PRM-083: Tân binh 3 / Đồng 5 / Bạc 8 / Vàng 12 / Kim cương 15
    private final BigDecimal maxTotalDeclaredValue; // PRM-084: Tân binh 3M / Đồng 6M / Bạc 10M / Vàng 15M / Kim cương 20M
    private final boolean canAcceptHighValue;        // BR-R-024: Bạc trở lên mới được nhận đơn > 1.000.000đ
    private final BigDecimal commissionRate;         // PRM-042..046: Tân binh 8%, Đồng 7%, Bạc 6%, Vàng 5%, Kim cương 4%
}

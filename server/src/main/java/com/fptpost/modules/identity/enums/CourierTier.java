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
    NEW("Tân binh", 3, new BigDecimal("3000000"), false),
    BRONZE("Đồng", 5, new BigDecimal("6000000"), false),
    SILVER("Bạc", 8, new BigDecimal("10000000"), true),
    GOLD("Vàng", 12, new BigDecimal("15000000"), true),
    DIAMOND("Kim cương", 15, new BigDecimal("20000000"), true);

    private final String displayName;
    private final int maxConcurrentOrders;          // PRM-083: Tân binh 3 / Đồng 5 / Bạc 8 / Vàng 12 / Kim cương 15
    private final BigDecimal maxTotalDeclaredValue; // PRM-084: Tân binh 3M / Đồng 6M / Bạc 10M / Vàng 15M / Kim cương 20M
    private final boolean canAcceptHighValue;        // BR-R-024: Bạc trở lên mới được nhận đơn > 1.000.000đ
}

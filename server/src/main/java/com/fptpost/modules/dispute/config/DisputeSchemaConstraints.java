package com.fptpost.modules.dispute.config;

import com.fptpost.common.persistence.EnumCheckConstraints;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Bổ sung trạng thái DISPUTED, PARTIALLY_REFUNDED và loại bút toán mới vào CHECK constraint của bảng đã có dữ liệu. */
@Component
@RequiredArgsConstructor
public class DisputeSchemaConstraints implements ApplicationRunner {

    private final EnumCheckConstraints enumCheckConstraints;

    @Override
    public void run(ApplicationArguments arguments) {
        enumCheckConstraints.synchronize("orders", "status", OrderStatus.class);
        enumCheckConstraints.synchronize("escrows", "status", EscrowStatus.class);
        enumCheckConstraints.synchronize("ledger_transactions", "type", LedgerTransactionType.class);
    }
}

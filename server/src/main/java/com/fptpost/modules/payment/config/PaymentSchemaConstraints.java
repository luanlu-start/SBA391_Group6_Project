package com.fptpost.modules.payment.config;

import com.fptpost.common.persistence.EnumCheckConstraints;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import com.fptpost.modules.payment.model.RefundKind;
import com.fptpost.modules.payment.model.RefundStatus;
import com.fptpost.modules.payment.model.WithdrawalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSchemaConstraints implements ApplicationRunner {

    private final EnumCheckConstraints enumCheckConstraints;

    @Override
    public void run(ApplicationArguments arguments) {
        enumCheckConstraints.synchronize("withdrawal_requests", "status", WithdrawalStatus.class);
        enumCheckConstraints.synchronize("ledger_transactions", "type", LedgerTransactionType.class);
        enumCheckConstraints.synchronize("refund_requests", "kind", RefundKind.class);
        enumCheckConstraints.synchronize("refund_requests", "status", RefundStatus.class);
    }
}

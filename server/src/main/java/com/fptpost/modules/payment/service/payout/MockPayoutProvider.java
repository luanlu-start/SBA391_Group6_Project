package com.fptpost.modules.payment.service.payout;

import com.fptpost.modules.payment.model.WithdrawalRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Demo provider: settles immediately so the full flow can run without a disbursement contract. */
@Component
@ConditionalOnProperty(name = "app.payout.provider", havingValue = "mock")
public class MockPayoutProvider implements PayoutProvider {

    @Override
    public String code() {
        return "MOCK";
    }

    @Override
    public PayoutResult submit(WithdrawalRequest withdrawal) {
        return PayoutResult.paid("MOCK-" + withdrawal.getRequestCode());
    }
}

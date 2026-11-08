package com.fptpost.modules.payment.service.payout;

import com.fptpost.modules.payment.model.WithdrawalRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payout.provider", havingValue = "manual", matchIfMissing = true)
public class ManualPayoutProvider implements PayoutProvider {

    @Override
    public String code() {
        return "MANUAL";
    }

    @Override
    public PayoutResult submit(WithdrawalRequest withdrawal) {
        return PayoutResult.awaitingManualTransfer();
    }
}

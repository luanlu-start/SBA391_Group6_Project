package com.fptpost.modules.dispute.config;

import com.fptpost.common.persistence.EnumCheckConstraints;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Bổ sung trạng thái DISPUTED, PARTIALLY_REFUNDED và loại bút toán mới vào CHECK constraint của bảng đã có dữ liệu.
 * Database dùng chung khi phát triển có thể chứa giá trị của nhánh khác chưa có trong bản build này; khi đó chỉ cảnh báo
 * để server vẫn khởi động, constraint đầy đủ được tạo lại khi chạy bản đã gộp mọi nhánh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeSchemaConstraints implements ApplicationRunner {

    private final EnumCheckConstraints enumCheckConstraints;

    @Override
    public void run(ApplicationArguments arguments) {
        synchronize("orders", "status", OrderStatus.class);
        synchronize("escrows", "status", EscrowStatus.class);
        synchronize("ledger_transactions", "type", LedgerTransactionType.class);
    }

    private void synchronize(String table, String column, Class<? extends Enum<?>> type) {
        try {
            enumCheckConstraints.synchronize(table, column, type);
        } catch (DataAccessException exception) {
            log.warn("Could not update the CHECK constraint of {}.{}; existing rows use values unknown to this build", table, column);
        }
    }
}

package com.fptpost.modules.order.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Cột {@code orders.is_relay} là {@code NOT NULL} không có giá trị mặc định. Trên database đã có đơn hàng, SQL Server từ chối
 * lệnh {@code ALTER TABLE ... ADD} do {@code ddl-auto=update} sinh ra, khiến mọi thao tác ghi đơn hàng lỗi "Invalid column name".
 * Khi khởi động, nếu cột còn thiếu thì thêm cột với mặc định {@code 0} (đơn thường, không phải chặng tiếp sức).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class OrderRelayColumn implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!isSqlServer()) return;
        Integer missing = jdbc.queryForObject("""
                SELECT CASE WHEN OBJECT_ID('dbo.orders', 'U') IS NOT NULL AND COL_LENGTH('dbo.orders', 'is_relay') IS NULL
                            THEN 1 ELSE 0 END
                """, Integer.class);
        if (missing == null || missing == 0) return;
        jdbc.execute("ALTER TABLE dbo.orders ADD is_relay bit NOT NULL CONSTRAINT df_orders_is_relay DEFAULT 0");
        log.info("Added orders.is_relay with default 0");
    }

    private boolean isSqlServer() {
        try (Connection connection = dataSource.getConnection()) {
            return "Microsoft SQL Server".equals(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read database metadata", exception);
        }
    }
}

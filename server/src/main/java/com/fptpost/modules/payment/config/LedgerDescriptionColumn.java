package com.fptpost.modules.payment.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Database tạo trước bản này có cột {@code ledger_transactions.description} kiểu {@code varchar}, làm mất dấu tiếng Việt.
 * {@code ddl-auto=update} không đổi kiểu cột đã có nên chuyển sang {@code nvarchar} khi khởi động.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerDescriptionColumn implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!isSqlServer()) return;
        List<String> types = jdbc.queryForList("""
                SELECT ty.name FROM sys.columns c JOIN sys.types ty ON ty.user_type_id = c.user_type_id
                WHERE c.object_id = OBJECT_ID('dbo.ledger_transactions') AND c.name = 'description'
                """, String.class);
        if (types.isEmpty() || !"varchar".equalsIgnoreCase(types.getFirst())) return;
        jdbc.execute("ALTER TABLE dbo.ledger_transactions ALTER COLUMN description nvarchar(500) NULL");
        log.info("Converted ledger_transactions.description to nvarchar(500)");
    }

    private boolean isSqlServer() {
        try (Connection connection = dataSource.getConnection()) {
            return "Microsoft SQL Server".equals(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read database metadata", exception);
        }
    }
}

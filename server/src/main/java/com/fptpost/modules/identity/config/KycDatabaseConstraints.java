package com.fptpost.modules.identity.config;

import java.sql.Connection;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class KycDatabaseConstraints implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    @Override public void run(ApplicationArguments arguments) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().equals("Microsoft SQL Server")) return;
        }
        jdbc.execute("""
                IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'uq_kyc_approved_identity' AND object_id = OBJECT_ID(N'dbo.kyc_requests'))
                    CREATE UNIQUE INDEX uq_kyc_approved_identity ON dbo.kyc_requests(id_number_hash) WHERE status = 'APPROVED'
                """);
    }
}

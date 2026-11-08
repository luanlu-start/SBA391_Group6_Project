package com.fptpost.common.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hibernate tạo CHECK constraint cho cột enum khi tạo bảng nhưng {@code ddl-auto=update} không cập nhật lại,
 * nên giá trị enum mới bị SQL Server từ chối trên database đã có dữ liệu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnumCheckConstraints {

    private static final String IDENTIFIER = "[A-Za-z0-9_]+";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public void synchronize(String table, String column, Class<? extends Enum<?>> enumType) {
        if (!table.matches(IDENTIFIER) || !column.matches(IDENTIFIER)) {
            throw new IllegalArgumentException("Invalid table or column name");
        }
        if (!isSqlServer()) return;
        List<String> values = Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList();
        List<String> outdated = jdbc.query("""
                        SELECT cc.name, cc.definition FROM sys.check_constraints cc
                        JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id
                        WHERE cc.parent_object_id = OBJECT_ID(?) AND c.name = ?
                        """,
                (row, index) -> {
                    String definition = row.getString("definition");
                    boolean current = values.stream().allMatch(value -> definition.contains("'" + value + "'"));
                    return current ? null : row.getString("name");
                },
                "dbo." + table, column).stream().filter(name -> name != null && name.matches(IDENTIFIER)).toList();
        if (outdated.isEmpty()) return;
        for (String name : outdated) {
            jdbc.execute("ALTER TABLE dbo." + table + " DROP CONSTRAINT [" + name + "]");
        }
        String allowed = values.stream().map(value -> "'" + value + "'").collect(Collectors.joining(", "));
        jdbc.execute("ALTER TABLE dbo." + table + " ADD CONSTRAINT [ck_" + table + "_" + column + "] CHECK ("
                + column + " IN (" + allowed + "))");
        log.info("Updated enum CHECK constraint for {}.{} to {} values", table, column, values.size());
    }

    private boolean isSqlServer() {
        try (Connection connection = dataSource.getConnection()) {
            return "Microsoft SQL Server".equals(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read database metadata", exception);
        }
    }
}

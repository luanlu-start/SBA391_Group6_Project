package com.fptpost.modules.guarantee.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Số dư Quỹ bảo đảm (BR-R-064). Sổ cái tài khoản GUARANTEE_FUND là nguồn gốc; dòng này được khóa khi trích quỹ
 * hoặc chi bồi thường để hai thao tác đồng thời không chi vượt số dư.
 */
@Entity
@Table(name = "guarantee_fund_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuaranteeFundAccount {

    public static final long MAIN = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private Long balance;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;

    @Version
    private long version;
}

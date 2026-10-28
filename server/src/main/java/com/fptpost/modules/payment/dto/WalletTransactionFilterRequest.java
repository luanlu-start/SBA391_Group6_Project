package com.fptpost.modules.payment.dto;

import com.fptpost.modules.payment.model.LedgerTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Request lọc lịch sử giao dịch ví (UC-38).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tìm kiếm và lọc lịch sử giao dịch ví")
public class WalletTransactionFilterRequest {

    @Builder.Default
    @Schema(description = "Số trang (bắt đầu từ 0)", example = "0")
    private int page = 0;

    @Builder.Default
    @Schema(description = "Số bản ghi trên mỗi trang", example = "10")
    private int size = 10;

    @Schema(description = "Loại giao dịch sổ cái (WALLET_TOPUP, ORDER_ESCROW_HOLD, ESCROW_RELEASE, ESCROW_REFUND)")
    private LedgerTransactionType type;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Thời điểm bắt đầu lọc (ISO 8601)", example = "2026-10-01T00:00:00Z")
    private Instant fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "Thời điểm kết thúc lọc (ISO 8601)", example = "2026-10-31T23:59:59Z")
    private Instant toDate;

    public void setFromDate(String value) {
        if (value == null || value.isBlank()) {
            this.fromDate = null;
            return;
        }
        try {
            this.fromDate = Instant.parse(value);
        } catch (Exception e) {
            try {
                LocalDate localDate = LocalDate.parse(value);
                this.fromDate = localDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            } catch (Exception ex) {
                this.fromDate = null;
            }
        }
    }

    public void setToDate(String value) {
        if (value == null || value.isBlank()) {
            this.toDate = null;
            return;
        }
        try {
            this.toDate = Instant.parse(value);
        } catch (Exception e) {
            try {
                LocalDate localDate = LocalDate.parse(value);
                this.toDate = localDate.atTime(23, 59, 59, 999_999_999).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            } catch (Exception ex) {
                this.toDate = null;
            }
        }
    }
}

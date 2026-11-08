package com.fptpost.modules.reporting.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import com.fptpost.modules.reporting.dto.response.CourierRevenueResponse;
import com.fptpost.modules.reporting.dto.response.DashboardResponse;
import com.fptpost.modules.reporting.exception.ReportingErrorCode;
import com.fptpost.modules.reporting.repository.ReportingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** UC-59: số liệu dashboard điều hành, doanh thu người giao theo tháng và xuất CSV (mở được bằng Excel). */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class ReportingService {

    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_RANGE_DAYS = 366;
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(VIETNAM);
    private static final List<LedgerTransactionType> DASHBOARD_LEDGER_TYPES = List.of(LedgerTransactionType.ESCROW_RELEASE,
            LedgerTransactionType.WALLET_TOPUP, LedgerTransactionType.WITHDRAWAL_REQUEST);

    private final ReportingRepository reporting;
    private final ParameterService parameterService;
    private final Clock clock;

    public DashboardResponse dashboard(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        Map<String, Long> byStatus = new TreeMap<>();
        for (Object[] row : reporting.countOrdersByStatus(range.start(), range.end())) {
            byStatus.put(String.valueOf(row[0]), (Long) row[1]);
        }
        long created = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long cancelled = byStatus.getOrDefault("CANCELLED", 0L);

        Map<String, long[]> ledger = new HashMap<>();
        for (Object[] row : reporting.ledgerTotals(DASHBOARD_LEDGER_TYPES, range.start(), range.end())) {
            ledger.put(String.valueOf(row[0]), new long[]{(Long) row[1], ((Number) row[2]).longValue()});
        }
        List<Object[]> releases = reporting.escrowReleases(range.start(), range.end());
        Map<Long, BigDecimal> rates = feeRates(releases);
        BigDecimal defaultRate = parameterService.getSnapshot().decimal("FEE_NEW");
        long commission = releases.stream().mapToLong(row -> commission(row, rates, defaultRate)).sum();

        Map<LocalDate, long[]> daily = new TreeMap<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) daily.put(day, new long[2]);
        for (Instant time : reporting.orderCreationTimes(range.start(), range.end())) {
            daily.computeIfPresent(time.atZone(VIETNAM).toLocalDate(), (day, values) -> { values[0]++; return values; });
        }
        for (Object[] row : releases) {
            long amount = amount(row);
            daily.computeIfPresent(((Instant) row[3]).atZone(VIETNAM).toLocalDate(), (day, values) -> { values[1] += amount; return values; });
        }

        return new DashboardResponse(range.from(), range.to(), created, byStatus,
                created == 0 ? 0 : BigDecimal.valueOf(cancelled).divide(BigDecimal.valueOf(created), 4, RoundingMode.HALF_UP).doubleValue(),
                reporting.countNewUsers(range.start(), range.end()),
                reporting.countNewCouriers(range.start(), range.end()),
                reporting.countActiveCouriers(range.start(), range.end()),
                ledger.getOrDefault("ESCROW_RELEASE", new long[2])[1],
                ledger.getOrDefault("ESCROW_RELEASE", new long[2])[0],
                commission,
                ledger.getOrDefault("WALLET_TOPUP", new long[2])[1],
                ledger.getOrDefault("WITHDRAWAL_REQUEST", new long[2])[1],
                daily.entrySet().stream().map(entry -> new DashboardResponse.DailyPoint(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList());
    }

    public List<CourierRevenueResponse> courierRevenue(YearMonth month) {
        if (month == null) throw new AppException(ReportingErrorCode.REPORT_RANGE_INVALID);
        Range range = range(month.atDay(1), month.atEndOfMonth());
        List<Object[]> releases = reporting.escrowReleases(range.start(), range.end());
        Map<Long, BigDecimal> rates = feeRates(releases);
        var snapshot = parameterService.getSnapshot();
        BigDecimal defaultRate = snapshot.decimal("FEE_NEW");
        BigDecimal withholdRate = snapshot.decimal("WITHHOLD");

        Map<Long, long[]> perCourier = new LinkedHashMap<>();
        for (Object[] row : releases) {
            Long courier = (Long) row[0];
            if (courier == null) continue;
            long amount = amount(row);
            long[] totals = perCourier.computeIfAbsent(courier, key -> new long[4]);
            totals[0]++;
            totals[1] += amount;
            totals[2] += commission(row, rates, defaultRate);
            totals[3] += floorRate(amount, withholdRate);
        }
        Map<Long, Object[]> people = perCourier.isEmpty() ? Map.of() : reporting.people(perCourier.keySet()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));
        List<CourierRevenueResponse> result = new ArrayList<>();
        perCourier.forEach((courier, totals) -> {
            Object[] person = people.get(courier);
            result.add(new CourierRevenueResponse(person == null ? null : (UUID) person[1], person == null ? null : (String) person[2],
                    person == null ? null : (String) person[3], totals[0], totals[1], totals[2], totals[3],
                    totals[1] - totals[2] - totals[3]));
        });
        result.sort(Comparator.comparingLong(CourierRevenueResponse::grossAmount).reversed());
        return result;
    }

    public ExportFile export(String type, LocalDate from, LocalDate to, YearMonth month) {
        String kind = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        StringBuilder csv = new StringBuilder("﻿");
        String fileName;
        switch (kind) {
            case "ORDERS" -> {
                Range range = range(from, to);
                line(csv, "order_code", "status", "product_name", "sender_public_id", "courier_public_id", "price_suggested", "price_agreed", "created_at", "ended_at");
                for (Object[] row : reporting.ordersForExport(range.start(), range.end())) {
                    line(csv, text(row[0]), text(row[1]), text(row[2]), text(row[3]), text(row[4]), money(row[5]), money(row[6]),
                            time(row[7]), time(row[8]));
                }
                fileName = "don-hang-" + range.from() + "-" + range.to() + ".csv";
            }
            case "COURIER_REVENUE" -> {
                if (month == null) throw new AppException(ReportingErrorCode.REPORT_RANGE_INVALID);
                line(csv, "courier_id", "full_name", "email", "released_orders", "gross_amount", "commission", "withholding", "net_income");
                for (CourierRevenueResponse row : courierRevenue(month)) {
                    line(csv, text(row.courierPublicId()), text(row.fullName()), text(row.email()), Long.toString(row.releasedOrders()),
                            Long.toString(row.grossAmount()), Long.toString(row.commission()), Long.toString(row.withholding()),
                            Long.toString(row.netIncome()));
                }
                fileName = "doanh-thu-nguoi-giao-" + month + ".csv";
            }
            case "DASHBOARD" -> {
                DashboardResponse dashboard = dashboard(from, to);
                line(csv, "date", "orders_created", "escrow_released_amount");
                for (DashboardResponse.DailyPoint point : dashboard.daily()) {
                    line(csv, point.date().toString(), Long.toString(point.ordersCreated()), Long.toString(point.escrowReleasedAmount()));
                }
                fileName = "tong-quan-" + dashboard.from() + "-" + dashboard.to() + ".csv";
            }
            default -> throw new AppException(ReportingErrorCode.REPORT_TYPE_INVALID);
        }
        return new ExportFile(fileName, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<Long, BigDecimal> feeRates(List<Object[]> releases) {
        Set<Long> orderIds = releases.stream().map(row -> (Long) row[1]).filter(Objects::nonNull).collect(Collectors.toSet());
        if (orderIds.isEmpty()) return Map.of();
        Map<Long, BigDecimal> rates = new HashMap<>();
        for (Object[] row : reporting.feeRates(orderIds)) {
            if (row[1] != null) rates.put((Long) row[0], (BigDecimal) row[1]);
        }
        return rates;
    }

    /** Hoa hồng theo tỷ lệ phí đã khóa trên đơn (BR-F-081); đơn cũ chưa khóa tỷ lệ dùng FEE_NEW. */
    private static long commission(Object[] release, Map<Long, BigDecimal> rates, BigDecimal defaultRate) {
        BigDecimal rate = release[1] == null ? defaultRate : rates.getOrDefault((Long) release[1], defaultRate);
        return floorRate(amount(release), rate);
    }

    /** Làm tròn xuống như mô phỏng giá tham chiếu để số liệu báo cáo khớp với bảng tính phí. */
    private static long floorRate(long amount, BigDecimal rate) {
        return BigDecimal.valueOf(amount).multiply(rate).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    private static long amount(Object[] release) {
        return release[2] == null ? 0 : ((Number) release[2]).longValue();
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.ofInstant(clock.instant(), VIETNAM) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || start.plusDays(MAX_RANGE_DAYS - 1).isBefore(end)) throw new AppException(ReportingErrorCode.REPORT_RANGE_INVALID);
        return new Range(start, end, start.atStartOfDay(VIETNAM).toInstant(), end.plusDays(1).atStartOfDay(VIETNAM).toInstant());
    }

    private static void line(StringBuilder csv, String... cells) {
        csv.append(Arrays.stream(cells).map(ReportingService::cell).collect(Collectors.joining(","))).append("\r\n");
    }

    /** Trích dẫn khi cần và vô hiệu hóa công thức bảng tính trong dữ liệu người dùng nhập. */
    private static String cell(String value) {
        if (value == null) return "";
        String text = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String money(Object value) {
        return value == null ? "" : ((BigDecimal) value).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String time(Object value) {
        return value == null ? "" : EXPORT_TIME.format((Instant) value);
    }

    private record Range(LocalDate from, LocalDate to, Instant start, Instant end) {
    }

    public record ExportFile(String fileName, byte[] content) {
    }
}

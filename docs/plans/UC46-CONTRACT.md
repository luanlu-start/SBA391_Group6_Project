# Contract UC-46 — bản triển khai đợt 1

Ngày 14/10/2026; owner Trần Công Thuận; nhánh `feature/UC-46-ThuanTC-cau-hinh-tham-so`.
Nguồn nghiệp vụ: Excel đang áp dụng, sheet `06_Tham_số`, API-114–116, SCR-57.
Các tên Java bên dưới là interface đã có trong checkout, không còn chỉ là đề xuất của plan.

## C01 — tham số và snapshot nội bộ

`com.fptpost.modules.catalog.service.ParameterService.getSnapshot()` trả
`ParameterSnapshot(values, versions, capturedAt)`. Hai map chứa cùng 109 key, không thể sửa;
giá trị giữ dạng chuỗi chính xác. Có `value(key)`, `decimal(key)` và `integer(key)`.
Không cache; mỗi lần lấy snapshot đọc SQL. Không mở endpoint public cho toàn bộ tham số.

Order/Pricing cần lưu cả giá trị và version đã dùng vào đơn khi chốt giá.
Đơn cũ sử dụng snapshot đã lưu. UC-46 không tự sửa dữ liệu của Order/Identity;
việc các consumer dùng snapshot là trách nhiệm tích hợp tiếp theo của owner tương ứng.
`capturedAt` là thời điểm đọc; version riêng từng key là dấu vết thay đổi thực tế.

`proposedSnapshot(overrides)` áp giá trị thử lên bản đọc và kiểm tra toàn bộ ràng buộc;
không ghi SQL. Versions trong bản preview vẫn là versions nguồn, chưa phải phiên bản đã commit.

Registry: `server/src/main/resources/catalog/parameters.json`, đủ PRM-001–109.
Seed chỉ bổ sung key thiếu, giữ giá trị/version đã chỉnh. Version 0 và effectiveFrom epoch
đại diện giá trị ban đầu; UI hiển thị “Giá trị ban đầu”. `updatedBy` chỉ có sau lần sửa đầu.

## API quản trị

Mọi endpoint yêu cầu JWT/session thật với role ADMIN. Kết quả thành công dùng
`ApiResponse.data`; lỗi dùng Problem Details theo base. Giá trị tham số gửi dạng chuỗi,
ví dụ tỷ lệ 8% là `"0.08"`, không phải `"8"`.

| Method | Path dưới /api/v1/admin/params | Kết quả trong data |
| --- | --- | --- |
| GET | rỗng | Danh sách ParameterResponse |
| PUT | /{key} | ParameterResponse đã lưu |
| PUT | /batch | Danh sách các key được yêu cầu cập nhật |
| GET | /{key}/history?page=0&size=10 | PageResponse, mới nhất theo version trước |
| POST | /simulate | PricingSimulationResponse, Cache-Control: no-store |

ParameterResponse: `prmId,key,group,name,valueType,value,unit,description,version,effectiveFrom,updatedBy`.
History: `id,oldValue,newValue,changedBy,changedAt,version`; id và actor là UUID công khai.
Page bắt đầu 0; size 1–100. Key phân biệt chữ hoa/thường như registry.

Sửa một key (expectedVersion phải lấy từ GET gần nhất):

```json
{"value":"14000","expectedVersion":0}
```

Sửa các trọng số liên quan trong một giao dịch:

```json
{"changes":{"W_ONTIME":{"value":"0.4","expectedVersion":0},"W_RATING":{"value":"0.25","expectedVersion":0}}}
```

Ví dụ trên giả định các trọng số còn lại đang ở mặc định và các key ở version 0.
PUT batch và GET history là API bổ sung để thực hiện đúng ràng buộc/lịch sử trong UC-46;
không thay đường dẫn GET/PUT/POST simulate của Excel. Toàn bộ batch phải hợp lệ mới commit.
Giá trị giữ nguyên không tạo history/audit hay tăng version. Khi 409, đọc lại dữ liệu,
cho người dùng xem thay đổi trước khi gửi lại expectedVersion mới.

| HTTP | Mã lỗi chính |
| --- | --- |
| 400 | SYS_INVALID_REQUEST; PARAMETER_INVALID_VALUE; PARAMETER_CONSTRAINT_VIOLATION; PARAMETER_QUOTE_INVALID |
| 401 / 403 | Chưa có phiên hợp lệ / không có ADMIN theo base security |
| 404 | PARAMETER_NOT_FOUND |
| 409 | PARAMETER_VERSION_CONFLICT; SYS_VERSION_CONFLICT cho lỗi optimistic lock của persistence |
| 503 | PARAMETER_SIMULATION_UNAVAILABLE khi không có Pricing provider |

Validation gồm kiểu số/chuỗi cấu trúc, miền giá trị, min/max liên khóa, tổng bốn trọng số bằng 1,
thứ tự tier và tỷ lệ khấu trừ không khiến thu nhập courier âm. Đây là kiểm tra tính nhất quán
của giá trị đề xuất; không tự đặt lịch áp dụng trong tương lai. Sửa thành công có hiệu lực ngay
cho lần đọc snapshot sau, đơn đã lưu snapshot giữ nguyên.

## C02 — PricingSimulationPort

```java
PricingSimulationResponse simulate(PricingQuoteRequest quote, ParameterSnapshot snapshot);
```

Port nằm trong `com.fptpost.modules.catalog.service.PricingSimulationPort`.
Provider nhận snapshot được truyền vào, không tự đọc lại SQL, không tạo Order/Ledger/history.
Thành Huy có thể cung cấp Spring bean thực thi port để nối facade Pricing của mình;
bean thật sẽ thay adapter tham chiếu. Không có cơ chế fallback sang tham chiếu khi bean thật lỗi.

Request preview mẫu:

```json
{
  "quote": {
    "distanceKm": 5,
    "weightKg": 1,
    "longestSideCm": 20,
    "urgency": "NORMAL",
    "declaredValue": 500000,
    "insured": false,
    "courierTier": "NEW",
    "agreedFareRatio": 1,
    "promotionDiscount": 0
  },
  "overrides": {"BASE_FARE":"14000"}
}
```

Urgency: NORMAL/SAMEDAY/URGENT; tier: NEW/BRONZE/SILVER/GOLD/DIAMOND.
`distanceKm` không âm; trọng lượng và cạnh lớn nhất dương. Bên nhận kiểm tra giới hạn catalog,
bid ratio và discount. `overrides` có thể rỗng để mô phỏng bộ hiện tại.

Response gồm giá gợi ý/thỏa thuận, base/distance/weight/size, urgency multiplier,
bid min/max, commission/withholding/insurance/discount/fund, courierNet/platformNet/senderTotal,
balanceDifference, parameterVersions và mode. Các số tiền cuối là số nguyên VND;
distanceFee và tỷ lệ là BigDecimal để giữ chính xác cấu thành trước làm tròn.
Provider thật phải trả mode rõ ràng khác REFERENCE và giữ các ca cân bằng tiền.

Hiện `ReferencePricingSimulation` tính theo công thức sheet `07_Mô_phỏng_Biểu_phí`,
đã đối chiếu 20 ca nguồn và bộ tham số tùy chỉnh. Chỉ được đăng ký khi có
dev/local/demo/test, đồng thời không có prod/staging. Có thể tắt bằng
`app.parameters.simulation.reference-enabled=false`. Nếu không có provider thật,
simulation trả 503; CRUD tham số vẫn hoạt động.

## Audit/outbox bàn giao cho P02

`AuditOutboxService.enqueue(action, entityType, entityId, before, after)` yêu cầu có
giao dịch SQL đang mở (MANDATORY) và actor JWT hợp lệ. UC-46 phát event outbox type AUDIT,
action PARAMETER_UPDATED, entityType SYSTEM_PARAMETER, entityId là key.
Đây là sự kiện audit hiện tại, chưa phải kênh notification/ParameterChanged cho toàn bộ consumer.

History, mutation và enqueue commit/rollback cùng nhau. Worker chỉ lấy type AUDIT;
claim/ack/retry là các giao dịch SQL ngắn riêng, Mongo được gọi ngoài giao dịch SQL.
Mongo audit_logs chống trùng theo eventId, kiểm tra nội dung duplicate, không update/delete.
Lease 30 giây; lịch chạy mặc định 2 giây; batch 25; retry backoff tối đa 5 phút.
Payload giới hạn và che trường nhạy cảm; lastError chỉ lưu loại lỗi.

P02/UC-60 tiếp tục query/filter/paging/index và UI audit từ dữ liệu này.
Đợt 1 chưa có API/UI tra cứu audit tổng hợp, retention job hay handler cho các domain khác.

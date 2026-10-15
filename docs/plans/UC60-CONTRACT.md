# Contract UC-60 — Nhật ký kiểm toán

Ngày 15/10/2026. Owner Trần Công Thuận. Nhánh `feature/UC-60-ThuanTC-tra-cuu-audit`.
Nguồn: UC-60, API-157, SCR-60, DB-055; BR-F-116, BR-N-026/038, BR-R-045/084.

## API và quyền

`GET /api/v1/admin/audit-logs` yêu cầu JWT/session thật có role ADMIN.
Service cũng kiểm tra ADMIN, response đặt `Cache-Control: no-store`.
Đường dẫn theo API-157; không có endpoint tạo/sửa/xóa audit hoặc thao tác purge qua HTTP.
Chi tiết before/after trả ngay trong từng dòng kết quả để frontend mở modal.

| Query | Kiểu và cách áp dụng |
| --- | --- |
| actor | UUID công khai của người thực hiện; so khớp chính xác với trường Mongo actor |
| action | Mã hành động chính xác, tối đa 100 ký tự |
| entityType | Loại đối tượng chính xác, tối đa 100 ký tự |
| entityId | Mã đối tượng chính xác, tối đa 200 ký tự |
| from / to | ISO Instant có múi giờ; hai mốc inclusive; from không lớn hơn to |
| page | Bắt đầu 0, tối đa 100000 |
| size | Mặc định 20, trong 1–100 |

Các bộ lọc kết hợp bằng AND; chuỗi trống không lọc. Không diễn giải regex hoặc Mongo operator
từ query người dùng. Thứ tự cố định `at DESC, eventId DESC` để xử lý cả sự kiện cùng timestamp.
BSON có độ chính xác millisecond: from làm tròn lên, to làm tròn xuống khi có phần nhỏ hơn ms.
Frontend nhập/hiển thị giờ Việt Nam UTC+7 và gửi ISO UTC; kết quả có thể thay đổi khi có
sự kiện mới hoặc retention chạy, không phải snapshot cố định giữa nhiều lần phân trang.

Ví dụ:

```text
/api/v1/admin/audit-logs?action=PARAMETER_UPDATED&entityType=SYSTEM_PARAMETER&entityId=BASE_FARE&page=0&size=20
```

Response `ApiResponse.data` là `PageResponse` chuẩn:
`content,page,size,totalElements,totalPages,first,last`.
Mỗi phần tử gồm `eventId,actorType,actorPublicId,action,entityType,entityId,before,after,ip,correlationId,at`.
ID là public UUID/event UUID; không trả khóa SQL nội bộ hoặc payload SQL outbox nguyên bản.
Before/after được che dữ liệu nhạy cảm cả lúc capture và trước khi trả API,
bao gồm map/mảng lồng nhau và các biến thể tên trường password, CCCD, OTP, token, secret.
Frontend render JSON dưới dạng text, không thực thi HTML trong payload.

| HTTP | Hành vi |
| --- | --- |
| 200 | Query thành công, gồm cả trang rỗng |
| 400 | Binding/Bean Validation dùng SYS_INVALID_REQUEST; khoảng thời gian/filter sai dùng AUDIT_INVALID_FILTER |
| 401 / 403 | Phiên không hợp lệ / thiếu ADMIN |
| 405 | ADMIN gọi phương thức ghi không được hỗ trợ |
| 503 | AUDIT_STORE_UNAVAILABLE, thông báo chung, không lộ lỗi/URI Mongo |

## Lưu trữ và housekeeping

Collection `audit_logs` dùng chung producer/sink UC-46, không sao chép sang kho riêng cho Admin.
Unique eventId giữ index tương đương đã có. Sáu index phụ phục vụ thời gian, actor, action,
entityType+entityId, entityType riêng và entityId riêng, luôn kèm at/eventId cho thứ tự.
Indexer chạy lại an toàn, tự thử lại sau lỗi, không khiến lỗi Mongo ngăn SQL outbox ghi nhận thay đổi.
Không tạo TTL cố định trên audit.

`AuditRetentionService` đọc `ParameterService.getSnapshot().integer("AUDIT_KEEP")` mỗi batch.
Mặc định 5 năm theo PRM-107. Mốc hết hạn dùng năm lịch UTC, xử lý năm nhuận;
chỉ bản ghi có `at < cutoff` được loại bỏ bằng thao tác Mongo atomic từng document.
Đây là housekeeping theo chính sách lưu trữ, không phải chức năng sửa/xóa của người quản trị.
Giá trị không biểu diễn an toàn, thiếu tham số hoặc lỗi kho dữ liệu sẽ giữ bản ghi còn lại,
ghi loại lỗi và thử lại kỳ sau. Giới hạn kỹ thuật hỗ trợ AUDIT_KEEP nguyên 1–999 năm.

| Property | Mặc định |
| --- | --- |
| audit.indexes.enabled | true |
| audit.indexes.fixed-delay-ms | 60000 |
| audit.retention.enabled | true |
| audit.retention.initial-delay-ms | 60000 |
| audit.retention.fixed-delay-ms | 3600000 |
| audit.retention.batch-max | 250, cho phép 1–1000 |

Tests tắt background jobs bằng test-only application.properties và gọi worker/indexer trực tiếp.
Runtime dùng giá trị mặc định hoặc cấu hình process; không thay `.env` đã được leader cấu hình.
Không có job xóa lịch sử trạng thái SQL hoặc ledger trong UC-60. Retention chat/vị trí/ledger
thuộc module tương ứng và kế hoạch job sau này.

## Phạm vi tích hợp

Luồng có bằng chứng chạy thật hiện tại: sửa tham số UC-46 → SQL history/outbox → Mongo → query UC-60.
Capture dùng `AuditOutboxService.enqueue()` trong cùng giao dịch SQL với nghiệp vụ.
Các owner KYC, phân quyền, khóa tài khoản, Order và Ledger phải bổ sung producer ở thao tác nhạy cảm của họ;
API tra cứu sẽ đọc cùng collection, không cần mock hoặc viết lại màn hình.
BR-R-045 yêu cầu lịch sử trạng thái đơn bất biến vẫn thuộc owner UC-13, không được thay bằng audit Mongo.
Ảnh KYC và quy tắc không tự duyệt BR-R-084 vẫn thuộc các UC tạo hành động đó.

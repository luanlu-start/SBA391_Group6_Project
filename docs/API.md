# API hiện tại và danh mục FPTPost

API chạy được tra cứu trong Swagger UI tại `/swagger-ui/index.html`; OpenAPI runtime
tại `/api-docs` (đường dẫn đã cấu hình trong application.yml). Danh mục FPTPost dự kiến lấy từ
[Excel v1.0](sources/fptpost-br-uc-v1.0.xlsx), sheet `10_API`, dòng 5–171,
và [SPEC](sources/fptpost-spec-v1.0.pdf), mục 6.

## Endpoint base đã có

| Method | Đường dẫn | Kết quả |
| --- | --- | --- |
| GET | /api/v1/health | Kiểm tra ứng dụng |
| GET | /api/v1/products | Tìm kiếm/phân trang Product |
| GET | /api/v1/products/{id} | Chi tiết Product |
| POST | /api/v1/products | Tạo Product; HTTP 201 |
| PUT | /api/v1/products/{id} | Sửa Product |
| DELETE | /api/v1/products/{id} | Xóa Product; HTTP 200 kèm envelope |
| GET | /api/v1/activity-logs | Phân trang nhật ký; lọc entityId |

Product CRUD là ví dụ kỹ thuật, không tương ứng UC-12 hay các API orders.
Base chưa có JWT, quyền truy cập, VNPay, WebSocket hoặc các endpoint FPTPost dưới đây.

## Contract đang chạy

Response dùng `ApiResponse<T>`: `success`, `status`, `code` số, `message`, `data`,
`errors`, `timestamp`; trường null bị bỏ. HTTP status và status trong body phải khớp.
Mã nghiệp vụ hiện có: 1000 thành công, 1002 request sai, 2001 Product không tồn tại,
9999 lỗi nội bộ. Mã UC không phải mã lỗi nghiệp vụ.

Ví dụ Product không tồn tại (HTTP 404):

```json
{
  "success": false,
  "status": 404,
  "code": 2001,
  "message": "Product not found",
  "timestamp": "2026-10-09T00:00:00Z"
}
```

Lỗi validation trả `errors` map theo field. Danh sách ở `data.content`, có `page`,
`size`, `totalElements`, `totalPages`, `first`, `last`. Page từ 0, size mặc định 12,
tối đa 100; sort qua `sortBy` và `direction` có whitelist. Product filter gồm
search/category/status/minPrice/maxPrice; Activity filter gồm entityId.

## Khác biệt với contract SPEC

SPEC yêu cầu lỗi Problem Details với code chuỗi và correlationId, errors dạng danh sách;
phân trang mặc định 20, `sort=field,asc|desc`, chat/thông báo dùng cursor.
Các yêu cầu khác: Idempotency-Key cho thao tác tiền, ETag/If-Match cho PUT đơn/chuyến,
X-Correlation-Id, quyền theo role và ownership, rate limit.

Các yêu cầu đó **chưa có trong base**. Cần chốt contract chung và cập nhật backend,
frontend cùng OpenAPI trước khi các thành viên triển khai các UC; không trộn hai kiểu
lỗi hoặc hai cách phân trang tùy từng người. Xem [đối chiếu SPEC](SPEC-ALIGNMENT.md).
Danh mục bên dưới mới có method/path/quyền/UC, chưa thay thế OpenAPI có DTO, response,
status, security scheme và ví dụ cho từng endpoint.

## Danh mục dự kiến — chưa triển khai

167 mục: 165 HTTP và 2 WS. Cột đường dẫn giữ nguyên nội dung sheet: HTTP thêm tiền tố
`/api/v1` khi gọi; mục WS là kênh theo giao thức, không tự thêm tiền tố REST.
`Hệ thống` là chủ thể xử lý nội bộ theo nguồn, không có nghĩa endpoint công khai.

| Mã | Phân hệ | Method | Đường dẫn trong nguồn | UC chính | Quyền dự kiến | Pha |
| --- | --- | --- | --- | --- | --- | --- |
| API-001 | AUTH | POST | /auth/register | UC-01 | Công khai | Pha 1 |
| API-002 | AUTH | POST | /auth/otp/verify | UC-02 | Công khai | Pha 1 |
| API-003 | AUTH | POST | /auth/otp/resend | UC-02 | Công khai | Pha 1 |
| API-004 | AUTH | POST | /auth/login | UC-03 | Công khai | Pha 1 |
| API-005 | AUTH | POST | /auth/google | UC-03 | Công khai | Pha 1 |
| API-006 | AUTH | POST | /auth/refresh | UC-03 | Công khai | Pha 1 |
| API-007 | AUTH | POST | /auth/logout | UC-04 | Đăng nhập | Pha 1 |
| API-008 | AUTH | GET | /auth/sessions | UC-04 | Đăng nhập | Pha 1 |
| API-009 | AUTH | DELETE | /auth/sessions/{id} | UC-04 | Đăng nhập | Pha 1 |
| API-010 | AUTH | POST | /auth/password/forgot | UC-05 | Công khai | Pha 1 |
| API-011 | AUTH | POST | /auth/password/reset | UC-05 | Công khai | Pha 1 |
| API-012 | AUTH | POST | /auth/password/change | UC-06 | Đăng nhập | Pha 1 |
| API-013 | KYC | GET | /me | UC-07 | Đăng nhập | Pha 1 |
| API-014 | KYC | PUT | /me/profile | UC-07 | Đăng nhập | Pha 1 |
| API-015 | KYC | PATCH | /me/contact | UC-07 | Đăng nhập | Pha 1 |
| API-016 | KYC | GET | /me/addresses | UC-07 | Đăng nhập | Pha 1 |
| API-017 | KYC | POST | /me/addresses | UC-07 | Đăng nhập | Pha 1 |
| API-018 | KYC | PUT | /me/addresses/{id} | UC-07 | Đăng nhập | Pha 1 |
| API-019 | KYC | DELETE | /me/addresses/{id} | UC-07 | Đăng nhập | Pha 1 |
| API-020 | KYC | POST | /me/data-export | UC-07 | Đăng nhập | Pha 1 |
| API-021 | KYC | POST | /me/delete-request | UC-07 | Đăng nhập | Pha 1 |
| API-022 | KYC | POST | /me/kyc | UC-08 | Đăng nhập | Pha 1 |
| API-023 | KYC | GET | /me/kyc | UC-08 | Đăng nhập | Pha 1 |
| API-024 | KYC | GET | /mod/kyc | UC-09 | MODERATOR | Pha 1 |
| API-025 | KYC | GET | /mod/kyc/{id} | UC-09 | MODERATOR | Pha 1 |
| API-026 | KYC | POST | /mod/kyc/{id}/approve | UC-09 | MODERATOR | Pha 1 |
| API-027 | KYC | POST | /mod/kyc/{id}/reject | UC-09 | MODERATOR | Pha 1 |
| API-028 | KYC | POST | /me/courier-registration | UC-10 | Đăng nhập | Pha 1 |
| API-029 | ORD | POST | /pricing/estimate | UC-11 | Công khai | Pha 1 |
| API-030 | ORD | POST | /orders | UC-12 | SENDER | Pha 1 |
| API-031 | ORD | POST | /orders/{id}/photos | UC-12 | SENDER | Pha 1 |
| API-032 | ORD | POST | /orders/{id}/publish | UC-12 | SENDER | Pha 1 |
| API-033 | ORD | POST | /orders/{id}/duplicate | UC-12 | SENDER | Pha 1 |
| API-034 | ORD | GET | /orders | UC-13 | Đăng nhập | Pha 1 |
| API-035 | ORD | GET | /orders/{id} | UC-13 | Đăng nhập | Pha 1 |
| API-036 | ORD | GET | /orders/{id}/timeline | UC-13 | Đăng nhập | Pha 1 |
| API-037 | ORD | PUT | /orders/{id} | UC-14 | SENDER | Pha 1 |
| API-038 | ORD | POST | /orders/{id}/cancel | UC-15 | Đăng nhập | Pha 1 |
| API-039 | MKT | GET | /market/orders | UC-16 | COURIER | Pha 1 |
| API-040 | MKT | GET | /market/orders/map | UC-16 | COURIER | Pha 1 |
| API-041 | MKT | GET | /market/orders/{id} | UC-16 | COURIER | Pha 1 |
| API-042 | MKT | PUT | /me/area-subscriptions | UC-16 | COURIER | Pha 1 |
| API-043 | MKT | POST | /market/orders/{id}/accept | UC-17 | COURIER | Pha 1 |
| API-044 | MKT | POST | /orders/{id}/offers | UC-18 | COURIER | Pha 1 |
| API-045 | MKT | DELETE | /offers/{id} | UC-18 | COURIER | Pha 1 |
| API-046 | MKT | GET | /me/offers | UC-18 | COURIER | Pha 1 |
| API-047 | MKT | GET | /orders/{id}/offers | UC-19 | SENDER | Pha 1 |
| API-048 | MKT | POST | /offers/{id}/accept | UC-19 | SENDER | Pha 1 |
| API-049 | MKT | POST | /offers/{id}/reject | UC-19 | SENDER | Pha 1 |
| API-050 | MKT | POST | /offers/{id}/counter | UC-19 | Đăng nhập | Pha 1 |
| API-051 | MKT | POST | /orders/{id}/invitations | UC-20 | SENDER | Pha 3 |
| API-052 | MKT | PUT | /me/favorites/{courierId} | UC-20 | SENDER | Pha 3 |
| API-053 | MKT | GET | /matching/orders-for-trip/{tripId} | UC-21 | COURIER | Pha 2 |
| API-054 | MKT | GET | /matching/couriers-for-order/{orderId} | UC-21 | SENDER | Pha 2 |
| API-055 | TRP | POST | /trips | UC-22 | COURIER | Pha 2 |
| API-056 | TRP | GET | /trips | UC-22 | COURIER | Pha 2 |
| API-057 | TRP | GET | /trips/{id} | UC-23 | COURIER | Pha 2 |
| API-058 | TRP | PUT | /trips/{id} | UC-23 | COURIER | Pha 2 |
| API-059 | TRP | POST | /trips/{id}/pause | UC-23 | COURIER | Pha 2 |
| API-060 | TRP | POST | /trips/{id}/cancel | UC-23 | COURIER | Pha 2 |
| API-061 | TRP | POST | /trips/{id}/complete | UC-23 | COURIER | Pha 2 |
| API-062 | NEG | GET | /orders/{id}/chat/messages | UC-24 | Đăng nhập | Pha 1 |
| API-063 | NEG | POST | /orders/{id}/chat/messages | UC-24 | Đăng nhập | Pha 1 |
| API-064 | NEG | POST | /chat/messages/{id}/report | UC-24 | Đăng nhập | Pha 1 |
| API-065 | NEG | WS | /ws (STOMP) /topic/chat.{orderId} | UC-24 | Đăng nhập | Pha 1 |
| API-066 | TRK | POST | /orders/{id}/pickup/token | UC-25 | SENDER | Pha 1 |
| API-067 | TRK | POST | /orders/{id}/pickup/confirm | UC-25 | COURIER | Pha 1 |
| API-068 | TRK | POST | /orders/{id}/start-transit | UC-26 | COURIER | Pha 1 |
| API-069 | TRK | POST | /orders/{id}/locations | UC-26 | COURIER | Pha 1 |
| API-070 | TRK | GET | /orders/{id}/tracking | UC-27 | Đăng nhập | Pha 1 |
| API-071 | TRK | GET | /public/track/{token} | UC-27 | Công khai (token) | Pha 1 |
| API-072 | TRK | WS | /ws (STOMP) /topic/track.{orderId} | UC-27 | Đăng nhập | Pha 1 |
| API-073 | TRK | POST | /orders/{id}/delivery/otp/resend | UC-28 | COURIER | Pha 1 |
| API-074 | TRK | POST | /orders/{id}/delivery/confirm | UC-28 | COURIER | Pha 1 |
| API-075 | TRK | POST | /orders/{id}/delivery/exception | UC-28 | COURIER | Pha 1 |
| API-076 | TRK | POST | /orders/{id}/incidents | UC-29 | COURIER | Pha 1 |
| API-077 | TRK | POST | /sos | UC-29 | Đăng nhập | Pha 1 |
| API-078 | TRK | POST | /orders/{id}/delivery-failed | UC-30 | COURIER | Pha 1 |
| API-079 | TRK | POST | /orders/{id}/return/confirm | UC-30 | SENDER | Pha 1 |
| API-080 | REL | POST | /batches | UC-31 | COURIER | Pha 3 |
| API-081 | REL | GET | /batches/{id}/route | UC-31 | COURIER | Pha 3 |
| API-082 | REL | POST | /orders/relay | UC-32 | SENDER | Pha 3 |
| API-083 | REL | POST | /orders/{id}/legs/{n}/handover | UC-33 | COURIER | Pha 3 |
| API-084 | PAY | POST | /wallet/topups | UC-34 | Đăng nhập | Pha 1 |
| API-085 | PAY | GET | /vnpay/return | UC-34 | Công khai | Pha 1 |
| API-086 | PAY | GET | /vnpay/ipn | UC-42 | Webhook VNPay | Pha 1 |
| API-087 | PAY | POST | /orders/{id}/payment | UC-35 | SENDER | Pha 1 |
| API-088 | PAY | GET | /wallet | UC-38 | Đăng nhập | Pha 1 |
| API-089 | PAY | GET | /wallet/transactions | UC-38 | Đăng nhập | Pha 1 |
| API-090 | PAY | GET | /wallet/statement | UC-38 | Đăng nhập | Pha 1 |
| API-091 | PAY | POST | /orders/{id}/refund-to-source | UC-37 | SENDER | Pha 1 |
| API-092 | PAY | GET | /orders/{id}/escrow | UC-36 | Đăng nhập | Pha 1 |
| API-093 | PAY | POST | /admin/escrow/{orderId}/release | UC-36 | ADMIN | Pha 1 |
| API-094 | PAY | GET | /me/bank-accounts | UC-39 | COURIER | Pha 1 |
| API-095 | PAY | POST | /me/bank-accounts | UC-39 | COURIER | Pha 1 |
| API-096 | PAY | DELETE | /me/bank-accounts/{id} | UC-39 | COURIER | Pha 1 |
| API-097 | PAY | POST | /wallet/withdrawals | UC-40 | Đăng nhập | Pha 1 |
| API-098 | PAY | GET | /wallet/withdrawals | UC-40 | Đăng nhập | Pha 1 |
| API-099 | PAY | POST | /wallet/withdrawals/{id}/cancel | UC-40 | Đăng nhập | Pha 1 |
| API-100 | PAY | GET | /admin/withdrawals | UC-41 | ADMIN | Pha 1 |
| API-101 | PAY | POST | /admin/withdrawals/{id}/approve | UC-41 | ADMIN | Pha 1 |
| API-102 | PAY | POST | /admin/withdrawals/{id}/reject | UC-41 | ADMIN | Pha 1 |
| API-103 | PAY | POST | /admin/withdrawals/{id}/mark-paid | UC-41 | ADMIN | Pha 1 |
| API-104 | PAY | POST | /admin/withdrawals/export | UC-41 | ADMIN | Pha 1 |
| API-105 | PAY | POST | /admin/withdrawals/import-result | UC-41 | ADMIN | Pha 1 |
| API-106 | PAY | GET | /admin/reconciliation | UC-43 | ADMIN | Pha 2 |
| API-107 | PAY | POST | /admin/reconciliation/run | UC-43 | ADMIN | Pha 2 |
| API-108 | PAY | POST | /admin/reconciliation/items/{id}/adjust | UC-43 | ADMIN | Pha 2 |
| API-109 | PAY | GET | /admin/ledger/integrity | UC-43 | ADMIN | Pha 2 |
| API-110 | FEE | POST | /orders/{id}/promo | UC-44 | SENDER | Pha 3 |
| API-111 | FEE | GET | /admin/promotions | UC-45 | ADMIN | Pha 3 |
| API-112 | FEE | POST | /admin/promotions | UC-45 | ADMIN | Pha 3 |
| API-113 | FEE | PUT | /admin/promotions/{id} | UC-45 | ADMIN | Pha 3 |
| API-114 | ADM | GET | /admin/params | UC-46 | ADMIN | Pha 1 |
| API-115 | ADM | PUT | /admin/params/{key} | UC-46 | ADMIN | Pha 1 |
| API-116 | ADM | POST | /admin/params/simulate | UC-46 | ADMIN | Pha 1 |
| API-117 | REP | POST | /orders/{id}/reviews | UC-47 | Đăng nhập | Pha 1 |
| API-118 | REP | GET | /couriers/{id}/profile | UC-48 | Đăng nhập | Pha 1 |
| API-119 | REP | GET | /couriers/{id}/reviews | UC-48 | Đăng nhập | Pha 1 |
| API-120 | REP | POST | /reviews/{id}/reply | UC-48 | Đăng nhập | Pha 1 |
| API-121 | REP | GET | /me/trust | UC-49 | COURIER | Pha 1 |
| API-122 | DSP | POST | /orders/{id}/complaints | UC-50 | Đăng nhập | Pha 1 |
| API-123 | DSP | GET | /complaints | UC-51 | Đăng nhập | Pha 1 |
| API-124 | DSP | GET | /complaints/{id} | UC-51 | Đăng nhập | Pha 1 |
| API-125 | DSP | POST | /complaints/{id}/messages | UC-51 | Đăng nhập | Pha 1 |
| API-126 | DSP | POST | /mod/complaints/{id}/ruling | UC-51 | MODERATOR | Pha 1 |
| API-127 | DSP | POST | /complaints/{id}/appeal | UC-51 | Đăng nhập | Pha 1 |
| API-128 | DSP | POST | /admin/complaints/{id}/compensation | UC-52 | ADMIN | Pha 1 |
| API-129 | DSP | GET | /admin/guarantee-fund | UC-52 | ADMIN | Pha 1 |
| API-130 | NTF | GET | /notifications | UC-53 | Đăng nhập | Pha 1 |
| API-131 | NTF | POST | /notifications/{id}/read | UC-53 | Đăng nhập | Pha 1 |
| API-132 | NTF | POST | /notifications/read-all | UC-53 | Đăng nhập | Pha 1 |
| API-133 | NTF | GET | /me/notification-settings | UC-53 | Đăng nhập | Pha 1 |
| API-134 | NTF | PUT | /me/notification-settings | UC-53 | Đăng nhập | Pha 1 |
| API-135 | NTF | POST | /me/push-subscriptions | UC-53 | Đăng nhập | Pha 1 |
| API-136 | SAF | GET | /mod/orders/pending | UC-54 | MODERATOR | Pha 1 |
| API-137 | SAF | POST | /mod/orders/{id}/approve | UC-54 | MODERATOR | Pha 1 |
| API-138 | SAF | POST | /mod/orders/{id}/reject | UC-54 | MODERATOR | Pha 1 |
| API-139 | SAF | POST | /reports | UC-55 | Đăng nhập | Pha 1 |
| API-140 | SAF | GET | /mod/reports | UC-55 | MODERATOR | Pha 1 |
| API-141 | SAF | POST | /admin/users/{id}/lock | UC-56 | ADMIN | Pha 1 |
| API-142 | SAF | POST | /admin/users/{id}/unlock | UC-56 | ADMIN | Pha 1 |
| API-143 | SAF | POST | /admin/blacklist | UC-56 | ADMIN | Pha 1 |
| API-144 | ADM | GET | /admin/users | UC-57 | ADMIN | Pha 1 |
| API-145 | ADM | GET | /admin/users/{id} | UC-57 | ADMIN | Pha 1 |
| API-146 | ADM | PUT | /admin/users/{id}/roles | UC-57 | ADMIN | Pha 1 |
| API-147 | ADM | POST | /admin/staff | UC-57 | ADMIN | Pha 1 |
| API-148 | ADM | GET | /admin/categories | UC-58 | ADMIN | Pha 1 |
| API-149 | ADM | PUT | /admin/categories/{id} | UC-58 | ADMIN | Pha 1 |
| API-150 | ADM | PUT | /admin/prohibited-keywords | UC-58 | ADMIN | Pha 1 |
| API-151 | ADM | PUT | /admin/areas | UC-58 | ADMIN | Pha 1 |
| API-152 | ADM | PUT | /admin/meeting-points | UC-58 | ADMIN | Pha 1 |
| API-153 | ADM | PUT | /admin/content/{key} | UC-58 | ADMIN | Pha 1 |
| API-154 | ADM | GET | /admin/dashboard | UC-59 | ADMIN | Pha 1 |
| API-155 | ADM | GET | /admin/reports/export | UC-59 | ADMIN | Pha 1 |
| API-156 | ADM | GET | /admin/reports/courier-revenue | UC-59 | ADMIN | Pha 1 |
| API-157 | ADM | GET | /admin/audit-logs | UC-60 | ADMIN | Pha 2 |
| API-158 | GAM | GET | /me/earnings | UC-61 | COURIER | Pha 2 |
| API-159 | GAM | GET | /me/stats | UC-61 | COURIER | Pha 2 |
| API-160 | GAM | GET | /me/badges | UC-62 | Đăng nhập | Pha 3 |
| API-161 | GAM | GET | /me/referral | UC-62 | Đăng nhập | Pha 3 |
| API-162 | ADM | GET | /admin/jobs | UC-63 | ADMIN | Pha 1 |
| API-163 | ADM | POST | /admin/jobs/{name}/run | UC-63 | ADMIN | Pha 1 |
| API-164 | SYS | GET | /geo/search | UC-12 | Đăng nhập | Pha 1 |
| API-165 | SYS | GET | /geo/route | UC-11 | Đăng nhập | Pha 1 |
| API-166 | SYS | POST | /files | UC-12 | Đăng nhập | Pha 1 |
| API-167 | SYS | GET | /actuator/health | UC-63 | Công khai | Pha 1 |

## Cập nhật khi code một UC

Đọc BR/luồng ngoại lệ và tiêu chí nghiệm thu, chốt DTO và mã lỗi trong OpenAPI,
triển khai kiểm tra role/ownership, transaction/idempotency cần thiết, rồi kiểm thử
cả thành công và lỗi. Chỉ chuyển endpoint sang phần đã có sau khi code được tích hợp.
Không đánh dấu cả UC hoàn thành chỉ vì đã tạo route hoặc Swagger annotation.

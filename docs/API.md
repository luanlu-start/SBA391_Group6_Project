# API hiện tại và danh mục FPTPost

API chạy được tra cứu trong Swagger UI tại `/swagger-ui/index.html`; OpenAPI runtime
tại `/api-docs` (đường dẫn đã cấu hình trong application.yml). Danh mục FPTPost dự kiến lấy từ
[Excel v1.0](sources/fptpost-br-uc-v1.0.xlsx), sheet `10_API`, dòng 5–171,
và [SPEC](sources/fptpost-spec-v1.0.pdf), mục 6. Danh mục cũ bên dưới là baseline.
Nghiệp vụ/phân công hiện tại theo workbook `FPTPost_Dac_ta_BR_UC (1).xlsx` do nhóm cung cấp.
Các mục UC-45, UC-46 và UC-60 bên dưới mô tả API đã triển khai theo bản đang áp dụng.

## UC-45 đã triển khai — ADMIN

| Method | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET | /api/v1/admin/promotions | Tìm mã, lọc trạng thái thực tế, phân trang và lượt dùng thật |
| POST | /api/v1/admin/promotions | Tạo mã khuyến mãi; HTTP 201 |
| PUT | /api/v1/admin/promotions/{id} | Sửa/tạm dừng/kích hoạt với expectedVersion |
| GET | /api/v1/admin/promotions/{id} | Chi tiết; endpoint bổ sung |
| GET | /api/v1/admin/promotions/target-options | Tìm người áp dụng theo tên/email/UUID; endpoint bổ sung |

`id` là UUID. Giá trị giảm `value` dùng chuỗi thập phân để bảo toàn độ chính xác:
`FIXED_AMOUNT` là số đồng nguyên dương, tối đa 16 chữ số; `PERCENTAGE` lớn hơn 0,
tối đa 100 với hai chữ số thập phân. Thời hạn là ISO Instant, gồm mốc bắt đầu và không gồm
mốc kết thúc. `target=ALL` có danh sách người dùng rỗng; `SELECTED_USERS` có 1–100 UUID.
Response trả thêm `targetUsers` với nhãn tên/email để chỉnh sửa lựa chọn.

`status` là `ACTIVE` hoặc `PAUSED`; `effectiveStatus` còn phản ánh chưa đến hạn, hết hạn hoặc hết lượt.
Mã được chuẩn hóa thành chữ hoa và giữ nguyên sau khi tạo; trùng mã/phiên bản cũ trả 409.
Không giảm giới hạn lượt xuống dưới số lượt đã dùng, không sửa số lượt hay xóa lịch sử qua API.
Mọi mutation ghi audit trong cùng giao dịch SQL, response no-store và chỉ ADMIN được truy cập.

UC-44 sử dụng snapshot từ `PromotionCatalog` trong module pricing. Luồng áp mã/reserve/redeem
vào Order/Payment do owner UC-44 tích hợp; quản trị UC-45 chưa thực hiện thao tác đó.

## UC-46 đã triển khai — ADMIN

| Method | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET | /api/v1/admin/params | 109 tham số hiện tại, giá trị và phiên bản |
| PUT | /api/v1/admin/params/{key} | Sửa một giá trị với expectedVersion |
| PUT | /api/v1/admin/params/batch | Sửa nhiều giá trị nguyên tử; endpoint bổ sung cho ràng buộc liên khóa |
| GET | /api/v1/admin/params/{key}/history | Lịch sử phân trang; endpoint bổ sung |
| POST | /api/v1/admin/params/simulate | Preview tham số đề xuất; không ghi dữ liệu |

Request/response thực tế tra trong Swagger/OpenAPI của ứng dụng đang chạy;
quy ước lỗi xem [API-ERRORS.md](API-ERRORS.md).
Simulation hiện dùng adapter `REFERENCE` trong dev/demo/test/local; production/staging cần Pricing provider thật.

## UC-60 đã triển khai — ADMIN

`GET /api/v1/admin/audit-logs` trả `ApiResponse<PageResponse<AuditLogResponse>>`.
Lọc AND theo `actor` (UUID), `action`, `entityType`, `entityId`, `from/to` ISO Instant inclusive;
`page=0`, `size=20` mặc định, size tối đa 100. Thứ tự `at DESC,eventId DESC`.
Chi tiết before/after đã mask nằm trong mỗi dòng; response no-store; không có API ghi/xóa.
Kho Mongo không khả dụng trả `AUDIT_STORE_UNAVAILABLE`/503, khác với query thành công nhưng rỗng.
Schema, index và retention xem [DATABASE.md](DATABASE.md#index-và-retention-uc-60-đã-triển-khai).

`GET /api/v1/admin/audit-logs/filter-options` cung cấp danh sách để tìm và chọn bộ lọc.
`field` là `actor`, `action`, `entityType` hoặc `entityId`; `q` tìm chuỗi thông thường
(tối đa 100 ký tự), `limit` mặc định 30, từ 1 đến 50. Với `entityId`, có thể truyền
`entityType` để giới hạn mã theo loại đối tượng. Response `data` gồm
`options: [{value, label, description}]` và `hasMore`; khi còn kết quả, nhập thêm từ khóa.
Người thực hiện tìm theo tên, email hoặc UUID qua interface đọc của Identity;
các mã thao tác/đối tượng lấy từ toàn bộ nhật ký đã ghi nhận. UUID lịch sử không còn tài khoản
vẫn tìm được bằng mã đầy đủ. API chỉ cho ADMIN, trả no-store và không ghi dữ liệu.

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
| GET | /api/v1/auth/registration-policies | Nội dung/phiên bản chính sách đăng ký |
| POST | /api/v1/auth/register | UC-01 đăng ký chờ xác thực, gửi OTP email; HTTP 201 |
| POST | /api/v1/auth/otp/verify | UC-02 xác thực email, kích hoạt tài khoản; HTTP 200 |
| POST | /api/v1/auth/otp/resend | UC-02 gửi lại OTP sau thời gian chờ; HTTP 200 |
| GET | /api/v1/auth/csrf | CSRF token trước thao tác đăng nhập/refresh/logout |
| POST | /api/v1/auth/login | UC-03 đăng nhập email/SĐT, JWT và refresh cookie |
| POST | /api/v1/auth/refresh | UC-03 xoay vòng refresh cookie và cấp JWT mới |
| POST | /api/v1/auth/logout | Đăng xuất phiên hiện tại |
| GET | /api/v1/auth/sessions | UC-04 danh sách phiên đang hoạt động của tài khoản hiện tại |
| DELETE | /api/v1/auth/sessions/{id} | UC-04 thu hồi một phiên của tài khoản hiện tại |
| GET | /api/v1/me | Thông tin người dùng đang đăng nhập |

Product CRUD là ví dụ kỹ thuật, không tương ứng UC-12 hay các API orders.
UC-01–04 đã có đăng ký, xác thực email, đăng nhập, refresh rotation và quản lý phiên.
Product/Activity và các API nội bộ yêu cầu Bearer; health và các endpoint auth công khai
được liệt kê trong SecurityConfig. Chưa có VNPay/WebSocket hoặc đầy đủ role/ownership cho các module chưa triển khai.

## API xác thực UC-01–04

Luồng giao diện: `/register` → `/verify` → `/login` → `/account`.
Các endpoint xác thực nằm trong `AuthenticationController`.

| API | Request / kết quả trong `data` |
| --- | --- |
| GET registration-policies | Trả `terms`, `privacy` (version/title/paragraphs) và `passwordMinLength` |
| POST register | Nhận `fullName`, `email`, `phone`, `password`, `adultConfirmed`, `termsAccepted`, `privacyAccepted`, `termsVersion`, `privacyVersion`; trả `userId`, `status`, `challengeId`, `verificationChannel`, `maskedTarget`, `expiresAt`, `resendAvailableAt` |
| POST otp/verify | Nhận `{challengeId, otp}`; OTP là chuỗi 6 số; trả `userId`, `status: ACTIVE`, `verificationChannel`, `verifiedAt` |
| POST otp/resend | Nhận `{challengeId}`; trả challenge mới và metadata xác thực, frontend thay challenge đang lưu |
| POST login | Nhận `{identifier, password, rememberMe}` với email hoặc SĐT; `rememberMe` mặc định `false`; trả `accessToken`, `tokenType`, `expiresAt`, `user` và đặt refresh cookie |
| POST refresh | Nhận refresh cookie; trả payload như login và thay refresh cookie |
| POST logout | Bearer; thu hồi phiên hiện tại, xóa refresh cookie |
| GET sessions | Bearer; trả danh sách `{id, deviceInfo, ip, createdAt, expiresAt, current}`; chỉ gồm các phiên đang hoạt động của tài khoản hiện tại |
| DELETE sessions/{id} | Bearer; `id` là UUID công khai của phiên; trả `{currentSessionRevoked}`; nếu là phiên hiện tại thì xóa refresh cookie |
| GET me | Bearer; trả thông tin người dùng, không chứa mật khẩu hoặc token |

Các đường dẫn trong bảng thuộc `/api/v1/auth`, riêng `me` là `/api/v1/me`.
Nhập lại mật khẩu được kiểm tra trên frontend, không gửi lên API.
Email được chuẩn hóa chữ thường, SĐT Việt Nam về `+84...`.
Mật khẩu tối thiểu 8 ký tự, có chữ hoa/thường/số, tối đa 72 byte UTF-8; lưu BCrypt.

Đăng ký tạo User `PENDING_VERIFICATION`, role `SENDER`, hồ sơ xác nhận tuổi và hai consent.
OTP đăng ký (`OtpType.REGISTRATION`) được băm BCrypt, mặc định hết hạn sau 5 phút,
tối đa 5 lần nhập sai; bộ đếm vẫn được lưu khi trả lỗi. Verify thành công chuyển User
sang `ACTIVE`, chưa tạo phiên đăng nhập. Resend chờ ít nhất 60 giây và vô hiệu mã cũ.
SMTP thất bại trả `AUTH_OTP_DELIVERY_FAILED`/503 và rollback transaction;
gửi mail hiện đồng bộ, chưa có outbox/retry.

Login đúng mật khẩu của tài khoản chờ xác thực trả HTTP 403 `AUTH_ACCOUNT_UNVERIFIED`
kèm extension `verification` (challengeId, verificationChannel, maskedTarget, expiresAt,
resendAvailableAt). Frontend lưu metadata mới và tự chuyển `/verify`;
login không gửi email mới hoặc cấp JWT cho tài khoản này. Mã hết hạn có thể yêu cầu resend.
Sai thông tin trả `AUTH_LOGIN_FAILED`/401; sai 5 lần liên tiếp khóa đăng nhập 15 phút.
Lỗi giới hạn request/cooldown trả 429 và `Retry-After`.

Trước login/refresh, gọi `GET /api/v1/auth/csrf`, gửi token qua `headerName`
được trả về cùng cookie (`withCredentials`). CORS chỉ cho phép origin cấu hình.
Access JWT dùng HS256, mặc định 15 phút; backend kiểm tra chữ ký, issuer/audience,
trạng thái tài khoản và phiên SQL. Frontend giữ access token trong bộ nhớ.
Refresh token chỉ lưu hash SHA-256 trong `sessions`, gửi bằng cookie HttpOnly,
SameSite=Strict, Path=/api/v1/auth, hạn xác thực mặc định 7 ngày. Khi `rememberMe=true`,
cookie có Max-Age 7 ngày theo `app.authentication.refresh-ttl-days`; khi `false` hoặc
không gửi trường này, cookie chỉ tồn tại trong phiên trình duyệt (không có Max-Age/Expires).
Refresh giữ nguyên lựa chọn này. Trình duyệt có tính năng khôi phục phiên có thể khôi phục
cả cookie phiên; thao tác đăng xuất vẫn thu hồi phiên ở backend và xóa cookie.
Refresh xoay token/phiên;
dùng lại token đã thay sẽ thu hồi mọi phiên của tài khoản. Giới hạn mặc định 5 phiên.
Không lưu token, mật khẩu hoặc OTP vào localStorage/sessionStorage;
trang OTP chỉ lưu metadata challenge trong sessionStorage để tiếp tục khi tải lại.

Màn hình SCR-12 tại `/me/security`, có liên kết từ `/account`: xem IP, thiết bị,
thời điểm đăng nhập/hết hạn, nhận biết phiên hiện tại và xác nhận đăng xuất từng phiên.
Thu hồi phiên có hiệu lực ngay với cả JWT và refresh token; các phiên khác tiếp tục hoạt động.
Thu hồi phiên hiện tại chuyển về `/login`. API không trả hash/token hoặc phiên của tài khoản khác.
API quản lý phiên yêu cầu Bearer JWT, không dùng refresh cookie để xác thực nên không cần CSRF token.
Phiên đã hết hạn/thu hồi, ID không tồn tại hoặc thuộc tài khoản khác trả
`AUTH_SESSION_NOT_FOUND`/404. Khi ID trong danh sách đã được thay do refresh, backend
thu hồi phiên tiếp nối của cùng tài khoản; refresh và thu hồi cùng dùng khóa theo User.
Refresh giữ nguyên `createdAt` của lần đăng nhập để danh sách và thứ tự giới hạn thiết bị ổn định.

Quên/đổi mật khẩu, Google login và KYC chưa triển khai.
Cấu hình SMTP/template nằm trong [README](../README.md), schema trong [DATABASE](DATABASE.md),
mã lỗi và field validation theo [contract lỗi](API-ERRORS.md).

## Contract đang chạy

Response thành công dùng `ApiResponse<T>`: `success`, `status`, `code: 1000`,
`message`, `data`, `timestamp`; trường null bị bỏ.
Lỗi dùng `ApiProblem`/Problem Details, Content-Type `application/problem+json`.
HTTP status phải khớp `status` trong body; mã nghiệp vụ dạng chữ, khác mã UC.

Ví dụ Product không tồn tại (HTTP 404):

```json
{
  "type": "https://fptpost.vn/errors/product-not-found",
  "title": "Không tìm thấy sản phẩm",
  "status": 404,
  "code": "PRODUCT_NOT_FOUND",
  "detail": "Sản phẩm không tồn tại hoặc đã bị xóa.",
  "correlationId": "7f3c9a52-1d4e-4b8a-9c21-0a6e5d3f7b10"
}
```

Lỗi validation trả `errors` danh sách `{field, code, message}`; frontend chuyển thành
`ApiError.fieldErrors` để hiển thị theo field. Header `X-Correlation-Id` có ở thành công/lỗi,
body lỗi dùng cùng ID, log có ID và frontend cho sao chép khi có lỗi.
Xem [contract lỗi và cách thêm mã](API-ERRORS.md).

Danh sách ở `data.content`, có `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
Page từ 0, size mặc định 12, tối đa 100; sort qua `sortBy` và `direction` có whitelist.
Product filter gồm search/category/status/minPrice/maxPrice; Activity filter gồm entityId.

## Khác biệt còn lại với contract SPEC

Đã chuyển contract lỗi sang Problem Details, code chữ, errors danh sách và correlationId.
Response thành công vẫn dùng envelope ApiResponse; phân trang giữ mặc định 12,
`sortBy`/`direction`. SPEC minh họa payload thành công trực tiếp, mặc định 20,
`sort=field,asc|desc`, cursor cho chat/thông báo. Các phần này chưa chuyển.

Idempotency-Key cho thao tác tiền, ETag/If-Match cho PUT đơn/chuyến và quyền nghiệp vụ theo
role/ownership sẽ triển khai ở UC tương ứng. JWT/phiên đã có; hạn request áp dụng đăng ký, OTP, login và refresh.
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

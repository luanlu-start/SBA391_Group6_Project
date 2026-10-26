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
| GET | /api/v1/auth/registration-policies | Nội dung/phiên bản chính sách đăng ký |
| POST | /api/v1/auth/register | UC-01 đăng ký chờ xác thực, gửi OTP email; HTTP 201 |
| POST | /api/v1/auth/otp/verify | UC-02 xác thực email, kích hoạt tài khoản; HTTP 200 |
| POST | /api/v1/auth/otp/resend | UC-02 gửi lại OTP sau thời gian chờ; HTTP 200 |
| GET | /api/v1/auth/csrf | CSRF token trước thao tác đăng nhập/refresh/logout |
| POST | /api/v1/auth/login | UC-03 đăng nhập email/SĐT, JWT và refresh cookie |
| POST | /api/v1/auth/refresh | UC-03 xoay vòng refresh cookie và cấp JWT mới |
| POST | /api/v1/auth/logout | Đăng xuất phiên hiện tại |
| POST | /api/v1/auth/password/forgot | UC-05 yêu cầu OTP đặt lại mật khẩu; phản hồi chung HTTP 200 |
| POST | /api/v1/auth/password/reset | UC-05 xác thực OTP, đổi mật khẩu và thu hồi mọi phiên cũ |
| POST | /api/v1/auth/password/change | UC-06 đổi mật khẩu hiện tại; yêu cầu Bearer JWT |
| GET | /api/v1/auth/sessions | UC-04 danh sách phiên đang hoạt động của tài khoản hiện tại |
| DELETE | /api/v1/auth/sessions/{id} | UC-04 thu hồi một phiên của tài khoản hiện tại |
| GET | /api/v1/wallet | UC-38 xem thông tin số dư ví, tiền ký quỹ; HTTP 200 |
| POST | /api/v1/wallet/topups | UC-34 (API-084) nạp tiền ví qua VNPay, sinh paymentUrl; HTTP 200 |
| GET | /api/v1/payment/vnpay-return | UC-34 (API-085) nhận return từ VNPay, hiển thị biên lai giao dịch |
| GET/POST | /api/v1/payment/vnpay-ipn | UC-42 (API-086) webhook IPN từ VNPay, ghi nhận số dư và sổ cái kép |
| POST | /api/v1/orders/{id}/payment | UC-35 (API-087) thanh toán & ký quỹ đơn hàng qua Ví hoặc VNPay |
| GET | /api/v1/orders/{id}/escrow | UC-36 (API-092) xem thông tin ký quỹ đơn hàng (Sender, Courier, Admin) |
| POST | /api/v1/admin/escrow/{orderId}/release | UC-36 (API-093) Admin giải ngân tiền ký quỹ thủ công cho người giao hàng |
| GET | /api/v1/orders | Danh sách đơn hàng |
| GET | /api/v1/orders/{id} | Chi tiết đơn hàng |
| POST | /api/v1/orders | Tạo đơn hàng mới; HTTP 201 |
| GET | /api/v1/me | Thông tin người dùng đang đăng nhập |

Product CRUD là ví dụ kỹ thuật, không tương ứng UC-12 hay các API orders.
UC-01–06 đã có đăng ký, xác thực email, đăng nhập, refresh rotation, quản lý phiên, khôi phục và đổi mật khẩu.
UC-34, UC-35 và UC-36 đã được triển khai hoàn chỉnh cho cả Backend, Security và Frontend: nạp tiền vào ví qua cổng VNPay (SCR-42), thanh toán và ký quỹ đơn hàng (SCR-22), giải ngân tiền ký quỹ cho shipper (UC-36), quản lý bút toán sổ cái kép (ledger) và xử lý IPN đa mục đích (WALLET_TOPUP / ORDER_ESCROW) bảo đảm tính toàn vẹn (idempotency).
Product/Activity, Wallet, Escrow và Order Payment yêu cầu Bearer JWT; các endpoint callback VNPay công khai (return/ipn) được cấu hình trong SecurityConfig. Endpoint giải ngân admin yêu cầu role ADMIN.

## API xác thực UC-01–06

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
Refresh xoay token/phiên; dùng lại token đã thay của một phiên còn hoạt động sẽ thu hồi
mọi phiên của tài khoản. Token đã hết hạn hoặc có phiên tiếp nối đã đăng xuất/bị thay bởi
đăng nhập mới trả `AUTH_REFRESH_INVALID`, không thu hồi các phiên khác. Giới hạn mặc định 5 phiên.
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

Cookie `fptpost_browser` là UUID ngẫu nhiên nhận diện trình duyệt/profile, không có quyền xác thực.
Cookie dùng HttpOnly, SameSite=Strict, Path=/api/v1/auth, thời hạn 365 ngày; Secure theo
`app.authentication.cookie-secure`. Login/refresh thành công đặt cookie này cùng refresh cookie;
logout chỉ xóa refresh cookie. Không trả mã nhận diện trình duyệt trong JSON hoặc dùng tên trình
duyệt/IP làm khóa nhóm. Login đúng thông tin thu hồi phiên cũ của cùng tài khoản và mã trình duyệt,
rồi cấp phiên mới. Login sai không thu hồi phiên nào. Refresh giữ nguyên mã trình duyệt của phiên.
Hai máy/profile có mã khác nhau vẫn có hai phiên, kể cả cùng tên Chrome/Windows và IP.
Xóa cookie, đổi profile hoặc dùng ẩn danh sẽ có mã mới; không nhận diện phần cứng của máy.
Phiên cũ chưa có `browser_id` được gắn mã khi refresh; login có refresh cookie cũ thu hồi
phiên thuộc tài khoản đang đăng nhập. Các phiên cũ khác không bị gộp theo tên/IP và sẽ hết hạn.
Người dùng chỉ cần đăng xuất phiên không còn dùng hoặc không nhận ra. Mặc định tối đa 5
phiên hoạt động; đăng nhập ở trình duyệt thứ 6 thu hồi phiên cũ nhất.
Phiên hết hạn/thu hồi không xuất hiện trong danh sách. Dữ liệu đã hết hạn thêm 7 ngày được
dọn mỗi giờ; bản ghi token bị thay thế nhưng chưa hết hạn vẫn được giữ để phát hiện dùng lại.

### Quên và đặt lại mật khẩu (UC-05)

Luồng SCR-06/SCR-07: `/login` → `/forgot` → `/reset` → `/login`.
Hai endpoint công khai dưới `/api/v1/auth/password`; không dùng Bearer hoặc refresh cookie để xác thực.

- `POST /forgot`: `{ "identifier": "sender@example.com" }`. Email được chuẩn hóa chữ thường; SĐT Việt Nam chấp nhận dạng `09…` hoặc `+849…`. OTP luôn gửi tới **email đã đăng ký**, kể cả khi nhập SĐT; chưa tích hợp SMS.
- HTTP 200 trả cùng thông báo và `data: { "resendAvailableAt": "...UTC..." }` cho tài khoản tồn tại/không tồn tại/bị khóa/đã xóa. Không trả ID tài khoản, challenge ID hoặc địa chỉ nhận email. Lỗi gửi SMTP cũng giữ phản hồi chung; transaction OTP được rollback, ghi log lỗi gửi không chứa thông tin tài khoản/OTP.
- `POST /reset`: `{ "identifier": "sender@example.com", "otp": "012345", "newPassword": "NewPassword456!" }`. Nhập lại mật khẩu chỉ kiểm tra trên frontend. Thành công HTTP 200, không cấp JWT, xóa refresh cookie của trình duyệt và yêu cầu đăng nhập lại.

OTP có purpose `PASSWORD_RESET`, lưu BCrypt, hiệu lực 5 phút, tối đa 5 lần sai.
Chỉ OTP mới nhất của đúng tài khoản/purpose/email hiện tại được chấp nhận; gửi lại cách nhau ít nhất 60 giây và vô hiệu mã cũ.
Giới hạn gửi lại theo identifier áp dụng cả tài khoản không tồn tại; SQL lock theo User ngăn hai instance gửi trùng trong thời gian chờ.
Giới hạn 10 request/phút/IP dùng chung với auth, tính cả request sai JSON/validation; HTTP 429 có `Retry-After`.
Bộ đếm IP/identifier hiện nằm trong bộ nhớ từng instance, chưa dùng kho chia sẻ.

Mã sai, hết hạn, đã dùng, hết lượt thử, tài khoản không tồn tại hoặc không được phép đều trả cùng HTTP 400 `AUTH_PASSWORD_RESET_INVALID`.
Lượt nhập sai vẫn được lưu khi trả lỗi. OTP hợp lệ nhưng mật khẩu không đạt chính sách trả `AUTH_PASSWORD_INVALID` và không tiêu thụ mã.
Chính sách mật khẩu dùng chung với đăng ký: ít nhất 8 ký tự theo cấu hình, có chữ hoa/thường/số, tối đa 72 byte UTF-8, không trùng email/SĐT.
Thay mật khẩu, đánh dấu OTP đã dùng và thu hồi mọi phiên của tài khoản nằm trong cùng transaction/khóa User với login/refresh.
JWT và refresh token cũ mất hiệu lực ngay; phiên tài khoản khác không bị thu hồi.
Bộ đếm khóa đăng nhập tạm thời được đặt lại, nhưng không mở tài khoản bị quản trị khóa/xóa.
Tài khoản chờ xác thực có thể khôi phục mật khẩu, vẫn giữ trạng thái chờ xác thực và vai trò hiện tại.

Email bảo mật dùng template `password-changed`, gửi sau khi transaction commit, không chứa mật khẩu/OTP.
Nếu thông báo bảo mật gửi thất bại, mật khẩu mới vẫn có hiệu lực; hiện ghi log, chưa có outbox/retry.
Frontend không lưu OTP/mật khẩu vào localStorage/sessionStorage; router state chỉ giữ identifier và thời điểm được gửi lại.

### Đổi mật khẩu (UC-06)

SCR-12 dùng chung `/me/security` với quản lý phiên. `POST /api/v1/auth/password/change` yêu cầu Bearer JWT
còn hiệu lực, body `{ "currentPassword": "OldPassword123!", "newPassword": "NewPassword456!" }`.
Tài khoản được lấy từ JWT, không nhận ID/email để chọn tài khoản. Nhập lại mật khẩu chỉ kiểm tra ở frontend.

- Mật khẩu hiện tại sai trả HTTP 400 `AUTH_CURRENT_PASSWORD_INCORRECT`; phiên hiện tại vẫn còn hiệu lực.
- Mật khẩu mới dùng chung quy tắc với đăng ký/khôi phục, lưu BCrypt cost ≥ 10; trùng mật khẩu hiện tại trả HTTP 400 `AUTH_PASSWORD_UNCHANGED`.
- Khóa User và kiểm tra lại phiên trong transaction để phối hợp với login/refresh/reset. Thành công cập nhật hash, thu hồi **mọi phiên** của tài khoản, kể cả phiên hiện tại, và vô hiệu OTP `PASSWORD_RESET` còn hiệu lực. OTP đăng ký và phiên tài khoản khác không bị ảnh hưởng.
- HTTP 200 không cấp JWT, xóa refresh cookie; frontend xóa trạng thái xác thực và về `/login` với thông báo thành công.
- Email bảo mật dùng chung sự kiện `PasswordChangedEvent` và template `password-changed` với UC-05, gửi sau commit. Lỗi gửi thông báo không hoàn tác mật khẩu; hiện ghi log, chưa có retry/outbox.
- Giới hạn IP của auth áp dụng cả request sai JSON/validation; HTTP 429 có `Retry-After`. Không yêu cầu OTP cho UC-06 vì đã xác nhận bằng mật khẩu hiện tại.

## API Ví tiền & Ký quỹ (UC-34 & UC-35)

Luồng giao diện:
- **SCR-42 (Nạp tiền vào ví)** tại `/wallet`: xem số dư khả dụng, tiền ký quỹ, tổng tài sản; chọn hạn mức nạp (tối thiểu 20.000 đ, tối đa 10.000.000 đ/lần, trần số dư 20.000.000 đ theo BR-R-050/PRM-058) và chuyển hướng tới VNPay Sandbox.
- **SCR-22 (Thanh toán đơn và ký quỹ)** tại `/orders/:id/payment`: xem chi tiết đơn hàng, chọn thanh toán bằng Ví FPTPost (trừ số dư khả dụng, giữ tiền ký quỹ Escrow, cập nhật trạng thái đơn `ESCROWED`) hoặc thanh toán trực tiếp qua VNPay Sandbox.
- **Biên lai giao dịch VNPay** tại `/payment/vnpay-return`: tự động kiểm tra chữ ký SHA-512, hiển thị biên lai giao dịch minh bạch.

### Chi tiết các Endpoint đã triển khai

| API | Method | Endpoint | Quyền | Mục đích |
| --- | --- | --- | --- | --- |
| API-084 | POST | `/api/v1/wallet/topups` | Đăng nhập | Tạo giao dịch nạp ví qua VNPay (UC-34) |
| API-085 | GET | `/api/v1/payment/vnpay-return` | Công khai | Nhận phản hồi thanh toán người dùng từ VNPay |
| API-086 | GET/POST | `/api/v1/payment/vnpay-ipn` | Webhook IPN | Nhận IPN từ VNPay, xử lý cộng tiền/ký quỹ & sổ cái kép (UC-42) |
| API-087 | POST | `/api/v1/orders/{id}/payment` | SENDER | Thanh toán & ký quỹ đơn hàng qua Ví hoặc VNPay (UC-35) |
| API-088 | GET | `/api/v1/wallet` | Đăng nhập | Xem thông tin số dư ví và tiền ký quỹ (UC-38) |
| API-092 | GET | `/api/v1/orders/{id}/escrow` | Đăng nhập (Sender/Courier/Admin) | Xem thông tin ký quỹ đơn hàng (UC-36) |
| API-093 | POST | `/api/v1/admin/escrow/{orderId}/release` | ADMIN | Admin giải ngân tiền ký quỹ thủ công cho shipper (UC-36) |

### Request & Response Payload

#### 1. Nạp tiền vào ví (API-084)
- **Request Body**:
  ```json
  {
    "amount": 100000,
    "bankCode": "NCB",
    "language": "vn"
  }
  ```
- **Validation**:
  - `amount` >= 20.000 VND [PRM-056].
  - `amount` <= 10.000.000 VND [PRM-057].
  - `currentBalance + amount` <= 20.000.000 VND [PRM-058 / BR-R-050]. Vượt quá trả `WALLET_BALANCE_LIMIT_EXCEEDED` (HTTP 400).
- **Response `data`**:
  ```json
  {
    "code": "00",
    "message": "success",
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
    "txnRef": "TOPUP-1718000000-A1B2C3"
  }
  ```

#### 2. Thanh toán đơn hàng & Ký quỹ (API-087)
- **Request Body**:
  ```json
  {
    "paymentMethod": "WALLET", // hoặc "VNPAY"
    "bankCode": "NCB", // Tùy chọn khi paymentMethod là VNPAY
    "language": "vn"
  }
  ```
- **Quyền & Kiểm tra**:
  - Bắt buộc có role `SENDER` hoặc `ADMIN`.
  - Phải là chủ đơn hàng (`order.senderId == currentUser.id`).
  - Đơn hàng phải ở trạng thái hợp lệ (`PENDING_ESCROW`, `CONFIRMED`, `DRAFT`). Nếu đã `ESCROWED` trả thông báo thành công hoặc không trừ tiền trùng lặp.
- **Xử lý theo phương thức**:
  - **`WALLET`**: Kiểm tra `wallet.available >= order.declaredValue`. Trừ số dư khả dụng, tăng số dư `escrowHeld`, tạo bản ghi bảng `escrows` (`status = HELD`, `paymentMethod = WALLET`), ghi nhận bút toán kép Sổ cái (`ledger_transactions` và `ledger_entries`), chuyển trạng thái đơn hàng sang `ESCROWED`.
  - **`VNPAY`**: Sinh URL thanh toán VNPay với mục đích `ORDER_ESCROW`. Khi IPN VNPay trả về mã `00`, hệ thống tự động tạo `escrows`, ghi nhận bút toán kép và chuyển trạng thái đơn hàng sang `ESCROWED`.

#### 3. Toàn vẹn dữ liệu & Idempotency
- Mọi thao tác biến động số dư và sổ cái được bọc trong `@Transactional` với khóa bi quan (`PESSIMISTIC_WRITE`) trên bản ghi `wallets`.
- VNPay IPN (`/api/v1/payment/vnpay-ipn`) kiểm tra trạng thái giao dịch trước khi xử lý: nếu giao dịch không còn ở trạng thái `PENDING`, trả về ngay mã `02` (Order already confirmed) theo đúng quy chuẩn VNPay, ngăn chặn hoàn toàn việc cộng tiền hoặc ký quỹ trùng lặp.
- Hệ thống Sổ cái kép (`ledger_transactions` và `ledger_entries`):
  - Nạp ví: Ghi Nợ (DEBIT) tài khoản trung gian `VNPAY_CLEARING`, Ghi Có (CREDIT) tài khoản ví `USER_WALLET_{userId}`.
  - Ký quỹ bằng ví: Ghi Nợ (DEBIT) `USER_WALLET_{userId}`, Ghi Có (CREDIT) `ESCROW_ORDER_{orderId}`.
  - Ký quỹ qua VNPay: Ghi Nợ (DEBIT) `VNPAY_CLEARING`, Ghi Có (CREDIT) `ESCROW_ORDER_{orderId}`.
  - Giải ngân ký quỹ (UC-36): Ghi Nợ (DEBIT) `ESCROW_ORDER_{orderId}` (giảm nghĩa vụ giam giữ), Ghi Có (CREDIT) `USER_WALLET_{courierId}` (tăng số dư khả dụng người giao). Idempotency key: `ESCROW_RELEASE_{orderId}_{escrowRef}`.

#### 4. Xem thông tin ký quỹ & Giải ngân cho người giao (UC-36, API-092 & API-093)
- **Xem thông tin ký quỹ (API-092)**:
  - `GET /api/v1/orders/{id}/escrow`
  - Quyền: Đăng nhập. Người xem phải là Người gửi (`senderId`), Người giao (`courierId`), hoặc `ADMIN`. Trả `ESCROW_ACCESS_DENIED` (HTTP 403) nếu không có quyền.
  - Dữ liệu trả về: `id`, `orderId`, `orderCode`, `senderId`, `courierId`, `amount`, `status` (`HELD`, `RELEASED`, `REFUNDED`), `paymentMethod` (`WALLET`, `VNPAY`), `releaseAt`, `frozen`, `createdAt`, `updatedAt`.
- **Admin giải ngân tiền ký quỹ thủ công (API-093)**:
  - `POST /api/v1/admin/escrow/{orderId}/release`
  - Quyền: Bắt buộc role `ADMIN`.
  - Tiền điều kiện: Đơn hàng đã có người giao (`courierId != null`), khoản ký quỹ ở trạng thái `HELD`, không bị phong tỏa (`frozen == false`).
  - Xử lý: Giảm `escrowHeld` của sender (nếu ký quỹ từ ví), cộng `available` của shipper (tự động khởi tạo ví nếu chưa có), cập nhật `escrow.status = RELEASED` và `releaseAt = now()`, ghi nhận bút toán kép Sổ cái (Ledger).


Google login và KYC chưa triển khai.
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
body lỗi dùng cùng ID và log có ID. Với lỗi 5xx, frontend cho xem/sao chép mã trong phần chi tiết hỗ trợ mặc định thu gọn.
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

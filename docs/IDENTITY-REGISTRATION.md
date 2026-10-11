# UC-01–02 — Đăng ký và xác thực email

Phạm vi: SCR-03, màn hình OTP `/verify`, API-001/002/003; UC-01–02 theo BR-F-001,
BR-N-016, BR-R-001/002/003, BR-C-011/022. Đăng ký và xác thực email được triển khai
cùng nhánh UC-01 để hoàn thành luồng. Đăng nhập và token thuộc UC-03.

## Luồng hiện thực

1. `/register` tải nội dung và phiên bản điều khoản/chính sách từ backend.
2. Người dùng nhập họ tên, email, SĐT, mật khẩu; xác nhận đủ 18 tuổi và đồng ý
   hai chính sách. Nhập lại mật khẩu là kiểm tra phía giao diện.
3. Backend chuẩn hóa email chữ thường và số di động Việt Nam về `+84...`;
   kiểm tra định dạng, mật khẩu, phiên bản chính sách, blacklist và thông tin trùng.
4. Một transaction SQL tạo tài khoản PENDING_VERIFICATION, role SENDER,
   profile họ tên/xác nhận tuổi, hai bản ghi consent và OTP đăng ký.
5. OTP 6 số sinh bằng SecureRandom, lưu BCrypt hash, hạn 5 phút. Gửi qua SMTP email;
   thành công trả HTTP 201 và chuyển sang `/verify`. Không trả token,
   mật khẩu hoặc OTP trong JSON; tài khoản vẫn chờ xác thực.
6. Nhập OTP đúng, chưa hết hạn và chưa dùng: transaction đánh dấu OTP đã dùng,
   lưu `emailVerifiedAt`, chuyển tài khoản sang ACTIVE. Không xác thực SĐT hoặc tạo phiên đăng nhập.
7. Mỗi mã cho phép tối đa 5 lần nhập sai. Bộ đếm được commit cả khi trả lỗi HTTP;
   sau lần thứ 5, mã không thể dùng dù nhập đúng.
8. Gửi lại sau ít nhất 60 giây tính từ email OTP mới nhất; tạo challenge mới và vô hiệu
   mã cũ. SMTP lỗi thì rollback cả challenge mới và việc vô hiệu mã cũ.

Frontend lưu challenge UUID, email che, hạn mã và thời điểm gửi lại trong sessionStorage
để tải lại trang vẫn tiếp tục được; không lưu mật khẩu/OTP. Xóa challenge khi xác thực thành công.
Backend dùng giờ máy chủ để kiểm tra TTL/cooldown và khóa User trong SQL để serialize
verify/resend, ngăn dùng một OTP nhiều lần hoặc mất bộ đếm khi có yêu cầu đồng thời.

Frontend dùng chung AuthLayout với đăng nhập: hai cột desktop, một cột mobile.
RegisterPage ghép layout; RegisterForm dùng React Hook Form và registerSchema (Zod);
useRegister tải chính sách, xử lý submit/lỗi và chuyển sang OTP qua authService.
Ba ô xác nhận tuổi/điều khoản/chính sách dữ liệu được giữ riêng. Tên chính sách mở
PolicyDialog, hỗ trợ đóng bằng Esc và bàn phím; không hiển thị phiên bản trong form.
Payload vẫn gửi termsVersion/privacyVersion từ backend. Nếu chính sách thay đổi,
hai ô đồng ý được bỏ chọn, tải lại chính sách và yêu cầu người dùng đồng ý lại.

Mật khẩu BCrypt cost 12; tối thiểu 8 ký tự có chữ hoa/thường/số, tối đa 72 byte UTF-8.
Email và phone unique ở SQL để bảo vệ cả yêu cầu đồng thời. Phone blacklist dùng
SHA-256 trên số `+84...`; module quản trị blacklist phải dùng cùng normalization/hash.
Đăng ký không nhận CCCD; phần kiểm tra CCCD thuộc KYC.

## API

Các API AUTH được nhóm trong `identity/controller/AuthenticationController`.
Controller gọi `UserService.createUser()` cho UC-01.
`UserMapper` chuyển đổi request/entity/response; `OtpService.generateAndSendOtp()`
sinh và lưu mã băm rồi gọi `common.mail.service.EmailService.sendEmail()` qua
`EmailRequest` và `EmailTemplateType.VERIFY_EMAIL`. Nội dung HTML, text và tiêu đề
nằm trong `resources/templates/mail`; xem [hướng dẫn template email](EMAIL.md).
Controller gọi `OtpService.verifyOtp()` và `resendOtp()` cho UC-02.
Login/refresh/logout phiên hiện tại đã có; xem [UC-03 đăng nhập](IDENTITY-LOGIN.md).
Handler lỗi Identity nằm trong package exception.

DTO dùng class Lombok, chia thành `dto/request/UserCreationRequest` và
`dto/response/RegisterResponse`, `RegistrationPoliciesResponse`, `PolicyResponse`.
Mỗi DTO ở file riêng, dùng cùng bộ annotation Lombok. Request chuẩn hóa
email/họ tên/phone qua setter trước validation, không đưa mật khẩu vào `toString()`
hoặc JSON khi serialize. URL, tên field JSON và HTTP status giữ nguyên để frontend
tiếp tục dùng cùng API. Entity thuộc `model`; enum trạng thái `UserStatus` thuộc
`enums`, enum mã lỗi `IdentityErrorCode` thuộc `exception`.

OTP dùng `OtpType` trong `enums`, lưu tên enum vào cột `purpose` bằng
`@Enumerated(EnumType.STRING)`. Verify/resend đăng ký chỉ nhận `REGISTRATION`;
không chấp nhận OTP `PASSWORD_RESET` hoặc `EMAIL_CHANGE`. Hai loại này chưa có
endpoint nghiệp vụ, sẽ triển khai khi làm UC tương ứng. `User.email` có
`@NotBlank`, `@Email`, `@Size(max = 254)` để kiểm tra khi JPA persist/update;
request vẫn giữ validation để trả lỗi field ngay tại API.

- `GET /api/v1/auth/registration-policies`: nội dung/phiên bản TERMS, PRIVACY và độ dài mật khẩu tối thiểu.
- `POST /api/v1/auth/register`: `fullName`, `email`, `phone`, `password`,
  `adultConfirmed`, `termsAccepted`, `privacyAccepted`, `termsVersion`, `privacyVersion`.
- `POST /api/v1/auth/otp/verify`: `challengeId` (UUID), `otp` (chuỗi 6 chữ số).
  Thành công trả `data: {userId, status: ACTIVE, verificationChannel: EMAIL, verifiedAt}`.
- `POST /api/v1/auth/otp/resend`: `challengeId`. Thành công trả challenge UUID mới,
  `verificationChannel`, `maskedTarget`, `expiresAt`, `resendAvailableAt`.
  Frontend thay challenge đang lưu; mọi mã/challenge trước đó không dùng để verify được.
  Challenge cũ vẫn có thể yêu cầu resend nhưng phải chờ cooldown của challenge mới nhất.

Nội dung chính sách nằm trong [terms.json](../server/src/main/resources/policies/terms.json)
và [privacy.json](../server/src/main/resources/policies/privacy.json), gồm `title` và `paragraphs`.
`PolicyService` đọc các resource một lần khi backend khởi động và trả dữ liệu
để React hiển thị. Phiên bản vẫn lấy từ `app.registration.terms-version`/`privacy-version`
(`TERMS_VERSION`, `PRIVACY_VERSION`); không khai báo version lần nữa trong JSON.
Khi sửa nội dung, đổi version tương ứng trong cấu hình triển khai và build/restart backend.
Luồng kiểm tra phiên bản khi đăng ký và lưu consent dùng cùng cấu hình này.

Response thành công vẫn dùng ApiResponse của base; lỗi dùng [Problem Details](API-ERRORS.md). `data` gồm `userId` (UUID public),
`status`, `challengeId` (UUID public), `verificationChannel: EMAIL`, `maskedTarget`, `expiresAt`,
`resendAvailableAt`.
Không đưa BIGINT nội bộ hoặc dữ liệu nhạy cảm ra response.

| Mã lỗi | HTTP | Ý nghĩa |
| --- | --- | --- |
| SYS_INVALID_REQUEST | 400 | DTO/JSON không hợp lệ; errors là danh sách field/code/message |
| AUTH_EMAIL_ALREADY_EXISTS | 409 | Email đã được sử dụng |
| AUTH_PHONE_ALREADY_EXISTS | 409 | SĐT đã được sử dụng |
| AUTH_PASSWORD_INVALID | 400 | Mật khẩu không đạt quy tắc |
| AUTH_POLICY_CHANGED | 409 | Phiên bản chính sách thay đổi, phải đọc/đồng ý lại |
| AUTH_REGISTRATION_BLOCKED | 403 | Thông tin bị chặn đăng ký |
| AUTH_OTP_DELIVERY_FAILED | 503 | SMTP thất bại; registration transaction rollback |
| AUTH_REGISTRATION_CONFLICT | 409 | Xung đột ràng buộc khi đăng ký đồng thời |
| AUTH_OTP_NOT_FOUND | 404 | Challenge không tồn tại hoặc sai mục đích |
| AUTH_OTP_INVALID | 400 | Mã sai, bộ đếm đã tăng |
| AUTH_OTP_EXPIRED | 410 | Mã đã hết hạn |
| AUTH_OTP_ATTEMPTS_EXCEEDED | 400 | Đã nhập sai đủ 5 lần, phải yêu cầu mã mới |
| AUTH_OTP_ALREADY_USED | 409 | Mã đã dùng |
| AUTH_OTP_SUPERSEDED | 409 | Challenge đã được thay thế, dùng email mới nhất |
| AUTH_OTP_RESEND_TOO_SOON | 429 | Gửi lại quá sớm; Retry-After là số giây còn lại |
| AUTH_VERIFICATION_BLOCKED | 403 | Tài khoản đã active, bị khóa hoặc bị xóa |
| RATE_LIMITED | 429 | Giới hạn request, Retry-After: 60 |

## Schema và ranh giới module

Hibernate tạo/cập nhật users, roles/user_roles, user_profiles, consents, otp_codes và blacklist
từ entity. Role là dữ liệu nền bắt buộc do `IdentityDataInitializer` tạo,
không phụ thuộc profile demo.
Entity nghiệp vụ Identity dùng BIGINT nội bộ và public UUID theo SPEC;
Product demo giữ khóa UUID hiện tại. `@Version` có ở User và OtpCode.

Owner Identity quản lý users/roles/otp. Người triển khai hồ sơ/KYC mở rộng
user_profiles bằng entity; không tạo bảng người dùng/role trùng.
Các thay đổi schema và dữ liệu cũ tuân theo [hướng dẫn code first](DATABASE-SCHEMA.md).

## Chạy và kiểm tra email

`docker compose up --build -d` chạy thêm Mailpit. Mở `/register` trên frontend,
gửi đăng ký, sau đó xem email tại `http://localhost:8025` (MAILPIT_PORT).
SMTP local ở 1025 (MAILPIT_SMTP_PORT); Compose mặc định dùng mailpit:1025. Các cổng chỉ bind localhost.
Mailpit dùng cho phát triển, không gửi mail đến hộp thư thật.

Chạy backend ngoài Docker: `docker compose up -d sqlserver sqlserver-init mongodb mailpit`,
đặt SMTP_HOST=localhost và SMTP_PORT bằng cổng đã cấu hình. Với SMTP thật,
đặt host/port/username/password, SMTP_AUTH/SMTP_STARTTLS và MAIL_FROM theo provider.
Compose đọc các biến SMTP từ `.env` ở gốc; đổi cấu hình rồi chạy `docker compose up -d`.
MAILPIT_SMTP_PORT chỉ là cổng nhận SMTP local, không dùng làm cổng của provider.
Địa chỉ/tên người gửi thuộc `app.mail`, dùng chung cho các module;
`app.registration` chỉ cấu hình nghiệp vụ đăng ký.
Không commit mật khẩu SMTP hoặc nội dung email OTP.

## Giới hạn và bước tiếp theo

- Điều khoản/chính sách hiện là nội dung nháp `draft-2026-10-09`, phản ánh OQ-09
  còn mở. Nhóm phải duyệt nội dung và đổi version khi thay nội dung trước khi phát hành.
- UC-01 gửi SMTP đồng bộ, timeout 5 giây; lỗi gửi rollback để người dùng đăng ký lại.
  Gửi mail và SQL commit không phải một transaction phân tán: mail có thể đã gửi
  nhưng SQL commit thất bại sau đó. Đường outbox/retry của UC-53 cần triển khai riêng;
  không coi adapter SMTP này là pipeline thông báo hoàn chỉnh.
- Hạn request dùng fixed window 10/phút theo IP kết nối backend, đếm cả request sai.
  Store nằm trong một instance. Backend không tin X-Forwarded-For do client tự gửi.
  Khi chạy sau Nginx, các request proxy dùng chung quota; cần rate limit tại gateway
  hoặc cấu hình trusted proxy/IP và store dùng chung trước triển khai nhiều instance.
- UC-02 hiện xác thực email đăng ký. SMS và OTP cho quên mật khẩu/giao hàng thuộc
  luồng riêng, không nhận challenge mục đích khác ở hai endpoint này.
- Kích hoạt tài khoản không tạo phiên hay token. Đăng nhập và kiểm soát quyền các API
  nghiệp vụ sẽ triển khai ở UC-03 và module tương ứng.
- Xác nhận 18 tuổi ở UC-01 là lời xác nhận; kiểm tra tuổi/CCCD thực hiện ở KYC.

Kiểm thử HTTP/transaction trên H2 gồm mã đúng/sai/hết hạn, cooldown, gửi lại,
rollback SMTP, tài khoản khóa/xóa và yêu cầu verify/resend đồng thời.
Kiểm tra SQL Server 2022 và luồng UI → API → Mailpit riêng; H2 không thay kiểm tra T-SQL.

Tham khảo: [Spring Boot email](https://docs.spring.io/spring-boot/3.5/reference/io/email.html),
[Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).

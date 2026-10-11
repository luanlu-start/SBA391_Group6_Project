# UC-01 — Đăng ký tài khoản

Phạm vi: SCR-03, API-001; UC-01 theo BR-F-001, BR-N-016, BR-R-001/002,
BR-C-011/022. Tài liệu nguồn giữ nguyên. Đây là chức năng đăng ký; xác thực/gửi lại
OTP (UC-02), đăng nhập và token (UC-03) là các bước triển khai tiếp theo.

## Luồng hiện thực

1. `/register` tải nội dung và phiên bản điều khoản/chính sách từ backend.
2. Người dùng nhập họ tên, email, SĐT, mật khẩu; xác nhận đủ 18 tuổi và đồng ý
   hai chính sách. Nhập lại mật khẩu là kiểm tra phía giao diện.
3. Backend chuẩn hóa email chữ thường và số di động Việt Nam về `+84...`;
   kiểm tra định dạng, mật khẩu, phiên bản chính sách, blacklist và thông tin trùng.
4. Một transaction SQL tạo tài khoản PENDING_VERIFICATION, role SENDER,
   profile họ tên/xác nhận tuổi, hai bản ghi consent và OTP đăng ký.
5. OTP 6 số sinh bằng SecureRandom, lưu BCrypt hash, hạn 5 phút. Gửi qua SMTP email;
   thành công trả HTTP 201 và màn hình hướng dẫn kiểm tra email. Không trả token,
   mật khẩu hoặc OTP trong JSON, không kích hoạt tài khoản.

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
Các method xác thực OTP/login/refresh/logout
và `AuthenticationService` sẽ bổ sung khi triển khai UC tương ứng.
Handler lỗi Identity nằm trong package exception.

DTO dùng class Lombok, chia thành `dto/request/UserCreationRequest` và
`dto/response/RegisterResponse`, `RegistrationPoliciesResponse`, `PolicyResponse`.
Mỗi DTO ở file riêng, dùng cùng bộ annotation Lombok. Request chuẩn hóa
email/họ tên/phone qua setter trước validation, không đưa mật khẩu vào `toString()`
hoặc JSON khi serialize. URL, tên field JSON và HTTP status giữ nguyên để frontend
tiếp tục dùng cùng API. Entity thuộc `model`; enum trạng thái `UserStatus` thuộc
`enums`, enum mã lỗi `IdentityErrorCode` thuộc `exception`.

- `GET /api/v1/auth/registration-policies`: nội dung/phiên bản TERMS, PRIVACY và độ dài mật khẩu tối thiểu.
- `POST /api/v1/auth/register`: `fullName`, `email`, `phone`, `password`,
  `adultConfirmed`, `termsAccepted`, `privacyAccepted`, `termsVersion`, `privacyVersion`.

Nội dung chính sách nằm trong [terms.json](../server/src/main/resources/policies/terms.json)
và [privacy.json](../server/src/main/resources/policies/privacy.json), gồm `title` và `paragraphs`.
`RegistrationPoliciesService` đọc các resource một lần khi backend khởi động và trả dữ liệu
để React hiển thị. Phiên bản vẫn lấy từ `app.registration.terms-version`/`privacy-version`
(`TERMS_VERSION`, `PRIVACY_VERSION`); không khai báo version lần nữa trong JSON.
Khi sửa nội dung, đổi version tương ứng trong cấu hình triển khai và build/restart backend.
Luồng kiểm tra phiên bản khi đăng ký và lưu consent dùng cùng cấu hình này.

Response thành công vẫn dùng ApiResponse của base; lỗi dùng [Problem Details](API-ERRORS.md). `data` gồm `userId` (UUID public),
`status`, `challengeId` (UUID public), `verificationChannel: EMAIL`, `maskedTarget`, `expiresAt`.
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
| RATE_LIMITED | 429 | Giới hạn request, Retry-After: 60 |

## Schema và ranh giới module

V2 tạo users, roles/user_roles, user_profiles, consents, otp_codes và blacklist.
Role là dữ liệu nền bắt buộc trong migration; không phụ thuộc profile demo.
Entity nghiệp vụ Identity dùng BIGINT nội bộ và public UUID theo SPEC;
Product demo giữ khóa UUID hiện tại. `@Version` có ở User và OtpCode.

Owner Identity quản lý users/roles/otp. Người triển khai hồ sơ/KYC mở rộng
user_profiles bằng migration mới; không tạo bảng người dùng/role trùng.
Không sửa V2 sau khi đã áp dụng ở database chung.

## Chạy và kiểm tra email

`docker compose up --build -d` chạy thêm Mailpit. Mở `/register` trên frontend,
gửi đăng ký, sau đó xem email tại `http://localhost:8025` (MAILPIT_PORT).
SMTP local ở 1025; Compose backend dùng mailpit:1025. Các cổng chỉ bind localhost.
Mailpit dùng cho phát triển, không gửi mail đến hộp thư thật.

Chạy backend ngoài Docker: `docker compose up -d sqlserver sqlserver-init mongodb mailpit`,
đặt SMTP_HOST=localhost và SMTP_PORT bằng cổng đã cấu hình. Với SMTP thật,
đặt host/port/username/password, SMTP_AUTH/SMTP_STARTTLS và MAIL_FROM theo provider.
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
- UC-02 sẽ dùng challengeId/OTP để xác thực, max tries, cooldown và gửi lại.
  Màn hình hiện chỉ thông báo gửi email; không có form xác thực giả hoặc endpoint UC-02.
- Xác nhận 18 tuổi ở UC-01 là lời xác nhận; kiểm tra tuổi/CCCD thực hiện ở KYC.

Kiểm thử: HTTP/transaction trên H2, giới hạn request, SQL Server 2022 migration/unique
và luồng UI → API → mail thật trong Mailpit. H2 không thay kiểm tra constraint T-SQL.

Tham khảo: [Spring Boot email](https://docs.spring.io/spring-boot/3.5/reference/io/email.html),
[Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).

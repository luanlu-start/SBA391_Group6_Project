# UC-03 — Đăng nhập và phiên xác thực

Theo UC-03, BR-F-003, BR-R-004/005/006 và BR-C-005; SPEC mục 7.1.
Luồng: `/register` → `/verify` → `/login` → `/account`.
Đăng ký tạo User và role SENDER với PENDING_VERIFICATION; OTP email chuyển ACTIVE.
Chỉ tài khoản ACTIVE, đã xác thực email và chưa xóa mới được đăng nhập.

## API

| Method | Endpoint | Yêu cầu |
| --- | --- | --- |
| GET | `/api/v1/auth/csrf` | Công khai; trả headerName/token và cookie XSRF-TOKEN |
| POST | `/api/v1/auth/login` | Công khai, CSRF; JSON `{identifier, password}`; email hoặc SĐT |
| POST | `/api/v1/auth/refresh` | CSRF và cookie fptpost_refresh; không nhận refresh token trong JSON |
| POST | `/api/v1/auth/logout` | Bearer và CSRF; thu hồi phiên hiện tại, xóa refresh cookie |
| GET | `/api/v1/me` | Bearer; thông tin người dùng hiện tại |

Login/refresh trả ApiResponse với `data: {accessToken, tokenType, expiresAt, user}`.
UserResponse gồm public id, fullName, email, phone, status, roles và emailVerifiedAt;
không trả internal ID, password hash hoặc refresh token.
Trước login/refresh/logout gọi GET csrf, gửi cookie và token qua header được trả về.
CORS chỉ chấp nhận origin cấu hình; axios dùng withCredentials cho cookie.

## Token và khóa đăng nhập

- Access JWT HS256, khóa tối thiểu 256 bit; claim iss/aud/sub/roles/sid/jti/iat/exp.
  TTL mặc định 15 phút; Spring Security xác thực chữ ký, hạn, issuer/audience và phiên SQL.
  Phiên thu hồi/hết hạn hoặc tài khoản khóa/xóa mất hiệu lực ngay khi gọi API tiếp.
- Refresh token ngẫu nhiên 256 bit, chỉ lưu SHA-256 trong `sessions`.
  Cookie HttpOnly, SameSite=Strict, Path=/api/v1/auth, TTL 7 ngày.
  Refresh thay phiên/token cũ; dùng lại token đã thay sẽ thu hồi mọi phiên của tài khoản.
- Frontend giữ access token trong bộ nhớ, không ghi localStorage/sessionStorage.
  Tải lại trang phục hồi phiên qua refresh cookie; nhiều request 401 dùng chung một lần refresh.
- Sai thông tin trả cùng AUTH_LOGIN_FAILED/401, không chỉ rõ trường sai.
  Tài khoản không tồn tại vẫn so sánh với BCrypt hash giả.
- Sai 5 lần liên tiếp khóa đăng nhập 15 phút; lần tiếp theo trả 429 và Retry-After.
  Bộ đếm tài khoản lưu SQL, dùng khóa ghi để tránh mất lượt khi đồng thời.
  Bộ đếm IP có giới hạn bộ nhớ và nằm trong từng instance, không phải bộ đếm phân tán.
- Đăng nhập đúng trước khi khóa đặt lại bộ đếm; phiên thứ 6 thu hồi phiên cũ nhất.
- Account page hiển thị tài khoản/role và đăng xuất. Danh sách/thu hồi từng thiết bị (UC-04),
  Google login (Could), quên/đổi mật khẩu và chỉnh hồ sơ chưa triển khai.

## Frontend

Trang đăng nhập theo cấu trúc form/hook/service:

```text
client/src/
├── pages/LoginPage.tsx                 # Ghép layout và form
├── layouts/AuthLayout.tsx              # Hai cột desktop, một cột mobile
├── features/auth/
│   ├── components/LoginForm.tsx        # React Hook Form, lỗi dưới từng trường
│   ├── hooks/useLogin.ts               # Submit, lỗi API, cooldown, chuyển trang
│   └── schemas/loginSchema.ts          # Zod và kiểu LoginFormValues
└── services/authService.ts             # Login/refresh/logout/me qua axiosClient
```

Email/SĐT được trim; mật khẩu giữ nguyên. Schema login chỉ kiểm tra bắt buộc và
giới hạn độ dài, không áp lại toàn bộ chính sách tạo mật khẩu khi đăng nhập.
Form hỗ trợ hiện/ẩn mật khẩu, khóa submit khi đang gọi API/khôi phục phiên/cooldown,
aria-invalid/aria-describedby cho lỗi field. Khi nhập đúng mật khẩu của tài khoản
PENDING_VERIFICATION, login trả HTTP 403 `AUTH_ACCOUNT_UNVERIFIED` kèm extension
`verification` (challengeId, verificationChannel, maskedTarget, expiresAt, resendAvailableAt).
Frontend lưu metadata mới và tự chuyển sang `/verify`, kể cả khi đăng nhập bằng SĐT
hoặc mở trình duyệt mới. Không tạo phiên/JWT, không trả OTP và không gửi email mới khi login;
mã hết hạn có thể gửi lại từ trang xác thực theo cooldown hiện có.

## Cấu hình và schema

`app.authentication` gom TTL, giới hạn sai, thời gian khóa và số phiên.
`JWT_SECRET` là Base64 của ít nhất 32 byte ngẫu nhiên, lưu trong .env/secret manager.
Các instance phải dùng cùng khóa. Dev để trống sinh khóa tạm mỗi lần khởi động;
prod bắt buộc JWT_SECRET và AUTH_COOKIE_SECURE=true, dùng HTTPS.
Local HTTP đặt AUTH_COOKIE_SECURE=false để browser gửi cookie được.

Hibernate update tạo `sessions`; User thêm failed_login_count (DEFAULT 0) và login_locked_until.
Không có migration hoặc seed tài khoản/mật khẩu mặc định.
Public endpoint nằm trong SecurityConfig; mọi API khác mặc định cần Bearer.
Product/Activity vẫn là ví dụ kỹ thuật nhưng đã yêu cầu đăng nhập; endpoint actuator
ngoài health yêu cầu ADMIN. Module mới bổ sung role/ownership ở service, không dựa vào menu frontend.

Tham khảo [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
và [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

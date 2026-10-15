# Bàn giao cấu hình FPTPost của nhóm

Workspace hiện ở `feature/UC-60-ThuanTC-tra-cuu-audit`, kế thừa UC-46 tại cef0f4e và theo quy định nhánh của nhóm.
Các thay đổi nghiệp vụ được giữ nguyên; thiết lập này bổ sung cấu hình chạy, launcher
và sửa hẹp Mockito stubbing trong test harness. Nhánh UC-46 đã được đổi tên theo yêu cầu của Thuận.
Ngày 14/10, Thuận yêu cầu commit và push bản bàn giao trên nhánh này.

**Bàn giao đợt 1 ngày 14/10/2026:** P00 và bản độc lập P01/UC-46 đã triển khai và kiểm thử.
Chi tiết chức năng, API, kết quả kiểm thử và phần còn chờ Pricing: [ROUND1-HANDOFF](docs/plans/ROUND1-HANDOFF.md).
Trang quản trị: `http://localhost:15173/admin/params`, yêu cầu tài khoản có role `ADMIN`.

**Bàn giao UC-60 ngày 15/10/2026:** `/admin/audit` và GET `/api/v1/admin/audit-logs`
đã có query/phân trang/chi tiết/masking, index và retention AUDIT_KEEP.
Xem [UC60-HANDOFF](docs/plans/UC60-HANDOFF.md) và [UC60-CONTRACT](docs/plans/UC60-CONTRACT.md).
Các số liệu kiểm thử đợt đầu bên dưới là lịch sử; báo cáo UC-60 chứa kết quả mới nhất.

**Cập nhật merge develop ngày 14/10:** đã ghép UC-12/UC-34 từ `13eeb41`, giữ route
đơn hàng/thanh toán và ADMIN guard của UC-46. Kiểm thử sau merge và runtime xem
[MERGE-DEVELOP-CHECK](docs/plans/MERGE-DEVELOP-CHECK.md). Thông tin SMTP vẫn lấy từ `.env`;
không dùng credentials dự phòng gán trực tiếp trong source.

**Cấu hình hiện tại dùng SQL Server 2022 và MongoDB riêng trong Docker, Gmail SMTP,
profile `dev,demo`.** SQL Server Windows không được dùng hoặc thay đổi.
Backend package đã PASS **144 tests**, Frontend lint/typecheck/build PASS.
SMTP đã kiểm tra kết nối/xác thực thành công, không gửi email kiểm thử.
**Full Docker runtime đã PASS:** SQL/Mongo/Backend/Frontend healthy,
`sqlserver-init` Exited (0); API và Actuator UP, Frontend/proxy health trả 200.

## 1. Chuẩn bị và vị trí credentials

Chạy lệnh từ thư mục gốc. Cần Docker Desktop với **Linux containers**.
Chạy toàn bộ Docker không cần cài Java/Node/Maven trên host. Chạy IDE/terminal cần
**JDK 21, Maven 3.9.x, Node.js 21.7.x** và bundled npm.

`scripts/dev.ps1` nằm trong mã nguồn, dùng bản cài trên PATH/JAVA_HOME hoặc ưu tiên `.tools`
nếu có. Máy này có `.tools/jdk-21`, `.tools/apache-maven-3.9.11` và
`.tools/node-v21.7.3-win-x64` (JDK 21.0.12.1, Maven 3.9.11, Node 21.7.3/npm 10.5.0).

**`.env` gốc là nguồn cấu hình của leader.** Trên máy đã setup, giữ nguyên file này.
Trên clone mới, tạo từ mẫu rồi điền các giá trị riêng của nhóm:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

| File / biến | Vai trò |
| --- | --- |
| `.env` → `SQLSERVER_PASSWORD` | Password SQL user `sa` của container dự án |
| `.env` → `JWT_SECRET` | Khóa ký JWT Base64 chứa ít nhất 32 byte; không phải password website |
| `.env` → `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | Gmail, app password và địa chỉ người gửi |
| `server/.env` | Bản sinh lại cho IDE: SQL/JWT/SMTP cùng giá trị từ `.env` gốc |
| `client/.env` | Bản sinh lại chứa URL API/port Vite; không chứa SQL/JWT/SMTP secrets |
| `.tools/demo-account.env` → `EMAIL`, `PASSWORD` | Account ứng dụng đã xác thực để kiểm tra giao diện |

Không có account/password ứng dụng mặc định. Password đăng nhập website nằm riêng
trong `.tools/demo-account.env`, không phải `SQLSERVER_PASSWORD` trong `server/.env`.
MongoDB local không bật xác thực. Các `.env.example` chỉ là templates, không có secrets thật.
Giữ nguyên `SMTP_PASSWORD` đã cung cấp, kể cả khoảng trắng nếu có.

## 2. Cổng và kết nối

| Thành phần | Địa chỉ host |
| --- | --- |
| Frontend | http://localhost:15173 |
| Backend API | http://localhost:18080/api/v1 |
| Swagger | http://localhost:18080/swagger-ui/index.html |
| OpenAPI | http://localhost:18080/api-docs |
| API health | http://localhost:18080/api/v1/health |
| Health SQL/Mongo/SMTP | http://localhost:18080/actuator/health |
| SQL Server 2022 | `localhost,31433`; database `group6_project_db`; user `sa` |
| MongoDB 7 | `mongodb://localhost:37017/group6_project_db` |
| Gmail SMTP | `smtp.gmail.com:587`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true` |

Trong mạng Docker, SQL là `sqlserver:1433`, Mongo là `mongodb:27017`, Backend HTTP
nội bộ là `8080`; đây không phải các cổng host ở bảng trên.
Gmail credentials/JWT dùng giá trị đã cung cấp trong `.env` thật, không in trong tài liệu.
Mailpit là profile tùy chọn `mailpit`, không chạy trong cấu hình Gmail mặc định.

## 3. Chạy toàn bộ Docker

```powershell
docker compose up --build -d
docker compose ps -a
```

Các service mặc định: `sqlserver`, `sqlserver-init`, `mongodb`, `server`, `client`.
`sqlserver-init` tạo database nếu thiếu; **Exited (0)** là trạng thái thành công bình thường.
Frontend Docker dùng Nginx và proxy `/api` tới Backend.

```powershell
docker compose logs --tail 100 server sqlserver-init mongodb
docker compose down
```

`down` giữ volumes SQL/Mongo. Không dùng `-v` khi cần giữ dữ liệu.
Volume SQL hiện có đã được backup và password `sa` đồng bộ trước khi chạy cấu hình
nhóm; dữ liệu cũ được giữ. Đổi `MSSQL_SA_PASSWORD` trong env không tự reset password
của instance đã có volume, nên phải cập nhật SQL và env đồng bộ khi đổi password.

## 4. Chạy IDE hoặc terminal với cùng database Docker

Dừng **hai ứng dụng** Docker trước khi chuyển sang IDE/local để tránh trùng cổng:

```powershell
docker compose stop server client
npm run install:all
npm run setup:sync
npm run setup:infra
```

`setup:sync` sinh `server/.env` và `client/.env` từ `.env` gốc.
`setup:infra` chỉ khởi động SQL/init/Mongo, dùng cùng dữ liệu với Docker.

Trong IntelliJ, Run **FPTPost Dev**, gồm:

- **FPTPost Backend**: `com.fptpost.Application`, module `sba391-server`, JDK 21,
  working directory `server`, env file `server/.env`.
- **FPTPost Frontend**: npm `dev` trong `client`, Node/npm local hoặc bản cài phù hợp.
  Vite đọc API/port từ `client/.env`.

Trên máy mới chưa có run configurations, tạo như trên hoặc chạy terminal:

```powershell
npm run dev:local
```

Launcher nạp `.env` gốc vào tiến trình, chọn toolchain và chạy Backend/Frontend.
Có thể chạy riêng bằng hai terminal:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev.ps1 -Mode Server
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev.ps1 -Mode Client
```

Các mode: `Dev`, `Server`, `Client`, `Infra`, `Check`, `Docker`, `Sync`.
`Docker` build/run stack; `Sync` chỉ đồng bộ env. Sau khi đổi `.env`, chạy
`npm run setup:sync` rồi restart IDE; với Docker, recreate/build lại stack.
Stop ứng dụng trong IDE hoặc Ctrl+C ở terminal trước khi chạy lại hai ứng dụng Docker.

## 5. Schema, seed và account

Hibernate `ddl-auto: update` tạo/cập nhật schema khi Backend khởi động.
SQL có Product/Identity và UC-46: `system_parameters`, `parameter_history`,
`outbox_events`; catalog initializer thêm 109 PRM còn thiếu. Mongo lưu
`activity_logs` và `audit_logs`; outbox dispatcher chạy trong Backend, không cần service mới.

Identity initializer tạo roles `SENDER`, `COURIER`, `MODERATOR`, `ADMIN`;
seed demo mới từ develop bổ sung user theo từng role ở profile dev/demo/test/local,
giữ các tài khoản đã tồn tại. API quản trị UC-46 yêu cầu user có role `ADMIN`.
Profile `dev,demo` thêm Product mẫu còn thiếu, giữ các bản ghi đã chỉnh sửa.

Các trang: `/register`, `/verify`, `/login`, `/account`, `/me/security`, `/products`.
Đăng ký bằng cấu hình Gmail sẽ gửi OTP thật; mở email của người đăng ký để xác thực.
Không dùng smoke registration khi đang bật SMTP Gmail.
`/ui-kit` chỉ có ở Vite development, không có trong frontend Docker/Nginx.

## 6. Kiểm tra và bàn giao

```powershell
npm run setup:check
```

Maven local và Docker build đều PASS **144 tests**, không failure/error/skip;
logs `.tools/backend-build-docker-base.log`, `.tools/docker-build-base.log`.
Frontend lint/typecheck/build PASS. `setup:sync` sinh env thành công và
`setup:check` xác nhận API/Actuator UP; CORS cho Frontend `15173` với credentials.
IntelliJ đã chạy Backend bằng `server/.env` sinh tự động, kiểm tra health trên
cổng tạm `18081`, rồi dừng; trạng thái bàn giao hiện chỉ có stack Docker đang chạy.

Trên máy này có helpers local để dùng lại account test, không đăng ký/gửi email:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .tools/Smoke-Test.ps1 -ReuseAccount -ApiOrigin http://localhost:18080 -MailMode Gmail
powershell -NoProfile -ExecutionPolicy Bypass -File .tools/Smoke-Sessions.ps1 -ApiOrigin http://localhost:18080
```

Các helpers chỉ thao tác Product/phiên do test tạo và dọn sau khi kiểm tra.
Baseline và UC-04 đều PASS trên Docker, không đăng ký hoặc gửi email;
log runtime cuối: **`.tools/smoke-docker-base.log`**.

`scripts/dev.ps1`, Compose, templates và tài liệu này nằm trong mã nguồn của workspace.
Các chỉnh sửa setup được bàn giao cùng code UC-46; tra commit và remote bằng Git.
Các `.env` thật, `.tools`, `.idea`, account test và logs được Git ignore, không có
sẵn khi clone. Điền `.env` trên máy mới, cài toolchain nếu chạy local, rồi dùng
các lệnh trên; xem [README.md](README.md) để tra cấu hình và Mailpit tùy chọn.

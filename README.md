# FPTPost — Group 6 Project

FPTPost là nền tảng giao hàng tiện đường P2P, kết nối người gửi với người giao,
hỗ trợ KYC, sàn đơn/chào giá, ký quỹ, theo dõi và thanh toán theo đặc tả của nhóm.
Repository hiện cung cấp base React/Spring Boot với Product CRUD mẫu, Activity Log mẫu
và UC-01–04 Identity. UC-46 cung cấp quản trị 109 tham số, lịch sử thay đổi, SQL outbox/Mongo audit
và mô phỏng biểu phí trong môi trường phát triển. Mô phỏng production cần Pricing provider thật.

[API quản trị tham số](docs/API.md#uc-46-đã-triển-khai--admin) • [Schema và outbox](docs/DATABASE.md).

UC-60 bổ sung `/admin/audit`: Admin lọc/phân trang, xem chi tiết before/after đã che dữ liệu nhạy cảm,
truy vết event/IP/correlationId; Mongo index và job retention theo AUDIT_KEEP.
[API tra cứu audit](docs/API.md#uc-60-đã-triển-khai--admin).

UC-45 bổ sung `/admin/promotions`: tạo/sửa/tạm dừng khuyến mãi, chọn người áp dụng,
theo dõi lượt dùng và audit. Luồng áp mã vào đơn do UC-44 tích hợp qua module pricing dùng chung.

Đọc [thông tin dự án](docs/PROJECT-INFO.md), [63 Use Case](docs/USE-CASES.md) và
[quy ước code](docs/CONTRIBUTING.md) trước khi bắt đầu chức năng.

## Mục lục

- [Công nghệ](#công-nghệ)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Khởi chạy](#khởi-chạy)
- [Giao diện dùng chung](#giao-diện-dùng-chung)
- [Cấu hình](#cấu-hình)
- [Database và seed data](#database-và-seed-data)
- [API](#api)
- [Quy ước code](#quy-ước-code)
- [Kiểm thử và build](#kiểm-thử-và-build)
- [Quy trình Git](#quy-trình-git)
- [Tài liệu](#tài-liệu)
- [Phạm vi hiện tại](#phạm-vi-hiện-tại)

## Công nghệ

Stack của dự án:

| Thành phần | Công nghệ |
| --- | --- |
| Frontend | React 18.3, TypeScript 5.9, Vite 5.4, React Router 6, Axios |
| Form | React Hook Form, Zod; tách component/hook/schema theo chức năng |
| Backend | Spring Boot 3.5.16, Java 21 LTS, Spring Web MVC |
| Database | Microsoft SQL Server 2022 và MongoDB 7 |
| ORM / ODM | Spring Data JPA + Hibernate; Spring Data MongoDB |
| DB driver | Microsoft JDBC Driver; MongoDB Java Driver |
| Quản lý schema | JPA/Hibernate code first; `ddl-auto: update` |
| Mapping và validation | MapStruct 1.6.3, Jakarta Bean Validation |
| Email | Spring Mail, template Thymeleaf; cấu hình nhóm dùng Gmail SMTP, Mailpit là profile tùy chọn |
| Xác thực | Spring Security, JWT HS256, refresh token qua cookie HttpOnly |
| Tài liệu API | OpenAPI, Swagger UI |
| Kiểm thử | JUnit Jupiter, Mockito, MockMvc, H2 |
| Container | Docker + Docker Compose |
| Frontend production | Nginx |
| Node.js | 21.7.x theo SPEC |

Kiến trúc base đang chạy (thiết kế FPTPost xem [tài liệu kiến trúc](docs/LOGISTICS-BASE.md)):

```text
React + TypeScript
  |
  | REST API
  v
Spring Boot (Controller -> Service -> Repository)
  |
  +-- Spring Data JPA / Hibernate --> SQL Server 2022 (Product, Identity, Parameters, Outbox)
  |
  +-- Spring Data MongoDB ---------> MongoDB 7 (Activity Log, Audit)
```

Nhật ký thao tác Product được ghi vào MongoDB sau khi transaction SQL commit.
Thay đổi tham số UC-46 ghi history/outbox trong cùng giao dịch SQL; worker chuyển audit sang MongoDB với retry và chống trùng eventId.
Schema SQL được tạo và cập nhật từ entity bằng Hibernate khi backend khởi động.

## Cấu trúc dự án

```text
SBA391_Group6_Project/
├── client/
│   ├── public/
│   ├── src/
│   │   ├── api/                       # Axios client và API theo module
│   │   ├── assets/
│   │   ├── components/
│   │   │   ├── common/
│   │   │   └── ui/
│   │   ├── features/
│   │   │   ├── auth/                 # Form, hook, schema đăng ký/đăng nhập
│   │   │   ├── product/              # CRUD mẫu dùng component chung
│   │   │   └── ui-kit/               # Mẫu form, danh sách và component cho phát triển
│   │   ├── i18n/                      # Câu chữ lỗi theo mã nghiệp vụ
│   │   ├── layouts/
│   │   ├── pages/
│   │   ├── routes/
│   │   ├── services/authService.ts    # Đăng ký, OTP, đăng nhập và quản lý phiên
│   │   ├── styles/
│   │   ├── types/                     # Kiểu dữ liệu API và nghiệp vụ
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── .env.example
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── server/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/fptpost/
│   │   │   │   ├── common/
│   │   │   │   │   ├── exception/
│   │   │   │   │   ├── mail/              # Service gửi mail và cấu hình dùng chung
│   │   │   │   │   ├── request/
│   │   │   │   │   └── response/
│   │   │   │   ├── config/            # CORS, OpenAPI
│   │   │   │   ├── modules/
│   │   │   │   │   ├── activity/
│   │   │   │   │   ├── health/
│   │   │   │   │   ├── identity/
│   │   │   │   │   │   ├── config/
│   │   │   │   │   │   ├── controller/
│   │   │   │   │   │   │   └── AuthenticationController.java
│   │   │   │   │   │   ├── dto/
│   │   │   │   │   │   │   ├── request/UserCreationRequest.java
│   │   │   │   │   │   │   ├── internal/AuthenticationResult.java
│   │   │   │   │   │   │   └── response/
│   │   │   │   │   │   │       ├── RegisterResponse.java
│   │   │   │   │   │   │       ├── RegistrationPoliciesResponse.java
│   │   │   │   │   │   │       └── PolicyResponse.java
│   │   │   │   │   │   ├── enums/
│   │   │   │   │   │   │   └── UserStatus.java
│   │   │   │   │   │   ├── exception/
│   │   │   │   │   │   │   ├── IdentityErrorCode.java
│   │   │   │   │   │   │   ├── IdentityException.java
│   │   │   │   │   │   │   └── IdentityExceptionHandler.java
│   │   │   │   │   │   ├── mapper/UserMapper.java
│   │   │   │   │   │   ├── model/                  # Entity JPA
│   │   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── security/               # Limiter cho request/đăng nhập
│   │   │   │   │   │   ├── util/ContactUtils.java
│   │   │   │   │   │   └── service/
│   │   │   │   │   │       ├── UserService.java
│   │   │   │   │   │       ├── AuthenticationService.java
│   │   │   │   │   │       ├── OtpService.java
│   │   │   │   │   │       ├── JwtTokenService.java
│   │   │   │   │   │       └── PolicyService.java
│   │   │   │   │   └── product/
│   │   │   │   │       ├── config/
│   │   │   │   │       │   └── ProductDemoSeeder.java
│   │   │   │   │       ├── controller/
│   │   │   │   │       │   └── ProductController.java
│   │   │   │   │       ├── dto/
│   │   │   │   │       │   ├── ProductRequest.java
│   │   │   │   │       │   ├── ProductResponse.java
│   │   │   │   │       │   └── ProductSearchRequest.java
│   │   │   │   │       ├── exception/
│   │   │   │   │       │   └── ProductErrorCode.java
│   │   │   │   │       ├── mapper/
│   │   │   │   │       │   └── ProductMapper.java
│   │   │   │   │       ├── model/
│   │   │   │   │       │   └── Product.java
│   │   │   │   │       ├── repository/
│   │   │   │   │       │   ├── ProductRepository.java
│   │   │   │   │       │   └── ProductSpecification.java
│   │   │   │   │       └── service/
│   │   │   │   │           └── ProductService.java
│   │   │   │   └── Application.java
│   │   │   └── resources/
│   │   │       ├── demo/products.json
│   │   │       ├── messages/errors.properties        # Title/detail và lỗi validation
│   │   │       ├── policies/
│   │   │       │   ├── terms.json
│   │   │       │   └── privacy.json
│   │   │       ├── templates/mail/                   # HTML, text, tiêu đề email
│   │   │       └── application.yml
│   │   └── test/java/com/fptpost/
│   ├── .env.example
│   ├── .dockerignore
│   ├── Dockerfile
│   └── pom.xml
├── docs/
│   ├── CONTRIBUTING.md
│   ├── PROJECT-INFO.md
│   ├── USE-CASES.md
│   ├── LOGISTICS-BASE.md
│   ├── DATABASE.md
│   ├── API.md
│   ├── API-ERRORS.md
│   ├── SBA301-CHECKLIST.md
│   └── sources/                     # Bản gốc BR/UC và SPEC v1.0
├── .env.example
├── .gitignore
├── compose.yaml
├── package.json
└── README.md
```

Backend tổ chức theo module. Module Product gồm `controller`, `service`, `repository`,
`model`, `dto`, `mapper`, `exception` và `config` cho seeder.
Controller nhận và validate request, service xử lý nghiệp vụ, repository truy cập database.
MapStruct ánh xạ giữa DTO và entity; API trả DTO.
Identity dùng DTO class Lombok chia `request/response`; đăng ký đi từ
`AuthenticationController` → `UserService` → `OtpService` → `EmailService`.
Mỗi DTO ở file cùng tên; `RegistrationPoliciesResponse` dùng `PolicyResponse` cho
`terms` và `privacy`. Entity nằm ở `model`, enum nghiệp vụ như `UserStatus`, `OtpType` nằm ở
`enums`; enum mã lỗi như `IdentityErrorCode` nằm ở `exception`.
`EmailService` nằm trong `common/mail`, dùng chung cho các module;
nội dung và giao diện email nằm trong template Thymeleaf.
Hướng dẫn tùy chỉnh nằm trong phần Email và template bên dưới.
Chi tiết đăng ký, OTP và đăng nhập xem [tài liệu API](docs/API.md).

## Khởi chạy

### Bằng Docker

Yêu cầu Docker Desktop với Linux containers.

Lần đầu, tạo file cấu hình ở thư mục gốc. Nếu đã có `.env`, giữ file đang dùng:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Điền các giá trị của nhóm vào `.env`: `SQLSERVER_PASSWORD`, `JWT_SECRET`,
`SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM`. Mẫu dùng Gmail SMTP
`smtp.gmail.com:587`, xác thực và STARTTLS; password SMTP là app password của Gmail.
Không ghi secrets thật vào `.env.example`. Nếu đã có `.env` của nhóm, dùng file đó.
Giữ nguyên giá trị `SMTP_PASSWORD` của nhóm, kể cả khoảng trắng nếu có.

Chạy toàn bộ stack:

```powershell
docker compose up --build -d
```

Compose mặc định chạy SQL Server 2022 riêng của dự án, MongoDB, backend và frontend.
Mailpit chỉ chạy khi bật profile `mailpit`. Service `sqlserver-init`
tạo database nếu chưa tồn tại; service này kết thúc với exit code 0 là bình thường.
Frontend được phục vụ qua Nginx và proxy `/api` tới backend.

| Thành phần | Địa chỉ mặc định |
| --- | --- |
| Frontend | http://localhost:15173 |
| Swagger UI | http://localhost:18080/swagger-ui/index.html |
| API health | http://localhost:18080/api/v1/health |
| Actuator health | http://localhost:18080/actuator/health |
| SQL Server riêng của dự án | localhost,31433 — database `group6_project_db`, user `sa` |
| MongoDB riêng của dự án | mongodb://localhost:37017/group6_project_db |
| Gmail SMTP | smtp.gmail.com:587, AUTH + STARTTLS |

Các cổng trên là cổng host từ `.env`. Trong mạng Docker, backend dùng
`sqlserver:1433`, `mongodb:27017`; HTTP nội bộ backend vẫn ở `8080`.
SQL Server Windows trên máy không được dùng hoặc thay đổi bởi stack này.

```powershell
docker compose ps -a
docker compose logs -f server
docker compose down
```

Khi sửa code, chạy lại `docker compose up --build -d`.
Dữ liệu nằm trong volumes `sqlserver-2022-data` và `mongo-data`, được giữ khi dừng stack.
Compose dùng volume SQL 2022 riêng, không gắn volume `sqlserver-data` của cấu hình cũ.
Volume cũ vẫn được giữ. Nếu chứa dữ liệu SQL 2025 cần chuyển, xuất/nhập dữ liệu sang
database SQL 2022; không chạy SQL 2022 trên các file database đã nâng lên SQL 2025.

Với volume SQL đã có dữ liệu, `SQLSERVER_PASSWORD` phải khớp mật khẩu `sa` đang
đặt trong instance đó. Đổi biến `MSSQL_SA_PASSWORD` không tự đổi password của
database volume cũ; cần đổi password SQL và đồng bộ `.env` khi chủ động cập nhật.

### Chạy trên máy để phát triển

Yêu cầu Node.js 21.7.x, JDK 21 và Maven 3.9.x. Launcher portable nằm trong mã nguồn tại
`scripts/dev.ps1`, ưu tiên toolchain trong `.tools` nếu có, hoặc dùng bản cài trên
PATH/`JAVA_HOME`. File `.env` gốc là nguồn cấu hình chung cho Docker, launcher và IDE.

Dừng backend/frontend Docker trước khi chạy IDE hoặc terminal cùng cổng, rồi chuẩn bị:

```powershell
docker compose stop server client
npm run install:all
npm run setup:sync
npm run setup:infra
```

`setup:sync` sinh lại `server/.env` và `client/.env` từ `.env` gốc, gồm JDBC
`localhost:31433`, Mongo `localhost:37017`, API `localhost:18080` và Vite `15173`.
Spring Boot ngoài Docker không tự đọc `.env`; launcher nạp env cho tiến trình,
còn IntelliJ đọc `server/.env` trong run configuration.

Chạy hai ứng dụng bằng terminal:

```powershell
npm run dev:local
```

Hoặc Run **FPTPost Dev** trong IntelliJ sau bước `setup:infra`. Backend dùng
main class `com.fptpost.Application`, module `sba391-server`, JDK 21, working
directory `server` và env file `server/.env`; Frontend chạy npm `dev` trong `client`.
Nếu mở dự án trên máy mới, tạo hai run configurations này hoặc dùng `dev:local`.

```powershell
npm run setup:check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev.ps1 -Mode Server
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev.ps1 -Mode Client
```

Launcher có các mode `Dev`, `Server`, `Client`, `Infra`, `Check`, `Docker`, `Sync`.
`Check` xem versions/status/health; `Docker` build và chạy stack; `Sync` chỉ đồng bộ
hai file env cho IDE. Dừng ứng dụng bằng Ctrl+C hoặc Stop trong IDE, rồi
`docker compose down` nếu muốn dừng hạ tầng; lệnh này giữ dữ liệu volumes.

## Giao diện dùng chung

Chạy `npm run dev:client` rồi mở `/ui-kit` trên frontend để xem mẫu tương tác:
form hai cột, danh sách/bộ lọc/phân trang và các component với tông cam dùng chung.
Trang mẫu chỉ có khi chạy
Vite development; bản build production và Docker/Nginx không chứa route này.
Form giao hàng và đơn hàng tại đây là dữ liệu minh họa, không tạo đơn hoặc gọi API nghiệp vụ.

| Thành phần | Nơi dùng |
| --- | --- |
| `styles/tokens.css` | Màu, font, khoảng cách, chiều cao input và bo góc dùng chung |
| `components/ui` | Button, Input, Select, Textarea, Checkbox, FormSection, Modal, Badge, Alert, DataTable, Pagination |
| `PageHeader`, `SectionCard`, `EmptyState` | Tiêu đề trang, nhóm nội dung và trạng thái rỗng |
| `MainLayout` | Trang dùng thanh điều hướng phía trên |
| `AuthLayout` | Đăng nhập/đăng ký |
| `WorkspaceLayout` | Màn hình làm việc có sidebar; nhận danh sách menu và nội dung qua props |

Form đăng ký/đăng nhập/OTP và Product CRUD đã dùng bộ component này. Tham khảo
`features/product/components/ProductForm.tsx`, `features/product/hooks/useProducts.ts`
và `features/product/schemas/productSchema.ts` khi tạo module mới. Các component input
nhận `ref` từ React Hook Form, tự nối label/hint/error và focus trường lỗi.
Quy ước chi tiết cùng ví dụ nằm trong [hướng dẫn frontend](docs/CONTRIBUTING.md#8-frontend).

## Cấu hình

Cấu hình backend nằm trong `server/src/main/resources/application.yml`.
Compose và `scripts/dev.ps1` đọc `.env` ở thư mục gốc. Chỉnh file này rồi chạy
`npm run setup:sync` trước khi restart ứng dụng trong IDE. `server/.env` và
`client/.env` là bản sinh lại; Spring Boot tự chạy ngoài launcher/IDE không tự nạp chúng.

| Biến | Nơi sử dụng | Ý nghĩa |
| --- | --- | --- |
| `FRONTEND_PORT` | Compose/launcher | Cổng host Frontend, mẫu nhóm dùng 15173 |
| `BACKEND_PORT` | Compose/launcher | Cổng host Backend, mẫu nhóm dùng 18080 |
| `SQLSERVER_PORT` | Compose/launcher | Cổng host SQL riêng của dự án, 31433 |
| `MONGO_PORT` | Compose/launcher | Cổng host MongoDB riêng của dự án, 37017 |
| `MAILPIT_PORT` | Profile Mailpit | Cổng hộp thư tùy chọn, 8025 |
| `SMTP_HOST`, `SMTP_PORT` | Compose/backend | Mẫu nhóm dùng `smtp.gmail.com`, `587` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Compose/backend | Email Gmail và app password; chỉ lưu trong `.env` thật |
| `SMTP_AUTH`, `SMTP_STARTTLS` | Compose/backend | Mẫu nhóm dùng `true`, `true` cho Gmail |
| `MAILPIT_SMTP_PORT` | Compose | Cổng SMTP Mailpit local, mặc định 1025; độc lập với cổng SMTP thật |
| `MAIL_FROM`, `MAIL_FROM_NAME` | Compose/backend | Địa chỉ và tên người gửi email |
| `MAIL_TEMPLATE_CACHE` | Compose/backend | Cache template email, local mặc định false |
| `TERMS_VERSION`, `PRIVACY_VERSION` | Backend | Phiên bản điều khoản/chính sách dùng khi đăng ký và lưu consent |
| `BACKEND_PROFILES` | Compose/launcher | Mẫu nhóm dùng `dev,demo`, nạp Product demo còn thiếu |
| `JWT_SECRET` | Compose/backend | Khóa JWT Base64 chứa tối thiểu 32 byte ngẫu nhiên; giữ riêng trong `.env` |
| `AUTH_COOKIE_SECURE` | Compose/backend | `false` cho localhost HTTP; `true` khi chạy production qua HTTPS |
| `SQLSERVER_PASSWORD` | Compose/backend | Password SQL user `sa`, khác password đăng nhập website |
| `SQLSERVER_URL`, `SQLSERVER_USERNAME` | Backend | IDE/local dùng `localhost:31433`, user `sa`; container dùng `sqlserver:1433` |
| `MONGODB_URI` | Backend | IDE/local dùng `localhost:37017`; container dùng `mongodb:27017` |
| `SERVER_PORT` | Backend | Local sinh từ `BACKEND_PORT` (18080); container nội bộ 8080 |
| `SPRING_PROFILES_ACTIVE` | Backend | Local sinh từ `BACKEND_PROFILES` (`dev,demo`) |
| `ALLOWED_ORIGINS` | Backend | Danh sách origin được phép |
| `VITE_API_URL` | Frontend | URL API; Docker build dùng `/api/v1` |
| `VITE_DEV_PORT` | Vite | Local sinh từ `FRONTEND_PORT` (15173) |

Các file `.env.example` là mẫu cấu hình. File `.env` chứa cấu hình local và không được commit.
Password SQL, SMTP và JWT nằm trong `.env` gốc, được đồng bộ sang `server/.env`;
`client/.env` chỉ chứa URL/port frontend.

Nội dung điều khoản và chính sách dữ liệu nằm trong
[terms.json](server/src/main/resources/policies/terms.json) và
[privacy.json](server/src/main/resources/policies/privacy.json).
Service đọc file một lần khi khởi động, React nhận nội dung qua API.
Khi chỉnh nội dung, đổi phiên bản tương ứng trong cấu hình triển khai và build/restart backend.
Câu chữ lỗi backend nằm trong [errors.properties](server/src/main/resources/messages/errors.properties);
enum mã lỗi quản lý code/HTTP status, resource quản lý title/detail và thông báo validation.

### Email và template

Mẫu nhóm dùng Gmail SMTP tại `smtp.gmail.com:587`, `SMTP_AUTH=true`,
`SMTP_STARTTLS=true`; điền Gmail/app password và `MAIL_FROM` trong `.env` thật.
Sau khi đổi SMTP, recreate Backend Docker hoặc chạy `setup:sync` rồi restart Backend local.

Mailpit là lựa chọn gửi email vào hộp thư local, không phát ra hộp thư thật:

```powershell
docker compose --profile mailpit up -d mailpit
```

Đọc Mailpit tại `http://localhost:8025`. Nếu dùng SMTP Mailpit, đặt `SMTP_HOST=mailpit`
cho Backend container hoặc `SMTP_HOST=localhost` cho Backend local, port `1025`,
`SMTP_AUTH=false`, `SMTP_STARTTLS=false` và bỏ credentials Gmail trước khi restart.
Kiểm tra health SMTP chỉ kết nối/xác thực; việc đăng ký qua Gmail sẽ gửi email OTP thật.

Các module dùng chung `common/mail/service/EmailService`, truyền người nhận,
`EmailTemplateType` và các biến qua `EmailRequest`. Service render template và gửi SMTP;
module nghiệp vụ quản lý OTP và xử lý lỗi gửi.

Template nằm trong `server/src/main/resources/templates/mail`: giao diện ở
`html/verify-email.html`, header/footer ở `html/fragments.html`, bản text ở
`text/verify-email.txt`, tiêu đề ở `subject/verify-email.txt`.
Sửa template rồi build/restart backend. Thêm loại email bằng ba file HTML/text/subject
cùng tên và khai báo trong `EmailTemplateType`; dùng `th:text` để escape dữ liệu người dùng.
Không commit mật khẩu SMTP hoặc log OTP/nội dung email.

## Database và seed data

| Dữ liệu | Database |
| --- | --- |
| Product | SQL Server, bảng `products` |
| Tài khoản, role, hồ sơ, consent, OTP và blacklist | SQL Server, các bảng Identity |
| Nhật ký thao tác Product | MongoDB, collection `activity_logs` |

Hibernate dùng `ddl-auto: update`: tạo bảng/cột còn thiếu từ entity khi backend khởi động.
Thành viên sửa entity trong module mình, pull code mới rồi chạy lại backend;
không cần đăng ký số phiên bản V1/V2. Khóa ngoại, unique constraint, check và index
được khai báo trong entity. `IdentityDataInitializer` thêm các role nền còn thiếu.
Xem [quản lý schema code first](docs/DATABASE.md) cho thay đổi dữ liệu cũ.

Dữ liệu mẫu nằm trong `server/src/main/resources/demo/products.json`.
`ProductDemoSeeder` chỉ thêm các UUID mẫu còn thiếu, không ghi đè dữ liệu đã sửa.
Seeder chạy khi có `demo` và không có `prod`; mẫu cấu hình nhóm `dev,demo` nạp mẫu,
còn profile `dev` riêng không nạp.

- Docker: đặt `BACKEND_PROFILES=dev,demo` trong `.env`, rồi chạy lại Compose.
- Chạy trên máy: đặt `SPRING_PROFILES_ACTIVE=dev,demo` trước khi chạy backend.

Hibernate tạo/cập nhật schema trước khi initializer và seeder chạy.
Mỗi module bổ sung seeder riêng khi cần.

Theo thiết kế FPTPost, SQL lưu tài khoản, đơn, ví/sổ cái/ký quỹ; Mongo lưu vị trí,
chat, thông báo, audit. Xem [từ điển dữ liệu](docs/DATABASE.md) và chiến lược outbox
cần triển khai; Product/Activity hiện chỉ là ví dụ kỹ thuật.

Nhật ký MongoDB được ghi sau khi transaction SQL commit. Cơ chế hiện tại không có retry/outbox,
nên có thể thiếu nhật ký nếu MongoDB lỗi. Chi tiết nằm trong [quy ước backend](docs/CONTRIBUTING.md).

## API

Xem [tài liệu API](docs/API.md) để phân biệt 7 endpoint base, 9 endpoint Identity đã có
và 167 mục API trong thiết kế FPTPost. Phần bên dưới mô tả **API đang chạy**;
các khác biệt còn lại về response thành công/phân trang được ghi trong
[tài liệu API](docs/API.md).

### Endpoint

| Method | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET | `/api/v1/health` | Kiểm tra ứng dụng |
| GET | `/api/v1/products` | Tìm kiếm và phân trang sản phẩm |
| GET | `/api/v1/products/{id}` | Chi tiết sản phẩm |
| POST | `/api/v1/products` | Tạo sản phẩm |
| PUT | `/api/v1/products/{id}` | Cập nhật sản phẩm |
| DELETE | `/api/v1/products/{id}` | Xóa sản phẩm |
| GET | `/api/v1/activity-logs` | Phân trang nhật ký, lọc theo `entityId` |
| GET | `/api/v1/auth/registration-policies` | Điều khoản/chính sách và phiên bản hiện hành |
| POST | `/api/v1/auth/register` | Tạo tài khoản chờ xác thực và gửi OTP email |
| POST | `/api/v1/auth/otp/verify` | Xác thực OTP email và kích hoạt tài khoản |
| POST | `/api/v1/auth/otp/resend` | Gửi lại OTP sau thời gian chờ |
| GET | `/api/v1/auth/csrf` | CSRF token cho login/refresh/logout |
| POST | `/api/v1/auth/login` | Đăng nhập bằng email hoặc SĐT |
| POST | `/api/v1/auth/refresh` | Làm mới JWT và xoay vòng refresh cookie |
| POST | `/api/v1/auth/logout` | Đăng xuất phiên hiện tại |
| GET | `/api/v1/auth/sessions` | Xem các phiên đang hoạt động của tài khoản |
| DELETE | `/api/v1/auth/sessions/{id}` | Đăng xuất một phiên/thiết bị của tài khoản |
| GET | `/api/v1/me` | Thông tin người dùng đang đăng nhập |

Product ID và `entityId` của nhật ký dùng UUID.
Xem request/response chi tiết trong Swagger UI.

### Response và phân trang

Response thành công dùng `ApiResponse<T>`. Ví dụ kết quả tìm kiếm rỗng:

```json
{
  "success": true,
  "status": 200,
  "code": 1000,
  "message": "Products retrieved successfully",
  "data": {
    "content": [],
    "page": 0,
    "size": 12,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  },
  "timestamp": "2026-10-06T00:00:00Z"
}
```

Response thành công giữ `ApiResponse<T>` với `code: 1000`.
Lỗi dùng Problem Details, Content-Type `application/problem+json`: `type`, `title`,
`status`, `code` dạng chữ, `detail`, `correlationId` và `errors` dạng danh sách khi có validation.
Ví dụ: `AUTH_EMAIL_ALREADY_EXISTS`, `PRODUCT_NOT_FOUND`, `SYS_INVALID_REQUEST`.
Header `X-Correlation-Id` xuất hiện ở cả thành công/lỗi, có trong log để tra cứu.
Backend nhận UUID hợp lệ hoặc sinh mới; frontend gửi UUID, ánh xạ mã lỗi và cho sao chép mã theo dõi.
Chi tiết xem [contract lỗi](docs/API-ERRORS.md) và [API xác thực, JWT/phiên đăng nhập](docs/API.md).
Product/Activity yêu cầu đăng nhập; endpoint nội bộ mặc định cần Bearer.

Product hỗ trợ `search`, `category`, `status`, `minPrice`, `maxPrice`,
`page`, `size`, `sortBy` và `direction`:

```text
/api/v1/products?search=Mac&category=Electronics&page=0&size=12&sortBy=price&direction=ASC
```

Page bắt đầu từ 0, size từ 1 đến 100. Các bộ lọc kết hợp AND.
Sort cho phép `name`, `price`, `stock`, `createdAt`, `updatedAt`;
direction là `ASC` hoặc `DESC`. Danh sách luôn nằm trong `data.content`.

## Quy ước code

| Nội dung | Quy ước của nhóm |
| --- | --- |
| Tổ chức backend | Package `com.fptpost.modules/<module>`; controller → service → repository |
| Package theo tầng | `controller`, `service`, `repository`, `model`, `mapper`; enum nghiệp vụ ở `enums`, mã lỗi ở `exception` |
| Đặt tên | Class/component `PascalCase`, biến/hàm `camelCase`, constant `UPPER_SNAKE_CASE` |
| Service | Một class `@Service` nếu chỉ có một implementation; constructor injection |
| Entity JPA | `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`; initializer dùng `@Builder.Default` |
| DTO và mapping | Mỗi DTO một file; class Lombok trong `dto/request`, `dto/response`; request có validation, dùng MapStruct |
| API | `/api/v1/<resources>`; thành công dùng `ApiResponse`, lỗi dùng Problem Details; danh sách dùng `PageResponse.content` |
| Xử lý lỗi | `AppException` + enum mã lỗi của module; handler chung xử lý response |
| Database | SQL dùng JPA/Specification; Mongo dùng MongoRepository; nghiệp vụ VND theo SPEC dùng số nguyên `long`/`BIGINT`, Product demo hiện dùng `BigDecimal` |
| Frontend | API đặt trong `src/api`, dùng `axiosClient`; component chung ở `components` |
| Format | Java thụt 4 spaces, TypeScript/TSX thụt 2 spaces; format trước khi commit |
| Seed và test | Seed riêng theo module; kiểm thử nghiệp vụ, validation và query quan trọng |

Không đặt nghiệp vụ trong controller, không trả trực tiếp entity/document,
không bắt lỗi database rồi trả thành công. Frontend xử lý loading/error/data
và đọc mã nghiệp vụ thay vì so sánh message.

Xem ví dụ đặt tên, trách nhiệm từng tầng và checklist trong
[docs/CONTRIBUTING.md](docs/CONTRIBUTING.md).

## Kiểm thử và build

Chạy từ thư mục gốc:

```powershell
npm run test:server
npm run typecheck --prefix client
npm run lint --prefix client
npm run build:client
npm run build:server
```

Backend có test mapping, service, validation/controller, query/phân trang,
seed và transaction. Test JPA dùng H2 chế độ MSSQL; cần kiểm tra SQL Server/MongoDB
thật bằng Docker khi sửa phần liên quan database.

`build:server` hiện bỏ qua test; chạy `test:server` trước khi build.
Build frontend chạy TypeScript trước khi Vite đóng gói.
Dockerfile backend chạy test khi đóng gói; Dockerfile frontend chạy lint và build.

## Quy trình Git

| Nhánh | Mục đích |
| --- | --- |
| `main` | Phiên bản ổn định |
| `develop` | Tích hợp các chức năng của nhóm |
| `feature/<ma-uc>-<ma-tv>-<mo-ta>` | Phát triển chức năng gắn với mã Use Case |
| `fix/<ma-uc>-<ma-tv>-<mo-ta>` | Sửa lỗi của chức năng có UC |
| `docs/<ma-tv>-<mo-ta>` | Cập nhật tài liệu không thuộc UC |
| `chore/<ma-tv>-<mo-ta>` | Base dùng chung, cấu hình, hạ tầng hoặc bảo trì không thuộc UC |

Nhóm bổ sung mã thành viên vào cách đặt nhánh trong SPEC để nhận biết người phụ trách.
Mã thành viên dùng tên viết tắt cố định, không trùng nhau; ví dụ Lư Hữu Luân dùng `LuanLH`.
Giữ chữ hoa/thường của mã thành viên. Mã UC giữ đúng `UC-xx` từ sheet;
phần mô tả dùng chữ thường, không dấu, ngăn cách bằng gạch nối.
Ví dụ: `feature/UC-03-LuanLH-dang-nhap`, `fix/UC-02-LuanLH-otp-het-han`.
Xem mã UC trong [danh mục UC](docs/USE-CASES.md), không tự đặt mã mới.
Công việc chung không thuộc UC bỏ phần mã UC: `chore/LuanLH-frontend-ui-base`,
`docs/LuanLH-quy-uoc-git`.

Bắt đầu chức năng từ nhánh `develop`:

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c feature/UC-03-LuanLH-dang-nhap
```

Thay mã UC, mã thành viên và mô tả trong ví dụ theo công việc được phân công.
Sau khi commit và push nhánh feature, mở Pull Request vào `develop` để review và tích hợp.
Tiêu đề PR chức năng ghi UC và mã thành viên: `[UC-03] [LuanLH] Đăng nhập`.
PR công việc chung dùng `[LuanLH] Giao diện dùng chung`.
Commit theo [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/),
nhóm thêm UC và mã thành viên vào mô tả:

```text
feat(identity): [UC-03] [LuanLH] add login form
fix(identity): [UC-02] [LuanLH] reject expired OTP
feat(ui): [LuanLH] add UI kit, homepage and footer
docs(git): [LuanLH] define branch and commit conventions
```

Chi tiết loại commit và scope trong [quy ước đóng góp](docs/CONTRIBUTING.md#9-kiểm-thử-cấu-hình-và-đóng-góp).
Chỉ đưa phiên bản ổn định từ `develop` vào `main`.

## Tài liệu

UC-01–02 có tại `/register` → `/verify`: đăng ký, nhập OTP, gửi lại mã và kích hoạt tài khoản.
Cấu hình nhóm dùng Gmail SMTP; Mailpit là profile tùy chọn để đọc email local tại
`http://localhost:8025`. UC-03 có tại `/login` → `/account`, hỗ trợ email/SĐT, JWT và refresh cookie.
Xem [tài liệu API](docs/API.md) để tra contract và giới hạn xác thực,
[tài liệu database](docs/DATABASE.md) để tra schema; cấu hình SMTP nằm trong README này.

- [Quy ước code backend/frontend và hướng dẫn đóng góp](docs/CONTRIBUTING.md)
- [Thông tin dự án, phạm vi và tác nhân](docs/PROJECT-INFO.md)
- [Danh mục UC, phân công và quy ước mã nhánh](docs/USE-CASES.md)
- [Kiến trúc và ranh giới module FPTPost](docs/LOGISTICS-BASE.md)
- [Database và từ điển dữ liệu dự kiến](docs/DATABASE.md)
- [API hiện tại và danh mục thiết kế](docs/API.md)
- [Contract lỗi và quy ước mã nghiệp vụ](docs/API-ERRORS.md)
- [Tài liệu nguồn BR/UC và SPEC v1.0](docs/sources/README.md)
- [Checklist tham chiếu đề SBA301](docs/SBA301-CHECKLIST.md)

Checklist đề gốc cần đối chiếu với các điều chỉnh của giảng viên khi áp dụng cho nhóm.

## Phạm vi hiện tại

Base có Product CRUD, paging/filter/sort, MapStruct, validation, mã lỗi,
Swagger, seed demo, nhật ký MongoDB và Docker Compose.
Identity có UC-01–04: đăng ký, chính sách/consent, OTP email, đăng nhập JWT/refresh rotation, đăng xuất và quản lý phiên;
service gửi mail/template dùng chung. Lỗi API dùng Problem Details và correlation ID.
Frontend có trang chủ, Product, đăng ký, xác thực OTP, đăng nhập, tài khoản, điều khoản/chính sách và trang 404.
Trang chủ `/` theo SCR-01: giới thiệu FPost, cách hoạt động, ví dụ phí giao và đăng ký.
Phí trên trang chủ là các tình huống tham khảo từ biểu phí dự thảo, chưa gọi API ước tính giá.
Footer dùng chung có liên kết nhanh, chính sách và thông tin liên hệ. Trang `/legal` tải cùng nội dung chính sách với luồng đăng ký.

UC-46 có `/admin/params`: 109 tham số, sửa đơn/batch, version/history, preview và SQL outbox/Mongo audit.
Preview dùng Pricing adapter REFERENCE trong dev/demo; chưa tích hợp Pricing/Order/Identity consumers thật.

Các phần Identity còn lại (quên/đổi mật khẩu, hồ sơ/KYC) và các module
order, market, pricing, ledger/payment, tracking, reputation,
dispute, messaging và các UC admin còn lại cần triển khai theo BR/UC. UC-60 query/UI và retention đã có;
producer audit cho KYC/Order/Ledger/phân quyền tiếp tục tích hợp tại các UC tương ứng.
COD, bưu cục/kho và AI/eKYC
tự động nằm ngoài phạm vi v1.0. Demo thanh toán theo tài liệu dùng sandbox/tiền mô phỏng.
Tài liệu là dự thảo, không phải chứng nhận các UC đã hoàn thành.

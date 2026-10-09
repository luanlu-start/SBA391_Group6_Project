# FPTPost — Group 6 Project

FPTPost là nền tảng giao hàng tiện đường P2P, kết nối người gửi với người giao,
hỗ trợ KYC, sàn đơn/chào giá, ký quỹ, theo dõi và thanh toán theo đặc tả của nhóm.
Repository hiện cung cấp base React/Spring Boot: Product CRUD mẫu và Activity Log mẫu;
các module nghiệp vụ FPTPost chưa triển khai.

Đọc [thông tin dự án](docs/PROJECT-INFO.md), [63 Use Case](docs/USE-CASES.md) và
[đối chiếu SPEC với base](docs/SPEC-ALIGNMENT.md) trước khi bắt đầu chức năng.

## Mục lục

- [Công nghệ](#công-nghệ)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Khởi chạy](#khởi-chạy)
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
| Frontend | React 19.3, TypeScript 6, Vite 8, React Router 7, Axios |
| Backend | Spring Boot 4.1.1, Java 25 LTS, Spring Web MVC |
| Database | Microsoft SQL Server 2025 và MongoDB 7 |
| ORM / ODM | Spring Data JPA + Hibernate; Spring Data MongoDB |
| DB driver | Microsoft JDBC Driver; MongoDB Java Driver |
| Quản lý schema | Code first, Hibernate `ddl-auto: update` |
| Mapping và validation | MapStruct 1.6.3, Jakarta Bean Validation |
| Tài liệu API | OpenAPI, Swagger UI |
| Kiểm thử | JUnit Jupiter, Mockito, MockMvc, H2 |
| Container | Docker + Docker Compose |
| Frontend production | Nginx |
| Node.js | 24 LTS |

Kiến trúc base đang chạy (thiết kế FPTPost xem [tài liệu kiến trúc](docs/LOGISTICS-BASE.md)):

```text
React + TypeScript
  |
  | REST API
  v
Spring Boot (Controller -> Service -> Repository)
  |
  +-- Spring Data JPA / Hibernate --> SQL Server 2025 (Product)
  |
  +-- Spring Data MongoDB ---------> MongoDB 7 (Activity Log)
```

Nhật ký hoạt động được ghi vào MongoDB sau khi transaction SQL commit.
Schema SQL vẫn được quản lý bằng entity và Hibernate; dự án chưa dùng Flyway.

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
│   │   ├── layouts/
│   │   ├── pages/
│   │   ├── routes/
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
│   │   │   ├── java/com/group6/project/
│   │   │   │   ├── common/
│   │   │   │   │   ├── exception/
│   │   │   │   │   ├── request/
│   │   │   │   │   └── response/
│   │   │   │   ├── config/            # CORS, OpenAPI
│   │   │   │   ├── modules/
│   │   │   │   │   ├── activity/
│   │   │   │   │   ├── health/
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
│   │   │       └── application.yml
│   │   └── test/java/com/group6/project/
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
│   ├── SPEC-ALIGNMENT.md
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

## Khởi chạy

### Bằng Docker

Yêu cầu Docker Desktop với Linux containers.

Lần đầu, tạo file cấu hình ở thư mục gốc. Nếu đã có `.env`, giữ file đang dùng:

```powershell
Copy-Item .env.example .env
```

Điều chỉnh mật khẩu SQL Server và cổng trong `.env`, sau đó chạy:

```powershell
docker compose up --build -d
```

Compose chạy SQL Server, MongoDB, backend và frontend. Service `sqlserver-init`
tạo database nếu chưa tồn tại; service này kết thúc với exit code 0 là bình thường.
Frontend được phục vụ qua Nginx và proxy `/api` tới backend.

| Thành phần | Địa chỉ mặc định |
| --- | --- |
| Frontend | http://localhost:5173 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| API health | http://localhost:8080/api/v1/health |
| Actuator health | http://localhost:8080/actuator/health |
| SQL Server | localhost,1433 — database `group6_project_db` |
| MongoDB | mongodb://localhost:27017/group6_project_db |

Địa chỉ sử dụng cổng đã đặt trong `.env` nếu khác mặc định.

```powershell
docker compose ps -a
docker compose logs -f server
docker compose down
```

Khi sửa code, chạy lại `docker compose up --build -d`.
Dữ liệu nằm trong volumes `sqlserver-data` và `mongo-data`, được giữ khi dừng stack.
Nếu đã có dữ liệu từ SQL Server 2022, sao lưu trước khi chạy container SQL Server 2025.

### Chạy trên máy để phát triển

Yêu cầu Node.js 24, JDK 25 và Maven trên PATH; đặt `JAVA_HOME` trỏ tới JDK 25.
Từ thư mục gốc, cài dependencies và khởi động hai database:

```powershell
npm run install:all
npm run docker:db
```

Đặt các biến backend theo `server/.env.example` trong terminal hoặc IDE.
Ví dụ với cổng mặc định:

```powershell
$env:SQLSERVER_URL = 'jdbc:sqlserver://localhost:1433;databaseName=group6_project_db;encrypt=true;trustServerCertificate=true'
$env:SQLSERVER_USERNAME = 'sa'
$env:SQLSERVER_PASSWORD = '<mat-khau-trong-file-env>'
$env:MONGODB_URI = 'mongodb://localhost:27017/group6_project_db'
npm run dev
```

Nếu đổi cổng database trong `.env`, sửa URL trên theo cổng đó.
`npm run dev` chạy đồng thời frontend và backend; có thể chạy riêng bằng
`npm run dev:client` và `npm run dev:server`.

## Cấu hình

Cấu hình backend nằm trong `server/src/main/resources/application.yml`.
Compose tự đọc `.env` ở thư mục gốc; Spring Boot chạy ngoài Docker không tự đọc file `.env`.

| Biến | Nơi sử dụng | Ý nghĩa |
| --- | --- | --- |
| `FRONTEND_PORT` | Compose | Cổng frontend, mặc định 5173 |
| `BACKEND_PORT` | Compose | Cổng backend, mặc định 8080 |
| `SQLSERVER_PORT` | Compose | Cổng SQL Server, mặc định 1433 |
| `MONGO_PORT` | Compose | Cổng MongoDB, mặc định 27017 |
| `BACKEND_PROFILES` | Compose | Profile backend, mặc định `dev` |
| `SQLSERVER_PASSWORD` | Compose/backend | Mật khẩu SQL Server |
| `SQLSERVER_URL`, `SQLSERVER_USERNAME` | Backend | Kết nối SQL Server |
| `MONGODB_URI` | Backend | Kết nối MongoDB |
| `SERVER_PORT` | Backend | Cổng HTTP, mặc định 8080 |
| `SPRING_PROFILES_ACTIVE` | Backend | Profile, mặc định `dev` |
| `ALLOWED_ORIGINS` | Backend | Danh sách origin được phép |
| `VITE_API_URL` | Frontend | URL API; Docker build dùng `/api/v1` |

Các file `.env.example` là mẫu cấu hình. File `.env` chứa cấu hình local và không được commit.

## Database và seed data

| Dữ liệu | Database |
| --- | --- |
| Product | SQL Server, bảng `products` |
| Nhật ký thao tác Product | MongoDB, collection `activity_logs` |

Backend dùng **code first** với `spring.jpa.hibernate.ddl-auto: update`.
Sửa entity và khởi động lại để Hibernate tạo/cập nhật schema. UUID, giới hạn cột,
index và check constraint được khai báo trên entity. Base chưa dùng Flyway.

Khi cần tạo lại bảng, đổi `ddl-auto` thành `create`; dữ liệu trong các bảng JPA sẽ bị xóa.
Đổi tên/xóa cột hoặc chuyển đổi dữ liệu phức tạp cần xử lý riêng.

Dữ liệu mẫu nằm trong `server/src/main/resources/demo/products.json`.
`ProductDemoSeeder` chỉ thêm các UUID mẫu còn thiếu, không ghi đè dữ liệu đã sửa.
Seeder chạy khi có `demo` và không có `prod`; mặc định không nạp mẫu.

- Docker: đặt `BACKEND_PROFILES=dev,demo` trong `.env`, rồi chạy lại Compose.
- Chạy trên máy: đặt `SPRING_PROFILES_ACTIVE=dev,demo` trước khi chạy backend.

Hibernate tạo schema trước khi seeder chạy. `ddl-auto` không tự nạp dữ liệu mẫu.
Mỗi module bổ sung seeder riêng khi cần.

Theo thiết kế FPTPost, SQL lưu tài khoản, đơn, ví/sổ cái/ký quỹ; Mongo lưu vị trí,
chat, thông báo, audit. Xem [từ điển dữ liệu](docs/DATABASE.md) và chiến lược outbox
cần triển khai; Product/Activity hiện chỉ là ví dụ kỹ thuật.

Nhật ký MongoDB được ghi sau khi transaction SQL commit. Cơ chế hiện tại không có retry/outbox,
nên có thể thiếu nhật ký nếu MongoDB lỗi. Chi tiết nằm trong [quy ước backend](docs/CONTRIBUTING.md).

## API

Xem [tài liệu API](docs/API.md) để phân biệt 7 endpoint base đã có và 167 mục API
trong thiết kế FPTPost. Contract response của SPEC khác base và cần thống nhất
trước khi triển khai nghiệp vụ; phần bên dưới mô tả **API đang chạy**.

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

Product ID và `entityId` của nhật ký dùng UUID.
Xem request/response chi tiết trong Swagger UI.

### Response và phân trang

Các API module sử dụng `ApiResponse<T>`. Ví dụ kết quả tìm kiếm rỗng:

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

`status` là HTTP status; `code` là mã nghiệp vụ.
Lỗi validation có thêm `errors`; các trường null không xuất hiện trong JSON.
Ví dụ: `1000` thành công, `1002` request không hợp lệ, `2001` Product không tồn tại,
`9999` lỗi nội bộ. Các mã authentication/authorization đã dự trù, JWT chưa triển khai.

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
| Tổ chức backend | Theo `modules/<module>`; controller → service → repository |
| Đặt tên | Class/component `PascalCase`, biến/hàm `camelCase`, constant `UPPER_SNAKE_CASE` |
| Service | Một class `@Service` nếu chỉ có một implementation; constructor injection |
| DTO và mapping | Request có validation, response tách khỏi entity; dùng MapStruct |
| API | `/api/v1/<resources>`; trả `ApiResponse`, danh sách dùng `PageResponse.content` |
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
| `feature/<ma-uc>-<ten-chuc-nang>` | Phát triển chức năng gắn với mã Use Case |
| `docs/<mo-ta>` | Cập nhật tài liệu không thuộc UC |
| `chore/<mo-ta>` | Cấu hình, hạ tầng hoặc bảo trì không thuộc UC |

Tên nhánh dùng chữ thường, phần mô tả bằng tiếng Anh và ngăn cách bằng dấu gạch nối.
Nhánh chức năng phải có mã UC lấy từ sheet Use Case của nhóm, viết thường trong tên nhánh.
Theo sheet v1.0: UC-03 đăng nhập dùng `feature/uc-03-login`, UC-12 tạo đơn giao hàng
dùng `feature/uc-12-create-order`. Xem mã, phân công và sprint trong
[danh mục UC](docs/USE-CASES.md). Giữ dấu gạch nối và hai chữ số của mã `UC-xx`;
không tự đổi số thứ tự hoặc đặt mã mới.
Tài liệu hoặc hạ tầng không thuộc UC dùng nhánh `docs/` hoặc `chore/`, không gán mã UC giả.

Bắt đầu chức năng từ nhánh `develop`:

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c feature/uc-12-create-order
```

Thay mã UC và tên chức năng trong ví dụ bằng UC được phân công.
Sau khi commit và push nhánh feature, mở Pull Request vào `develop` để review và tích hợp.
Tiêu đề PR chức năng ghi mã UC, ví dụ `[UC-12] Create order`, để đối chiếu với sheet.
Chỉ đưa phiên bản ổn định từ `develop` vào `main`.

## Tài liệu

- [Quy ước code backend/frontend và hướng dẫn đóng góp](docs/CONTRIBUTING.md)
- [Thông tin dự án, phạm vi và tác nhân](docs/PROJECT-INFO.md)
- [Danh mục UC, phân công và quy ước mã nhánh](docs/USE-CASES.md)
- [Kiến trúc và ranh giới module FPTPost](docs/LOGISTICS-BASE.md)
- [Database và từ điển dữ liệu dự kiến](docs/DATABASE.md)
- [API hiện tại và danh mục thiết kế](docs/API.md)
- [Lựa chọn đã chốt, khác biệt với SPEC và câu hỏi còn mở](docs/SPEC-ALIGNMENT.md)
- [Tài liệu nguồn BR/UC và SPEC v1.0](docs/sources/README.md)
- [Checklist tham chiếu đề SBA301](docs/SBA301-CHECKLIST.md)

Checklist đề gốc cần đối chiếu với các điều chỉnh của giảng viên khi áp dụng cho nhóm.

## Phạm vi hiện tại

Base có Product CRUD, paging/filter/sort, MapStruct, validation, mã lỗi,
Swagger, seed demo, nhật ký MongoDB và Docker Compose.
Frontend có trang chủ, trang quản lý Product và trang 404.

Các module identity/KYC, order, market, pricing, ledger/payment, tracking, reputation,
dispute, messaging và admin/audit cần triển khai theo BR/UC. COD, bưu cục/kho và AI/eKYC
tự động nằm ngoài phạm vi v1.0. Demo thanh toán theo tài liệu dùng sandbox/tiền mô phỏng.
Tài liệu là dự thảo, không phải chứng nhận các UC đã hoàn thành.

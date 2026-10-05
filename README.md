# SBA391 - Group 6 Enterprise Full-Stack Platform

> **Kiến trúc Monolithic Chuẩn Doanh nghiệp** kết hợp **React 18 Single Page Application (Vite)** và **Java 21 Spring Boot 3 Backend** với Spring Data MongoDB (ODM / Repository Pattern) & OpenAPI 3 (Swagger UI).

---

## 🏛️ Kiến Trúc Hệ Thống (3-Tier Layered Architecture)

Dự án được cấu trúc theo mô hình phân tầng chuẩn doanh nghiệp (Enterprise Layered Architecture):

```
┌─────────────────────────────────────────────────────────────┐
│                   TIER 1: CLIENT (SPA)                      │
│   React 18 + Vite + Axios Interceptors + React Router v6    │
│   (Port: http://localhost:5173)                             │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTP / RESTful API (JSON)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 TIER 2: API & GATEWAY                       │
│   CORS Filter • OpenAPI Swagger Docs • Bean Validation      │
│   Global Exception Handler (@RestControllerAdvice)          │
│   (Port: http://localhost:8080/api/v1)                      │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 TIER 3: BUSINESS & DATA                     │
│   Service Layer (Business Rules & Transaction Logic)        │
│   MongoRepository<T, ID> (Spring Data ODM / JPA Pattern)    │
│   Resilient Fallback Cache (Demo không phụ thuộc local DB)  │
│   MongoDB Database (Port 27017 / MongoDB Atlas Cloud)       │
└─────────────────────────────────────────────────────────────┘
```

---

## 📁 Cấu Trúc Thư Mục Dự Án (Directory Structure)

```
SBA391_Group6_Project/
├── package.json                   # Monolithic root orchestration scripts
├── .gitignore                     # Git ignore chuẩn cho cả React & Java/Maven
├── README.md                      # Tài liệu kiến trúc & hướng dẫn vận hành
│
├── client/                        # TẦNG FRONTEND (React 18 + Vite)
│   ├── package.json
│   ├── vite.config.js
│   ├── .env.example               # Biến môi trường mẫu
│   ├── .env                       # Biến môi trường cục bộ (VITE_API_URL)
│   └── src/
│       ├── api/                   # Giao tiếp HTTP với Backend
│       │   ├── axiosClient.js     # Axios instance cấu hình interceptors, auth token
│       │   ├── healthApi.js       # Health check API
│       │   └── productApi.js      # REST API CRUD Product
│       ├── components/            # Components tái sử dụng
│       │   ├── common/            # Navbar, Footer, StatusBadge, LoadingSpinner
│       │   └── ui/                # Modal, Card...
│       ├── layouts/               # Layout template (MainLayout)
│       ├── pages/                 # Giao diện từng trang
│       │   ├── HomePage.jsx       # Dashboard giám sát server & kiến trúc
│       │   ├── ProductsPage.jsx   # CRUD Demo tương tác trực tiếp Backend
│       │   └── NotFoundPage.jsx   # Trang 404
│       ├── routes/                # Định tuyến React Router v6
│       └── styles/index.css       # Enterprise Design System (Dark theme, glassmorphism)
│
└── server/                        # TẦNG BACKEND (Java 21 Spring Boot 3)
    ├── pom.xml                    # Maven dependencies (Web, MongoDB, Validation, Swagger)
    ├── .env.example
    └── src/main/
        ├── resources/
        │   └── application.yml    # Cấu hình Server, MongoDB, Swagger, CORS
        └── java/com/group6/project/
            ├── Application.java   # Main entry point & Startup banner
            ├── common/            # Thành phần dùng chung toàn hệ thống
            │   ├── response/
            │   │   └── ApiResponse.java        # Format JSON response chuẩn doanh nghiệp
            │   └── exception/
            │       ├── ErrorCode.java          # Mã lỗi HTTP & Business
            │       ├── AppException.java       # Custom runtime exception
            │       ├── ResourceNotFoundException.java
            │       └── GlobalExceptionHandler.java # Bắt mọi ngoại lệ tập trung
            ├── config/            # Cấu hình hệ thống
            │   ├── CorsConfig.java             # Cho phép React gọi API
            │   └── OpenApiConfig.java          # Cấu hình Swagger UI 3
            └── modules/           # Module hóa theo tính năng
                ├── health/
                │   └── HealthController.java   # GET /api/v1/health
                └── product/       # Reference Module mẫu:
                    ├── controller/
                    │   └── ProductController.java  # REST API endpoints
                    ├── service/
                    │   ├── ProductService.java     # Interface business rules
                    │   └── impl/
                    │       └── ProductServiceImpl.java # Xử lý dữ liệu & Fallback
                    ├── repository/
                    │   └── ProductRepository.java  # MongoRepository (Spring Data)
                    ├── model/
                    │   └── Product.java            # Document Entity (@Document)
                    └── dto/
                        ├── ProductRequest.java     # DTO input (Validation @Valid)
                        └── ProductResponse.java    # DTO output
```

---

## ⚡ Tiêu Chuẩn Doanh Nghiệp Đã Được Thiết Lập

1. **Chuẩn hoá API Response (`ApiResponse<T>`)**:
   Mọi API đều trả về cấu trúc thống nhất:
   ```json
   {
     "success": true,
     "status": 200,
     "message": "Product created successfully",
     "data": { ... },
     "timestamp": "2026-10-05T14:00:00Z"
   }
   ```
2. **Kiểm tra dữ liệu đầu vào (Bean Validation)**:
   Sử dụng `@Valid`, `@NotBlank`, `@DecimalMin`, `@Min` tại tầng Controller. Khi vi phạm, `GlobalExceptionHandler` tự động bắt và trả về danh sách trường bị lỗi cụ thể (`errors: { "price": "Price must be non-negative" }`).
3. **Tài liệu API Tự Động (OpenAPI / Swagger 3)**:
   Truy cập `http://localhost:8080/swagger-ui/index.html` để xem và test trực tiếp các endpoint.
4. **Cơ chế Fallback thông minh (Resilient Development)**:
   Nếu máy của thành viên chưa cài MongoDB cục bộ, server tự động kích hoạt bộ nhớ In-Memory để demo CRUD vẫn hoạt động mượt mà. Khi có MongoDB chạy tại `localhost:27017`, dữ liệu sẽ được lưu trực tiếp vào database.
5. **Axios Interceptors phía Client**:
   Tự động giải nén `data`, inject Auth token nếu có, và bắt lỗi mạng tập trung (hiển thị thông báo thân thiện thay vì crash ứng dụng).

---

## 🚀 Hướng Dẫn Cài Đặt & Khởi Chạy

### Yêu Cầu Tiên Quyết (Prerequisites)
- **Node.js**: >= 18 (Khuyên dùng v20+)
- **Java JDK**: >= 21 (Oracle JDK hoặc OpenJDK)
- **Apache Maven**: >= 3.8
- **MongoDB** *(Tùy chọn)*: Chạy local cổng 27017 hoặc MongoDB Atlas.

---

### Bước 1: Cài đặt Dependencies

Tại thư mục gốc dự án:
```bash
npm run install:all
```
*(Lệnh này sẽ tự động cài các gói cần thiết ở cả thư mục gốc và thư mục `client/`)*

---

### Bước 2: Khởi Chạy Ứng Dụng

#### Cách 1: Chạy đồng thời cả Frontend và Backend (Khuyên Dùng) 🌟
Chỉ với **1 câu lệnh duy nhất** ở thư mục gốc:
```bash
npm run dev
```
*(Hệ thống sẽ chạy song song Spring Boot server và Vite React client trong cùng 1 terminal bằng `concurrently`)*

---

#### Cách 2: Chạy riêng từng phần (Terminal riêng biệt)

- **Chạy Client (React Vite)**:
  ```bash
  npm run dev:client
  # Hoặc: cd client && npm run dev
  ```
  Truy cập: [http://localhost:5173](http://localhost:5173)

- **Chạy Server (Spring Boot)**:
  ```bash
  npm run dev:server
  # Hoặc: mvn spring-boot:run -f server/pom.xml
  ```
  Truy cập: [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)

---

## 🔗 Danh Sách Đường Dẫn Quan Trọng (Endpoints)

| Thành Phần | URL | Mô tả |
| :--- | :--- | :--- |
| **Frontend Web App** | [http://localhost:5173](http://localhost:5173) | Giao diện React SPA |
| **CRUD Products Demo** | [http://localhost:5173/products](http://localhost:5173/products) | Trang quản lý sản phẩm tương tác API |
| **Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) | OpenAPI 3 Interactive API Explorer |
| **Health Check API** | [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health) | Kiểm tra trạng thái Server |
| **Products REST API** | [http://localhost:8080/api/v1/products](http://localhost:8080/api/v1/products) | Endpoints CRUD đầy đủ (GET, POST, PUT, DELETE) |

---

## 👥 Quy Ước Code Cho Các Thành Viên Trong Nhóm

1. **Phân tách DTO và Entity**: Không bao giờ trả trực tiếp Entity `@Document` ra ngoài Controller; luôn map qua `ResponseDTO`.
2. **Quy tắc Controller mỏng, Service dày**: Controller chỉ nhận request, gọi validate và chuyển giao cho Service. Mọi nghiệp vụ logic (tính toán, điều kiện, ghi log) đều viết trong Service.
3. **Thêm Module mới**:
   Tạo thư mục trong `server/src/main/java/com/group6/project/modules/<tên_chức_năng>/` gồm đủ các tầng: `controller`, `service`, `repository`, `model`, `dto`.
4. **Tạo API phía Client**:
   Khai báo phương thức trong `client/src/api/<tên>Api.js` sử dụng `axiosClient`.

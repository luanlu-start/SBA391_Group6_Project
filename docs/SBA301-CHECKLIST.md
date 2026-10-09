# Checklist yêu cầu SBA301

Nguồn: SBA301_Final_Project.pdf do nhóm cung cấp. Dự án giữ tên thư mục/artifact SBA391 hiện có;
checklist theo yêu cầu trong đề SBA301 đã xác nhận.

## Đã có nền tảng

- [x] React SPA + React Router, giao diện CRUD mẫu có modal/xác nhận xóa.
- [x] Spring Boot 3, Java 21, package com.fptpost, Controller → Service → Repository.
- [x] SQL Server + JPA/Hibernate: Product CRUD và UUID.
- [x] MongoDB + Spring Data MongoDB: collection activity_logs ghi lịch sử CRUD.
- [x] DTO, MapStruct, validation, ControllerAdvice và mã nghiệp vụ.
- [x] Phân trang, sorting, filtering tại database.
- [x] Swagger/OpenAPI.
- [x] Seed demo tách riêng, không chạy trong prod.
- [x] Flyway migration SQL; Hibernate validate; quy trình baseline database cũ.
- [x] JUnit 5, Mockito, MockMvc và test JPA.
- [x] Docker Compose, cấu hình qua môi trường (phần Docker là điểm cộng).

## Cần hoàn thành theo nghiệp vụ

- [x] Có đặc tả FPTPost v1.0, BR/UC, danh mục dữ liệu/API và kế hoạch phân công TV1–TV6.
- [ ] Nhóm rà soát/duyệt phạm vi, phân công và các điểm khác biệt với base.
- [ ] Ít nhất 5 domain entity có CRUD đầy đủ, ít nhất 5 bảng SQL có ý nghĩa.
- [ ] Có quan hệ 1–N và N–N thực tế; Product.category hiện là chuỗi, chưa thể hiện quan hệ.
- [ ] Hoàn thiện ERD SQL và tài liệu schema Mongo.
- [ ] Ít nhất 6 trang React có ý nghĩa; thêm protected routes và kiểm tra input.
- [ ] Quản lý state theo yêu cầu, responsive UI, Loading/Error/Data trong các luồng.
- [ ] JWT login/logout, BCrypt, ít nhất 2 role; bảo vệ API và frontend.
- [ ] Bổ sung test nghiệp vụ toàn nhóm: happy path, lỗi, edge cases; tối thiểu 10 case có ý nghĩa.
- [ ] Dữ liệu mẫu đủ cho toàn bộ demo và test.
- [ ] Bảng truy vết yêu cầu, thiết kế/diagram, tài liệu API, báo cáo và slide.
- [ ] Git và đóng góp từng thành viên, họp nhóm, checkpoint/peer assessment.
- [ ] README hướng dẫn security/demo accounts và hạn chế sau khi triển khai các module.
- [ ] Mỗi thành viên giải thích được code và phân tích thay đổi tại buổi bảo vệ.

## Mongo schema hiện tại

Collection activity_logs:

| Trường | Kiểu | Ý nghĩa |
| --- | --- | --- |
| _id | ObjectId do Mongo sinh | ID document, biểu diễn chuỗi trong response |
| entityType | string | PRODUCT |
| entityId | string UUID | Tham chiếu Product SQL, giữ được sau khi Product bị xóa |
| action | string | CREATED, UPDATED, DELETED |
| entityName | string | Tên sản phẩm tại thời điểm thao tác |
| occurredAt | BSON date | Thời điểm thao tác |

Index: (entityId ASC, occurredAt DESC, _id ASC) và (occurredAt DESC, _id ASC).
Mongo không có foreign key tới SQL. Lịch sử chỉ ghi sau SQL commit, best effort; chưa có actor/user vì chưa có JWT.
Seed không tạo log. Không cho sửa/xóa lịch sử qua API mẫu.

## Gợi ý phát triển tiếp

Theo [đặc tả nguồn](sources/README.md), FPTPost là nền tảng giao hàng tiện đường P2P;
bưu cục/kho và COD nằm ngoài phạm vi. User–Role N–N, Order–Offer/Photo/StatusHistory
1–N là các quan hệ theo thiết kế, chưa tạo trong schema hiện tại.
Product/Activity là ví dụ kỹ thuật, chưa thay thế module order hoặc audit nghiệp vụ.
Xem [UC](USE-CASES.md), [database](DATABASE.md), [kiến trúc](LOGISTICS-BASE.md) và
[các điểm cần chốt](SPEC-ALIGNMENT.md). Hoàn thiện ERD và contract trước khi chia việc code.

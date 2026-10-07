# Checklist yêu cầu SBA301

Nguồn: SBA301_Final_Project.pdf do nhóm cung cấp. Dự án giữ tên thư mục/artifact SBA391 hiện có;
checklist theo yêu cầu trong đề SBA301 đã xác nhận.

## Đã có nền tảng

- [x] React SPA + React Router, giao diện CRUD mẫu có modal/xác nhận xóa.
- [x] Spring Boot 3 Controller → Service → Repository.
- [x] SQL Server + JPA/Hibernate: Product CRUD và UUID.
- [x] MongoDB + Spring Data MongoDB: collection activity_logs ghi lịch sử CRUD.
- [x] DTO, MapStruct, validation, ControllerAdvice và mã nghiệp vụ.
- [x] Phân trang, sorting, filtering tại database.
- [x] Swagger/OpenAPI.
- [x] Seed demo tách riêng, không chạy trong prod.
- [x] JUnit 5, Mockito, MockMvc và test JPA.
- [x] Docker Compose, cấu hình qua môi trường (phần Docker là điểm cộng).

## Cần hoàn thành theo nghiệp vụ

- [ ] Chốt bài toán/domain và phân công module theo từng thành viên.
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

Nhóm chọn hướng chuyển phát như FPost. Xem LOGISTICS-BASE.md để phân chia module và dữ liệu.
User/Role có thể dùng N–N, Shipment/Parcel dùng 1–N, Branch/Shipment dùng 1–N theo vai trò bưu cục.
Product chỉ là module tham khảo kỹ thuật; không bắt buộc giữ danh mục sản phẩm trong ứng dụng chuyển phát.
Các module chuyển phát chưa được triển khai. Cần chốt quy trình và hoàn thiện ERD trước khi tạo schema.

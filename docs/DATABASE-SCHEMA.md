# Code first và seed data

SQL Server dùng JPA/Hibernate code first. Cấu hình trong `application.yml`:
`spring.jpa.hibernate.ddl-auto: update`. Hibernate tạo bảng còn thiếu và cập nhật
schema từ entity khi backend khởi động. Không dùng Flyway hoặc file SQL đánh số V1/V2.

## Làm việc theo module

Mỗi thành viên khai báo entity, quan hệ, kiểu cột, nullability, unique constraint và index
trong module mình phụ trách. Bảng dùng chung như users/roles chỉ có một owner;
module khác tham chiếu entity/contract đã thống nhất, không tạo bảng trùng.

Pull code mới rồi chạy lại backend; Docker dùng `docker compose up --build -d`.
Thay đổi entity và code sử dụng trường mới phải ở cùng PR, kiểm tra trên SQL Server thật.

`update` giữ dữ liệu hiện có, không nạp lại seed mỗi lần khởi động. Đổi tên/xóa cột,
đổi kiểu có dữ liệu hoặc thêm cột NOT NULL vào bảng có dữ liệu cần xử lý riêng.
`create`/`create-drop` dựng lại bảng và có thể xóa dữ liệu; chỉ dùng với database test
hoặc khi chủ động muốn làm lại database local.

## Dữ liệu nền và dữ liệu mẫu

- `IdentityDataInitializer` tạo role SENDER/COURIER/MODERATOR/ADMIN còn thiếu,
  không phụ thuộc profile demo và không ghi đè role đã có.
- `ProductDemoSeeder` đọc `demo/products.json`, thêm Product mẫu còn thiếu
  khi bật profile `demo` và không có `prod`.
- Module mới có initializer/seeder riêng; dữ liệu nền bắt buộc tách với dữ liệu demo.
- Seeder không gửi email, tạo tài khoản thật hoặc ghi đè dữ liệu người dùng.

MongoDB tiếp tục dùng Spring Data MongoDB và index cho document. Filtered unique index,
trigger và quyền bảo vệ ledger phải thiết kế riêng khi triển khai module cần chúng;
JPA không thay thế mọi tính năng riêng của database.

SPEC v1.0 đề xuất Flyway (ADR-10). Repository chọn code first ngày 11/10/2026
để nhóm triển khai song song. Bản nguồn giữ nguyên; xem [đối chiếu SPEC](SPEC-ALIGNMENT.md).

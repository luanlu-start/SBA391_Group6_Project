# Hướng phát triển base cho hệ thống chuyển phát

Phạm vi đã có: base React/Spring Boot, Product CRUD mẫu lưu SQL Server, lịch sử hoạt động mẫu lưu MongoDB.
Shipment/Parcel, JWT và nghiệp vụ chuyển phát bên dưới là đề xuất phát triển tiếp, chưa có API hay bảng tương ứng.

## Database và module

| Module | Dữ liệu | Database |
| --- | --- | --- |
| identity | User, Role, thông tin khách hàng/nhân viên | SQL Server |
| branch | Bưu cục, địa chỉ, nhân viên trực thuộc | SQL Server |
| shipment | Đơn vận chuyển, mã vận đơn, thông tin gửi/nhận, trạng thái hiện tại | SQL Server |
| parcel | Kiện hàng, khối lượng, kích thước, nội dung khai báo | SQL Server |
| delivery | Phân công lấy/giao hàng, nhân viên, thời gian, kết quả | SQL Server |
| pricing | Dịch vụ, quy tắc tính cước, phí đã chốt của đơn | SQL Server |
| payment | COD, thu tiền, đối soát | SQL Server |
| notification | Thông báo với dữ liệu tùy loại | MongoDB |
| activity | Nhật ký hoạt động, hỗ trợ điều tra thao tác | MongoDB |

User–Role là gợi ý quan hệ N–N; Shipment–Parcel là 1–N.
Một Shipment lưu ảnh chụp thông tin người gửi/nhận và phí tại lúc tạo, để thay đổi hồ sơ/bảng giá
không sửa thông tin đơn đã phát sinh. Giá, COD và phí dùng BigDecimal, tiền tệ phải xác định rõ.
Mã vận đơn là mã nghiệp vụ có unique constraint, tách khỏi UUID khóa chính.
Entity không trả thẳng ra controller, tránh vòng tham chiếu JPA; mapper chuyển sang response DTO.

## Trạng thái và hành trình

Trạng thái đơn hiện tại là dữ liệu nghiệp vụ trong SQL Server. Chuyển trạng thái qua service có kiểm tra
quyền và điều kiện, không cho client tùy ý gán status bất kỳ. Khi nhiều nhân viên cập nhật cùng đơn,
thiết kế optimistic locking bằng Version và mã lỗi xung đột phù hợp.

Đề xuất ban đầu cho nhóm: lịch sử chuyển trạng thái quan trọng cũng lưu SQL trong cùng transaction
với cập nhật đơn. MongoDB lưu thông báo/nhật ký bổ trợ. Cách này giữ đơn và lịch sử nghiệp vụ nhất quán.
Nếu sau này dùng Mongo làm timeline hoặc dữ liệu vị trí, cần xác định rõ dữ liệu gốc và cách đồng bộ.
Lịch sử SQL hoặc Mongo đều hợp lệ; đề không bắt buộc timeline ở Mongo.

ActivityLog hiện tại là best effort sau commit, có thể thiếu bản ghi khi Mongo lỗi.
Không dùng cơ chế này làm bằng chứng duy nhất cho chuyển trạng thái đơn, COD hay đối soát.
Nếu cần đồng bộ SQL sang Mongo đảm bảo không mất, thêm SQL outbox và worker có retry/idempotency.

## Cách dùng module mẫu

- Product minh họa Entity UUID, JpaRepository, Specification, transaction, MapStruct, validation và phân trang.
- Activity minh họa Document, MongoRepository, index, response DTO và sự kiện sau SQL commit.
- Khi bắt đầu module Shipment, giữ các quy ước chung và viết request/service theo nghiệp vụ chuyển phát.
- Sau khi Shipment hoàn thiện, có thể bỏ module Product và trang demo; chưa cần đổi tên Product thành Shipment
  rồi giữ nguyên price/category/stock vì các trường đó chưa mô tả đúng một đơn vận chuyển.

## Thứ tự triển khai

1. Chốt phạm vi: khách tạo đơn, nhân viên xử lý, shipper lấy/giao, quản lý đối soát.
2. Vẽ ERD, chọn ít nhất 5 entity có CRUD ý nghĩa, quan hệ 1–N và N–N.
3. Triển khai User/Role và JWT/BCrypt, áp dụng quyền cho API/trang React.
4. Triển khai Branch, Shipment, Parcel; thêm trạng thái/hành trình và phân công giao hàng.
5. Triển khai cước/COD theo phạm vi, notification Mongo và giao diện tương ứng.
6. Hoàn thiện tối thiểu 6 trang, dữ liệu demo, test nghiệp vụ, tài liệu và kiểm tra yêu cầu SBA301.

Giữ kiến trúc một ứng dụng chia module để nhóm dễ chạy và tích hợp. Chỉ thêm hạ tầng hàng đợi/cache
khi có yêu cầu cụ thể và nhóm kiểm thử, giải thích được luồng xử lý đó.

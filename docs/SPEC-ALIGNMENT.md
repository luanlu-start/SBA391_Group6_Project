# Đối chiếu SPEC với repository

Ngày đối chiếu: 09/10/2026. Nguồn: [Excel v1.0](sources/fptpost-br-uc-v1.0.xlsx)
và [SPEC v1.0](sources/fptpost-spec-v1.0.pdf), đều là dự thảo ngày 08/10/2026.
Tài liệu này ghi lựa chọn đã có và điểm cần thống nhất; không xác nhận base đáp ứng
toàn bộ đặc tả. [Bản gốc](sources/README.md) được giữ nguyên.

## Lựa chọn đã chốt cho repository

Theo trao đổi của nhóm khi xây base, các lựa chọn sau được ưu tiên so với cấu hình
khác trong v1.0 và cần phản ánh vào phiên bản đặc tả tiếp theo.

| Nội dung | SPEC/Excel v1.0 | Lựa chọn của repository |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3 | Java 25, Spring Boot 4.1.1 |
| Frontend | React 18, Router 6, Node 21; JS/JSDoc, TS còn mở | React 19.3, Router 7, Vite 8, TypeScript 6, Node 24 |
| Database | SQL Server + MongoDB | Giữ cả hai; SQL Server 2025 + MongoDB 7 |
| Schema | Flyway, migration bất biến (ADR-10) | Code first, Hibernate update; chưa thêm Flyway khi xây app |
| Cấu hình | Nhiều môi trường/profile | Một application.yml, biến môi trường; demo seed có profile riêng |
| Git | Feature/rebase trên main, tích hợp vào main | Feature từ develop, PR vào develop; bản ổn định sang main |
| Tên nhánh | Ví dụ feature/UC-12-tao-don | Chữ thường, mô tả tiếng Anh: feature/uc-12-create-order |

Tên thư viện trong SPEC không có nghĩa đã cài hoặc cấu hình trong base.

## Điểm cần thống nhất trước khi triển khai nghiệp vụ

| Mã | Nội dung | Hiện tại | SPEC v1.0 | Việc cần chốt |
| --- | --- | --- | --- | --- |
| ALIGN-01 | API lỗi/thành công | ApiResponse, code số, errors map | Problem Details, code chữ, errors array; ví dụ thành công payload trực tiếp | Một contract BE/FE/OpenAPI; chưa đổi code |
| ALIGN-02 | Phân trang | data.content, mặc định 12, sortBy/direction | content, mặc định 20, sort=field,asc; cursor chat/thông báo | Chốt cùng contract API; giữ content |
| ALIGN-03 | Khóa dữ liệu | Product UUID làm khóa chính | BIGINT nội bộ + UUID public_id | Chiến lược chung trước khi tạo entity FPTPost; chưa chuyển ID mẫu |
| ALIGN-04 | Tiền | Product demo BigDecimal/DECIMAL | Số nguyên VND, long/BIGINT | Thống nhất nghiệp vụ VND theo SPEC; chưa đổi kiểu/đơn vị demo |
| ALIGN-05 | Package | com.group6.project, controller/service/repository/model/dto/mapper | com.fptpost, api/application/domain/infrastructure | Chốt trước khi nhiều module được tạo; chưa tái cấu trúc base |
| ALIGN-06 | Mã môn | Repository/artifact SBA391 | Tài liệu ghi SBA301 | Xác nhận tên khi nộp; chưa đổi repository/artifact |
| ALIGN-07 | Ràng buộc DB | Hibernate update | Filtered unique index, trigger/quyền bảo vệ ledger, migration | Cách quản lý/kiểm tra DDL đặc thù; update không tự thay thế mọi DDL |

Cả hai kiểu response/ID có thể được thiết kế nhất quán. Cần quyết định rõ để tránh
từng thành viên viết khác nhau. Thay contract phải cập nhật client, handler, DTO,
OpenAPI và test trong cùng thay đổi.

## Khoảng cách triển khai

| Thành phần theo SPEC | Hiện trạng | Cần hiện thực |
| --- | --- | --- |
| Tài khoản/JWT/KYC | Chưa có | Access/refresh rotation, role + ownership, KYC thủ công, dữ liệu nhạy cảm |
| Đơn/sàn/chào giá/chuyến | Chưa có | State machine/guard, cạnh tranh nhận đơn, lịch sử, giá snapshot |
| Ledger/ví/ký quỹ | Chưa có | Bút toán kép, bất biến số dư, locking/idempotency, SQL transaction |
| VNPay/payout/đối soát | Chưa có | Sandbox, callback xác minh, chống trùng, Mock/thủ công, duyệt tách biệt |
| SQL outbox | Chưa có | Ghi cùng transaction, worker retry và chống trùng eventId |
| Activity Log Mongo | Có mẫu sau commit, best effort | Chưa phải audit bảo đảm của SPEC; không làm nguồn cho tiền/trạng thái |
| QR/OTP/vị trí/chat/WebSocket | Chưa có | Token có mục đích/hạn/sử dụng một lần, quyền tham gia, index/TTL |
| OpenAPI | Runtime Swagger cho base | Chưa có contract đầy đủ cho danh mục FPTPost |
| Kiểm thử | Test base, JPA dùng H2 | SQL Server/Mongo thật; cạnh tranh, idempotency, invariant, E2E |
| CI/quan sát/hạ tầng phụ trợ | Chưa có pipeline theo SPEC | CI/scan/coverage gate, correlationId, MinIO/Mailpit khi triển khai |

Ready/Done trong SPEC mục 11.5 là mục tiêu làm việc. Các mục CI, migration và review
cần phản ánh quyết định code first/hạ tầng thực tế; không đánh dấu đạt khi chưa có
bằng chứng chạy và nghiệm thu các BR.

## Câu hỏi còn mở trong SPEC

Giữ mã từ phụ lục A; đề xuất trong nguồn chưa phải quyết định đã được nhóm duyệt.

| Mã | Nội dung | Trạng thái đối chiếu |
| --- | --- | --- |
| OQ-01 | Hoa hồng trên phí hủy | Chưa chốt |
| OQ-02 | Miền email cộng đồng FPT | Chưa chốt |
| OQ-03 | WebSocket nhiều instance | Chưa chốt |
| OQ-04 | MinIO hoặc tệp cục bộ khi demo | Chưa chốt |
| OQ-05 | JavaScript hay TypeScript | Đã chọn TypeScript trong repository |
| OQ-06 | SMS thật hoặc giả lập | Chưa chốt |
| OQ-07 | Phạm vi địa lý pilot | Chưa chốt |
| OQ-08 | Bảo hiểm hàng giá trị cao | Chưa chốt |
| OQ-09 | Người soạn/rà soát điều khoản và chính sách | Chưa chốt |
| OQ-10 | Đăng nhập Google thuộc Could | Chưa chốt |

## Kiểm tra dữ liệu nguồn

Đếm trực tiếp: 286 BR, 63 UC, 109 PRM, 55 DB, 167 API, 64 SCR; mã từng danh mục
không trùng. Mã UC tường minh trong BR/API/màn hình đều tồn tại trong danh mục UC.
Đây là kiểm tra mã, chưa xác nhận mọi luồng nghiệp vụ/công thức đúng.

Hai ô công thức `13_Kế_hoạch_Nhân_sự!D71:E71` không có kết quả cache trong file
được cung cấp. Khi xem lịch tổng hợp REL cần mở Excel tính lại; không diễn giải
cache trống thành 0. Không sửa bản gốc trong lần nhập này.

# Đối chiếu SPEC với repository

Ngày đối chiếu: 11/10/2026. Nguồn: [Excel v1.0](sources/fptpost-br-uc-v1.0.xlsx)
và [SPEC v1.0](sources/fptpost-spec-v1.0.pdf), đều là dự thảo ngày 08/10/2026.
Tài liệu này ghi lựa chọn đã có và điểm cần thống nhất; không xác nhận base đáp ứng
toàn bộ đặc tả. [Bản gốc](sources/README.md) được giữ nguyên.

## Lựa chọn đã chốt cho repository

Runtime dùng Java 21/Boot 3, React 18/Router 6/Node 21 và namespace com.fptpost.
Ngày 11/10/2026, nhóm chọn Hibernate code first với `ddl-auto: update` thay Flyway
để triển khai song song. Đây là lựa chọn khác ADR-10 trong SPEC; bản nguồn giữ nguyên.

| Nội dung | SPEC/Excel v1.0 | Lựa chọn của repository |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3 | Java 21, Spring Boot 3.5.16, springdoc 2.8.17 |
| Frontend | React 18, Router 6, Node 21; JS/JSDoc, TS còn mở | React 18.3, Router 6, Node 21.7; giữ TypeScript 5.9, dùng Vite 5.4 tương thích |
| Database | SQL Server + MongoDB | Theo lựa chọn mới: SQL Server 2022 + MongoDB 7; volume SQL 2022 riêng |
| Schema | Flyway, migration bất biến (ADR-10) | Hibernate code first, ddl-auto: update; initializer/seed demo riêng |
| Cấu hình | Nhiều môi trường/profile | Một application.yml, biến môi trường; demo seed có profile riêng |
| Git | Feature/rebase trên main, tích hợp vào main | Feature từ develop, PR vào develop; bản ổn định sang main |
| Tên nhánh | Ví dụ feature/UC-12-tao-don | Giữ mã UC-xx, mô tả không dấu: feature/UC-12-tao-don |
| Package/tầng | com.fptpost, api/app/domain/infra | com.fptpost.modules, controller/service/repository/model/dto/mapper |

Tên thư viện trong SPEC không có nghĩa đã cài hoặc cấu hình trong base.

## Điểm cần thống nhất trước khi triển khai nghiệp vụ

| Mã | Nội dung | Hiện tại | SPEC v1.0 | Việc cần chốt |
| --- | --- | --- | --- | --- |
| ALIGN-01 | API lỗi/thành công | Lỗi đã dùng Problem Details, code chữ, errors array, correlationId; thành công giữ ApiResponse | Problem Details; ví dụ thành công payload trực tiếp | BE/FE/OpenAPI đã chuyển lỗi đồng bộ; payload thành công còn khác |
| ALIGN-02 | Phân trang | data.content, mặc định 12, sortBy/direction | content, mặc định 20, sort=field,asc; cursor chat/thông báo | Chốt cùng contract API; giữ content |
| ALIGN-03 | Khóa dữ liệu | Product UUID làm khóa chính; Identity mới dùng BIGINT nội bộ/public UUID | BIGINT nội bộ + UUID public_id | Identity theo SPEC; giữ ID demo, module mới áp dụng cùng chiến lược |
| ALIGN-04 | Tiền | Product demo BigDecimal/DECIMAL | Số nguyên VND, long/BIGINT | Giữ demo như đã chọn; module tiền VND mới theo thiết kế SPEC |
| ALIGN-05 | Package | Đã chuyển com.fptpost, giữ các tầng hiện tại | api/application/domain/infrastructure | Đã chốt namespace và cấu trúc ở bảng trên |
| ALIGN-06 | Mã môn | Repository/artifact SBA391 | Tài liệu ghi SBA301 | Xác nhận tên khi nộp; chưa đổi repository/artifact |
| ALIGN-07 | Ràng buộc DB | Khai báo PK/FK/unique/check/index trong entity, Hibernate update | Filtered unique index, trigger/quyền ledger, migration | Code first; thiết kế và test ràng buộc đặc thù SQL Server khi tạo module ledger/order |

Cả hai kiểu response/ID có thể được thiết kế nhất quán. Cần quyết định rõ để tránh
từng thành viên viết khác nhau. Thay contract phải cập nhật client, handler, DTO,
OpenAPI và test trong cùng thay đổi.

## Khoảng cách triển khai

| Thành phần theo SPEC | Hiện trạng | Cần hiện thực |
| --- | --- | --- |
| Tài khoản/JWT/KYC | UC-01–03 đăng ký/OTP, login JWT, refresh rotation và đăng xuất phiên hiện tại | Danh sách/thu hồi từng thiết bị, quyền nghiệp vụ từng module, hồ sơ/KYC |
| Đơn/sàn/chào giá/chuyến | Chưa có | State machine/guard, cạnh tranh nhận đơn, lịch sử, giá snapshot |
| Ledger/ví/ký quỹ | Chưa có | Bút toán kép, bất biến số dư, locking/idempotency, SQL transaction |
| VNPay/payout/đối soát | Chưa có | Sandbox, callback xác minh, chống trùng, Mock/thủ công, duyệt tách biệt |
| SQL outbox | Chưa có | Ghi cùng transaction, worker retry và chống trùng eventId |
| Activity Log Mongo | Có mẫu sau commit, best effort | Chưa phải audit bảo đảm của SPEC; không làm nguồn cho tiền/trạng thái |
| QR/OTP/vị trí/chat/WebSocket | Chưa có | Token có mục đích/hạn/sử dụng một lần, quyền tham gia, index/TTL |
| OpenAPI | Runtime Swagger cho base | Chưa có contract đầy đủ cho danh mục FPTPost |
| Kiểm thử | Test base, JPA dùng H2 | SQL Server/Mongo thật; cạnh tranh, idempotency, invariant, E2E |
| CI/quan sát/hạ tầng phụ trợ | Có correlationId header/body/log và Mailpit local; chưa có pipeline | CI/scan/coverage gate, structured JSON log, MinIO khi triển khai |

Ready/Done trong SPEC mục 11.5 là mục tiêu làm việc. Thay đổi schema cập nhật cùng entity;
CI và review cần được hiện thực theo hạ tầng thực tế. Không đánh dấu đạt khi chưa có
bằng chứng chạy và nghiệm thu các BR. Xem [quy trình code first](DATABASE-SCHEMA.md).

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

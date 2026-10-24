# Database FPTPost

Nguồn: [Excel v1.0](sources/fptpost-br-uc-v1.0.xlsx), sheet `09_Dữ_liệu`, dòng 5–59;
[SPEC v1.0](sources/fptpost-spec-v1.0.pdf), mục 3–4.
Đây là từ điển **thiết kế dự kiến**, chưa phải schema đang chạy hoặc ERD hoàn chỉnh.

## Phân chia dữ liệu

| Kho | Dữ liệu và vai trò |
| --- | --- |
| SQL Server | Tài khoản, đơn, chào giá/chuyến đi, trạng thái/lịch sử, ví/sổ cái/ký quỹ, thanh toán/rút/đối soát, tham số, outbox và idempotency |
| MongoDB | Sự kiện hành trình, điểm vị trí, phòng/tin nhắn chat, thông báo và audit |
| Lưu trữ tệp (dự kiến) | Ảnh đơn, KYC và bằng chứng; database giữ khóa tệp/checksum |

SQL là nguồn cho trạng thái đơn và tiền. Trong cùng transaction SQL: cập nhật nghiệp vụ,
ghi lịch sử, bút toán cần thiết và outbox. Worker xử lý outbox với retry và chống trùng
theo eventId; không giả định transaction JPA bao gồm MongoDB hoặc dịch vụ ngoài.
Lịch sử trạng thái SQL và audit Mongo có trách nhiệm khác nhau.

## Quy ước theo SPEC

| Nội dung | Thiết kế mục tiêu |
| --- | --- |
| Khóa | `id BIGINT IDENTITY` nội bộ + `public_id UNIQUEIDENTIFIER` unique dùng ngoài; tracking_code riêng |
| Tiền | Số nguyên VND: Java `long`, SQL `BIGINT`; kiểm tra miền giá trị theo loại số dư/bút toán |
| Thời gian | `Instant` UTC; SPEC đề xuất `DATETIME2(3)`, hiển thị UTC+7 |
| Tên/enum | snake_case, enum chuỗi kèm ràng buộc theo sheet trạng thái |
| Tiếng Việt | NVARCHAR; cột tìm kiếm chuẩn hóa/index khi truy vấn cần |
| Audit và cạnh tranh | created_at/updated_at/created_by/updated_by; `@Version` trên dữ liệu cập nhật đồng thời |
| Giá | Chốt snapshot giá và phiên bản tham số theo đơn; không tính lại đơn cũ từ bảng giá mới |

Thiết kế khóa trong SPEC khác Product hiện dùng UUID làm khóa chính. **Chưa đổi ID
hoặc dữ liệu hiện tại**; cần chốt một chiến lược chung trước khi tạo entity nghiệp vụ.
Product demo hiện dùng BigDecimal/DECIMAL; nghiệp vụ VND mới theo thiết kế phải thống nhất
số nguyên VND xuyên suốt DB, Java, DTO và giao diện.

## Ràng buộc cần thiết kế khi tạo entity

- Nhận đơn cạnh tranh: cập nhật có điều kiện từ OPEN, chỉ một người thắng.
- Ví không âm ở các loại số dư không cho phép âm; nợ là dữ liệu riêng.
- Sổ cái bút toán kép cân bằng, chỉ ghi qua ledger; điều chỉnh bằng bút toán mới.
- Duy nhất cho mã tham chiếu thanh toán, idempotency key, escrow của đơn và đánh giá theo người/đơn.
- Duy nhất có điều kiện cho một chào giá chờ theo cặp và một yêu cầu rút đang mở.
- Khóa ngoại và lịch sử SQL trong transaction; kiểm tra quyền sở hữu ở tầng ứng dụng.
- Mongo: index orderId/thời gian, unique phòng theo đơn, chống trùng thông báo;
  2dsphere cho vị trí, TTL theo tham số cho dữ liệu được phép hết hạn. Audit không áp TTL tùy tiện.

Chi tiết trường, quan hệ, điều kiện và index xem sheet `09_Dữ_liệu` và SPEC mục 3.3–3.5.
Unique index có điều kiện/trigger/quyền DB cần được thiết kế riêng khi triển khai module.
Nhóm kiểm tra DDL và hành vi trên SQL Server thật khi hiện thực các ràng buộc này;
không coi Hibernate update là kiểm tra thay thế cho mọi index/trigger/quyền.

## Danh mục thiết kế

55 mục (49 SQL, 6 Mongo); một mục có thể liệt kê nhiều bảng vật lý.
Giữ nguyên mã phân hệ và cách viết phạm vi UC trong nguồn.

| Mã | Bảng / collection dự kiến | Kho | Phân hệ | UC liên quan |
| --- | --- | --- | --- | --- |
| DB-001 | users | SQL Server | AUTH | UC-01..06 |
| DB-002 | roles, user_roles | SQL Server | AUTH | UC-03, UC-10, UC-57 |
| DB-003 | user_profiles | SQL Server | KYC | UC-07 |
| DB-004 | consents | SQL Server | KYC | UC-01, UC-08 |
| DB-005 | addresses | SQL Server | KYC | UC-07, UC-12 |
| DB-006 | sessions (refresh_tokens) | SQL Server | AUTH | UC-03, UC-04 |
| DB-007 | otp_codes | SQL Server | AUTH | UC-02, UC-05 |
| DB-008 | kyc_requests | SQL Server | KYC | UC-08, UC-09 |
| DB-009 | kyc_files | SQL Server | KYC | UC-08, UC-09 |
| DB-010 | courier_profiles | SQL Server | KYC | UC-10, UC-49 |
| DB-011 | bank_accounts | SQL Server | PAY | UC-39, UC-40 |
| DB-012 | item_categories | SQL Server | ADM | UC-12, UC-58 |
| DB-013 | prohibited_keywords | SQL Server | SAF | UC-54, UC-58 |
| DB-014 | orders | SQL Server | ORD | UC-11..15 |
| DB-015 | order_photos | SQL Server | ORD | UC-12, UC-25, UC-28 |
| DB-016 | order_status_history | SQL Server | TRK | UC-13 |
| DB-017 | offers | SQL Server | MKT | UC-18, UC-19 |
| DB-018 | invitations | SQL Server | MKT | UC-20 |
| DB-019 | favorite_couriers | SQL Server | MKT | UC-20 |
| DB-020 | trips | SQL Server | TRP | UC-22, UC-23 |
| DB-021 | trip_order_matches | SQL Server | MKT | UC-21 |
| DB-022 | order_legs | SQL Server | REL | UC-32, UC-33 |
| DB-023 | handover_tokens | SQL Server | TRK | UC-25, UC-28, UC-33 |
| DB-024 | wallets | SQL Server | PAY | UC-38 |
| DB-025 | ledger_accounts | SQL Server | PAY | UC-35, UC-36 |
| DB-026 | ledger_transactions | SQL Server | PAY | UC-34..43 |
| DB-027 | ledger_entries | SQL Server | PAY | UC-34..43 |
| DB-028 | escrows | SQL Server | PAY | UC-35, UC-36 |
| DB-029 | payment_transactions | SQL Server | PAY | UC-34, UC-35, UC-42 |
| DB-030 | refund_requests | SQL Server | PAY | UC-37 |
| DB-031 | withdrawal_requests | SQL Server | PAY | UC-40, UC-41 |
| DB-032 | reconciliation_reports | SQL Server | PAY | UC-43 |
| DB-033 | reconciliation_items | SQL Server | PAY | UC-43 |
| DB-034 | system_parameters | SQL Server | ADM | UC-46 |
| DB-035 | parameter_history | SQL Server | ADM | UC-46, UC-60 |
| DB-036 | promotions, promotion_usages | SQL Server | FEE | UC-44, UC-45 |
| DB-037 | reviews | SQL Server | REP | UC-47, UC-48 |
| DB-038 | trust_scores, trust_score_history | SQL Server | REP | UC-49 |
| DB-039 | complaints | SQL Server | DSP | UC-50, UC-51 |
| DB-040 | complaint_evidence, complaint_messages | SQL Server | DSP | UC-50, UC-51 |
| DB-041 | compensations | SQL Server | DSP | UC-52 |
| DB-042 | violation_reports | SQL Server | SAF | UC-55 |
| DB-043 | blacklist | SQL Server | SAF | UC-56, UC-09 |
| DB-044 | service_areas, meeting_points | SQL Server | ADM | UC-58 |
| DB-045 | content_pages, notification_templates | SQL Server | ADM | UC-53, UC-58 |
| DB-046 | badges, user_badges, referrals | SQL Server | GAM | UC-62 |
| DB-047 | job_runs, shedlock | SQL Server | ADM | UC-63 |
| DB-048 | outbox_events | SQL Server | SYS | UC-35, UC-53 |
| DB-049 | idempotency_keys | SQL Server | SYS | UC-35, UC-40 |
| DB-050 | tracking_events | MongoDB | TRK | UC-25..30 |
| DB-051 | location_pings | MongoDB | TRK | UC-26, UC-27 |
| DB-052 | chat_rooms | MongoDB | NEG | UC-24 |
| DB-053 | chat_messages | MongoDB | NEG | UC-24 |
| DB-054 | notifications | MongoDB | NTF | UC-53 |
| DB-055 | audit_logs | MongoDB | ADM | UC-60 |

## Schema hiện tại và code first

SQL có `products` phục vụ ví dụ và các bảng Identity cho UC-01–03:
`users`, `roles`, `user_roles`, `user_profiles`, `consents`, `otp_codes`, `blacklist`.
UC-03 thêm `sessions` lưu hash refresh token/phiên và bộ đếm khóa đăng nhập trong User.
Trường `sessions.remember_me` lưu lựa chọn ghi nhớ đăng nhập để refresh giữ đúng loại cookie;
DDL `update` thêm cột `bit NOT NULL DEFAULT 0`, các phiên cũ mặc định không ghi nhớ.
Mongo có `activity_logs` phục vụ ví dụ kỹ thuật. ActivityLog mẫu chưa phải audit_logs
theo SPEC: chưa có actor, kiểm soát truy cập và đồng bộ outbox đảm bảo.

UC-05 dùng lại `otp_codes` với purpose `PASSWORD_RESET`, không thêm bảng hoặc migration.
OTP đăng ký và đặt lại mật khẩu được kiểm tra riêng. Reset dùng mã mới nhất theo tài khoản/purpose;
`tries` lưu cả khi trả lỗi, `used_at` chống sử dụng lại. Khi thành công, cập nhật `users.password_hash`,
đánh dấu OTP đã dùng và thu hồi toàn bộ `sessions` của tài khoản trong cùng transaction có khóa User.

UC-04 dùng `sessions` để liệt kê các phiên đang hoạt động và thu hồi theo tài khoản sở hữu.
Khi refresh, phiên mới giữ thời điểm đăng nhập `created_at`; phiên cũ lưu `replaced_by`
để thao tác đăng xuất trên danh sách cũ vẫn thu hồi đúng phiên tiếp nối.

Danh sách chỉ gồm các phiên chưa bị thu hồi/thay thế và chưa hết hạn; mặc định tối đa 5 phiên
hoạt động mỗi tài khoản. `sessions.browser_id` là UUID của cookie nhận diện trình duyệt/profile;
index `ix_sessions_user_browser` dùng cho cặp tài khoản/trình duyệt. Login và refresh dùng khóa
theo User để mỗi trình duyệt chỉ có một phiên hoạt động cho cùng tài khoản; login mới thu hồi
phiên trước. Cột cho phép null để DDL update tương thích dữ liệu cũ; phiên cũ được gắn mã khi
refresh hoặc thay bằng login mới có refresh cookie. Không gộp dữ liệu cũ theo User-Agent/IP.
Job dọn dữ liệu chạy mỗi giờ, xóa bản ghi đã hết hạn thêm 7 ngày theo
`app.authentication.expired-session-retention-days`. Các bản ghi đã xoay token/thu hồi nhưng
chưa hết hạn được giữ lại để phát hiện việc dùng lại refresh token. Index `ix_sessions_expiry`
phục vụ truy vấn dọn dữ liệu. Có thể đổi lịch bằng `app.authentication.session-cleanup-cron`
hoặc đặt `-` để tắt lịch; việc hết hạn và thu hồi phiên vẫn được kiểm tra khi xác thực.

Hibernate `ddl-auto: update` tạo/cập nhật schema từ entity, cấu hình trong một
`application.yml`. Identity initializer tạo role nền; seed demo riêng theo module/profile,
chỉ thêm mẫu còn thiếu, không ghi đè dữ liệu đã sửa.
Mỗi module có một owner cho entity/bảng; module khác dùng contract đã thống nhất,
không tạo lại bảng dùng chung. Thay đổi entity và code sử dụng trường mới ở cùng PR;
pull code mới rồi restart backend, Docker dùng `docker compose up --build -d`.
Kiểm tra thay đổi schema trên SQL Server thật.

`update` giữ dữ liệu hiện có, không nạp lại seed mỗi lần khởi động. Đổi tên/xóa cột,
đổi kiểu hoặc thêm cột NOT NULL vào bảng có dữ liệu cần xử lý dữ liệu/schema riêng.
`create`/`create-drop` có thể xóa dữ liệu; chỉ dùng với database test hoặc khi chủ động
muốn làm lại database local.

`IdentityDataInitializer` tạo role SENDER/COURIER/MODERATOR/ADMIN còn thiếu,
không tạo tài khoản/mật khẩu mặc định. `ProductDemoSeeder` đọc `demo/products.json`,
chỉ thêm UUID mẫu còn thiếu khi bật `demo` và không có `prod`.
Module mới có initializer/seeder riêng, tách dữ liệu nền bắt buộc khỏi dữ liệu demo;
không ghi đè dữ liệu người dùng hoặc gửi email khi seed.

Repository hiện chọn Hibernate code first, không dùng Flyway; đây là lựa chọn khác
ADR-10 trong SPEC v1.0. Các tài liệu nguồn được giữ nguyên.

Chi tiết bản thiết kế hoàn chỉnh, bao gồm sơ đồ ERD trực quan (Mermaid), đặc tả 100% các bảng (bảng hiện có trong code và bảng thiết kế mới), mã nguồn DDL SQL Server / DBML, và các chiến lược xử lý Race Condition, Đối soát Ví - Ký quỹ - Sổ cái (Triple-Reconciliation) và Phân vùng (Partitioning) được trình bày tại:
👉 **[TÀI LIỆU THIẾT KẾ CƠ SỞ DỮ LIỆU TOÀN DIỆN (DATABASE_DESIGN.md)](DATABASE_DESIGN.md)**

Khởi tạo dữ liệu mẫu tự động được thực hiện qua file `server/src/main/resources/data.sql` với cơ chế `defer-datasource-initialization: true` và `sql.init.mode: always` cấu hình trong `application.yml`.


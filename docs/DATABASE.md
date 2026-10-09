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
Unique index có điều kiện/trigger/quyền DB phải được viết trong migration Flyway.
Nhóm kiểm tra DDL và hành vi trên SQL Server thật khi hiện thực các ràng buộc này;
không coi Hibernate validate là kiểm tra thay thế cho mọi index/trigger/quyền.

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

## Schema hiện tại và migration

Hiện chỉ có SQL `products` và Mongo `activity_logs` phục vụ ví dụ kỹ thuật;
hai tên này không nằm trong danh mục nghiệp vụ v1.0. ActivityLog mẫu chưa phải audit_logs
theo SPEC: chưa có actor, kiểm soát truy cập và đồng bộ outbox đảm bảo.

Flyway có V1 tạo products; Hibernate `ddl-auto: validate`, cấu hình trong một
`application.yml`. Migration đã áp dụng là bất biến, thay đổi schema bằng phiên bản mới.
Seed riêng theo module/profile demo, chỉ thêm mẫu còn thiếu, không ghi đè dữ liệu đã sửa.
Chưa có seed hay migration các bảng nghiệp vụ FPTPost. Database Hibernate cũ cần được
kiểm tra và baseline theo [hướng dẫn Flyway](DATABASE-MIGRATIONS.md).

Việc tiếp theo: chốt chiến lược ID, vẽ ERD đầy đủ từ từ điển, xác định quan hệ/index và
transaction từng UC trước khi tạo entity. Xem [SPEC-ALIGNMENT.md](SPEC-ALIGNMENT.md).

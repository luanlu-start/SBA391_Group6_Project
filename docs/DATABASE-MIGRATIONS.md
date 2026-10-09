# Quản lý schema bằng Flyway

Flyway quản lý SQL Server; Hibernate dùng `ddl-auto: validate` để kiểm tra entity
khớp schema. Cấu hình vẫn trong một `application.yml`.
MongoDB dùng document/index của Spring Data, không chạy T-SQL qua Flyway.
Khóa Flyway core/sqlserver cùng phiên bản 11.20.3. Database được chọn là SQL Server 2022.
Compose dùng volume sqlserver-2022-data riêng; volume SQL cũ được giữ lại, không gắn
file database đã chạy SQL 2025 vào SQL 2022. Việc chuyển dữ liệu giữa các phiên bản
là bước xuất/nhập riêng, không được thực hiện bằng baseline Flyway.

## Migration hiện có

`server/src/main/resources/db/migration/V1__create_products.sql` tạo bảng products,
khóa UUID, DECIMAL cho giá demo, check giá/tồn kho/trạng thái và index phân trang.
Migration chỉ tạo schema; ProductDemoSeeder vẫn nạp mẫu riêng với profile demo.
Chưa có bảng order/ledger và các constraint/trigger nghiệp vụ FPTPost.

Database SQL mới: tạo database trống, chạy ứng dụng; Flyway áp V1 trước khi JPA
validate và seeder chạy. Bảng `flyway_schema_history` ghi phiên bản và checksum.

## Quy ước thay đổi schema

- Một phiên bản được dùng một lần trong toàn repository, ví dụ `V2__add_order_tables.sql`.
- Không sửa/đổi tên/xóa migration đã chạy ở database chung; tạo phiên bản mới.
- Thay entity kèm migration, index/ràng buộc và kiểm tra dữ liệu cũ bị ảnh hưởng trong cùng PR.
- Dùng SQL Server thật để kiểm tra T-SQL, filtered unique index, trigger và transaction.
- Không bật Hibernate update/create để né migration. Không dùng Flyway clean khi chạy app.
- Dữ liệu nền bắt buộc (role/tham số) phải có cách quản lý phiên bản; dữ liệu demo theo module/profile riêng.

Các thành viên thống nhất số migration trước khi merge; phát hiện trùng version thì đổi
file chưa áp dụng trước khi tích hợp. Không đánh dấu UC hoàn thành chỉ vì có bảng.

## Database cũ do Hibernate tạo

Database không rỗng chưa có flyway_schema_history sẽ bị từ chối theo mặc định.
Không xóa volumes để xử lý lỗi này. Trước khi tiếp nhận database cũ:

1. Sao lưu, xác nhận đúng database và đối chiếu products với V1 (cột, kiểu, nullability,
   primary key, check constraint và index). Kiểm tra bảng khác nếu database được dùng chung.
2. Chỉ khi schema tương đương V1, bật baseline một lần ở version 1; V1 được coi là
   đã có, không chạy lại CREATE TABLE và không ghi đè dữ liệu.
3. Sau khi ứng dụng chạy thành công, kiểm tra history có BASELINE version 1, dữ liệu
   cũ còn nguyên và Hibernate validate đạt; tắt baseline-on-migrate rồi khởi động lại.
4. Nếu schema chưa tương đương, điều chỉnh có kiểm soát trước khi baseline; không dùng
   baseline hoặc repair để che sai lệch schema/checksum.

Docker: đặt `FLYWAY_BASELINE_ON_MIGRATE=true` trong .env **chỉ cho lần tiếp nhận đã
kiểm tra**, chạy `docker compose up --build -d`, rồi trả biến về false và chạy lại
`docker compose up -d server` để áp cấu hình. Mặc định trong .env.example là false.

Chạy ngoài Docker: đặt `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` cho lần khởi động
đó; sau khi baseline, xóa biến hoặc đặt false. baseline-version được cấu hình là 1.

## Kiểm thử

Test JPA hiện dùng H2 với create-drop và tắt Flyway ở từng test slice; chỉ kiểm tra
repository/transaction, không xác nhận migration T-SQL. Kiểm tra migration riêng trên
SQL Server: database mới, khởi động lại không áp trùng, giữ dữ liệu/seed đã sửa,
constraint từ chối dữ liệu sai và schema khớp Hibernate validate.

Tài liệu tham chiếu:
[Spring Boot database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html),
[Flyway baseline](https://documentation.red-gate.com/fd/baselines-273973441.html),
[baseline-on-migrate](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html).
Phiên bản SQL Server được kiểm tra trong
[Flyway 11.20.3](https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-sqlserver/src/main/java/org/flywaydb/database/sqlserver/SQLServerDatabase.java).

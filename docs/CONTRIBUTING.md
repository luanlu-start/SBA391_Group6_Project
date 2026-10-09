# Quy ước code và đóng góp

Tài liệu thống nhất cách viết backend, frontend và tích hợp chức năng cho nhóm.
Product là module mẫu SQL Server/JPA; Activity là module mẫu MongoDB.

Khi triển khai FPTPost, đọc [thông tin dự án](PROJECT-INFO.md),
[UC được phân công](USE-CASES.md) và [đối chiếu SPEC](SPEC-ALIGNMENT.md).
Phần API/phân trang bên dưới mô tả contract base đang chạy; chốt các khác biệt
trước khi nhiều thành viên viết module nghiệp vụ.

## 1. Đặt tên và format

| Loại | Quy ước | Ví dụ |
| --- | --- | --- |
| Java package/module | Chữ thường | `com.fptpost.modules.product`, `com.fptpost.modules.order` |
| Java class/interface/enum | PascalCase | `ProductService`, `ProductErrorCode` |
| Java field/method | camelCase | `createdAt`, `getProductById` |
| Hằng số, enum value | UPPER_SNAKE_CASE | `PRODUCT_NOT_FOUND`, `IN_TRANSIT` |
| React component/page/layout | PascalCase, file cùng tên | `ProductsPage.tsx`, `Modal.tsx` |
| Custom hook | Bắt đầu bằng use | `useOrders` |
| File API frontend | camelCase, hậu tố Api | `productApi.ts`, `orderApi.ts` |
| SQL table/column | snake_case | `products`, `created_at` |
| Endpoint | Danh từ số nhiều, kebab-case | `/products`, `/activity-logs` |
| Nhánh chức năng | feature/mã-UC-mô-tả-kebab-case | `feature/UC-12-tao-don` |

Dùng tiếng Anh cho tên code. Java thụt 4 spaces; TypeScript/TSX thụt 2 spaces.
Giữ style của file đang sửa, chạy formatter IDE và ESLint, xóa import/biến không dùng.
Comment giải thích lý do hoặc điều kiện nghiệp vụ khó nhận ra; không lặp lại nguyên nội dung code.

Order/useOrders là ví dụ cho module FPTPost tương lai, chưa phải chức năng đã có.

## 2. Tổ chức module backend

```text
com/fptpost/modules/<module>/
├── controller/
├── service/
├── repository/
├── model/
├── dto/
├── mapper/
├── exception/
└── config/          # Khi module có seed hoặc cấu hình riêng
```

Chỉ tạo thư mục/lớp cần dùng. Event đặt trong `event` nếu module có sự kiện.
`common` dành cho response, request và exception dùng chung; tránh đưa nghiệp vụ riêng vào đó.

| Thành phần | Trách nhiệm |
| --- | --- |
| Controller | Nhận request, `@Valid`, gọi service, trả response và HTTP status |
| Service | Điều kiện nghiệp vụ, thao tác dữ liệu, transaction, phát sự kiện |
| Repository | CRUD và truy vấn database |
| Model | Entity JPA hoặc document MongoDB |
| DTO | Request/response contract; validation đầu vào |
| Mapper | Ánh xạ DTO/model bằng MapStruct |
| Exception | Enum mã lỗi của module triển khai `ErrorCode` |
| Seeder | Nạp mẫu cho module, tách khỏi service nghiệp vụ |

Service dùng một class `@Service` khi chỉ có một implementation.
Chỉ tách interface/implementation khi có nhu cầu thực tế.
Dependency injection qua constructor, có thể dùng `@RequiredArgsConstructor` và field `final`.

Module khác giao tiếp qua facade/interface hoặc sự kiện, không gọi repository nội bộ của nhau.
Ranh giới theo [kiến trúc FPTPost](LOGISTICS-BASE.md). Nhóm chọn package com.fptpost
với các tầng controller/service/repository/model/dto/mapper, không thêm bốn tầng trùng chức năng.

## 3. DTO, mapping và validation

Request và response tách khỏi entity/document. Dùng tên `ProductRequest`, `ProductResponse`,
`ProductSearchRequest`; tách request tạo/sửa khi hai thao tác có dữ liệu hoặc validation khác nhau.

- Request khai báo `@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Digits`... theo dữ liệu.
- Controller dùng `@Valid`; kiểm tra nghiệp vụ cần đọc database nằm trong service.
- Giới hạn DTO phải khớp độ dài, precision và scale của database.
- Dùng MapStruct `componentModel = "spring"`, `unmappedTargetPolicy = ERROR`.
- Bỏ qua ID và timestamps trong mapping từ input; các trường này do server quản lý.
- Quy định rõ field bỏ qua, field null để xóa và field null để giữ khi update.

Product mẫu giữ ID/createdAt và giữ status khi không gửi; description có thể xóa.

## 4. REST API, response và mã lỗi

Endpoint theo `/api/v1/<resources>`; dùng GET/POST/PUT/DELETE phù hợp.
ID của entity SQL dùng UUID khi phù hợp; dùng typed UUID trong controller để validate giá trị.

API module trả `ResponseEntity<ApiResponse<T>>`.
Danh sách trả `ApiResponse<PageResponse<T>>`, không trả trực tiếp `Page` của Spring hoặc entity.
HTTP status phải khớp trường `status` trong response; `code` thể hiện kết quả nghiệp vụ.

Service ném `AppException(<Module>ErrorCode...)` cho lỗi nghiệp vụ.
`GlobalExceptionHandler` xử lý response; không lặp try/catch ở mỗi controller/service
và không trả chi tiết exception nội bộ cho client.

| Code hiện có | Ý nghĩa |
| --- | --- |
| 1000 | Thành công |
| 1001–1005 | Xác thực, request, quyền truy cập, không tìm thấy, trùng tài nguyên |
| 2001 | Product không tồn tại |
| 9999 | Lỗi nội bộ |

Module mới thống nhất mã với nhóm và cập nhật tài liệu, tránh trùng mã.
Frontend đọc `code` và `errors`, không so sánh `message`.
Thêm mô tả API bằng OpenAPI theo controller mẫu.
JWT/phân quyền chưa triển khai; khi bổ sung cần bảo vệ cả endpoint lẫn trang frontend.

## 5. Repository, tìm kiếm và phân trang

SQL dùng `JpaRepository`; bộ lọc tùy chọn dùng `JpaSpecificationExecutor` + `Specification`.
Mongo dùng `MongoRepository`; thêm MongoTemplate/custom fragment khi truy vấn thực sự cần.
Không tạo nhiều method repository cho mọi tổ hợp bộ lọc.

Product là ví dụ tham khảo:

```text
/api/v1/products?search=Mac&category=Electronics&status=ACTIVE&minPrice=0&maxPrice=5000&page=0&size=12&sortBy=price&direction=ASC
```

Bộ lọc kết hợp AND; search tìm tên chứa từ khóa và escape SQL LIKE wildcard.
Category khớp toàn bộ, không phân biệt hoa thường.

- Page bắt đầu từ 0; size từ 1 đến 100.
- Validate sort field bằng whitelist và direction ASC/DESC.
- Thêm ID làm tiêu chí sort phụ để thứ tự ổn định.
- Filter, sort, paging và count chạy tại database.
- Response gồm content, page, size, totalElements, totalPages, first, last.
- Khi thêm bộ lọc, cập nhật DTO, query và kiểm tra tổ hợp điều kiện.

Index dựa trên truy vấn thực tế; không giả định tìm kiếm contains sẽ sử dụng index hiệu quả.

## 6. Database và transaction

SQL entity dùng `jakarta.persistence.Entity/Id`; Mongo document dùng `Document` và Spring Data `Id`.
Không dùng `GeneratedValue` của JPA cho document MongoDB.
Product mẫu dùng `UUID` + `GenerationType.UUID`, `BigDecimal`/`DECIMAL` cho giá.
SPEC FPTPost dùng BIGINT nội bộ + UUID public_id; chiến lược ID cần chốt trước khi
tạo entity nghiệp vụ. Tiền VND trong thiết kế dùng Java `long` và SQL `BIGINT`,
không tự suy diễn đơn vị giá của Product demo là tiền VND nghiệp vụ.
Timestamps dùng `Instant` theo quy ước UTC của base.

Khi thêm quan hệ JPA, tránh đưa quan hệ vào toString/equals/hashCode khiến đọc dữ liệu ngoài ý muốn.
Thiết kế unique constraint, foreign key và index cùng với entity.

Service ghi SQL dùng `@Transactional`; service đọc có thể dùng `readOnly = true`.
Không bắt lỗi database rồi trả thành công hoặc fallback dữ liệu bộ nhớ.
Transaction JPA không tự bao gồm MongoDB.

Product phát `ProductActivityEvent` trong transaction.
Activity listener ghi MongoDB ở `AFTER_COMMIT`, tránh ghi lịch sử cho thao tác SQL rollback.
Lịch sử hiện là best effort: lỗi Mongo được log sau SQL commit và có thể mất bản ghi.
API đọc lịch sử vẫn trả lỗi khi Mongo không truy cập được.
Đối với sự kiện nghiệp vụ FPTPost, thiết kế SQL outbox cùng transaction,
worker retry/idempotency theo eventId như SPEC yêu cầu; cơ chế này chưa triển khai.

Không dùng ActivityLog mẫu làm bằng chứng duy nhất cho trạng thái đơn, ví/ký quỹ hoặc đối soát.
Chỉ module ledger được ghi bút toán; ghi lịch sử trạng thái và bút toán liên quan trong
cùng transaction SQL, có kiểm tra quyền và điều kiện. Xem [database](DATABASE.md).

## 7. Migration và seed data

Cấu hình trong một `application.yml`; Flyway quản lý schema, Hibernate dùng `ddl-auto: validate`.
Sửa entity kèm migration mới với unique/foreign key/check/index cần thiết trong cùng PR.
Không sửa migration đã áp dụng; không dùng Hibernate update/create để né migration.
Filtered unique index, trigger và quyền bảo vệ ledger phải có migration T-SQL và test
SQL Server khi module tương ứng được tạo; chưa có các bảng ledger trong base.

Seed demo tách khỏi migration schema. Database cũ do Hibernate tạo phải được kiểm tra
và baseline một lần theo [hướng dẫn migration](DATABASE-MIGRATIONS.md), giữ dữ liệu cũ.

Mỗi module có seeder riêng nếu cần. Không gom mẫu của mọi module vào service hoặc một AppInit quá lớn.
Seed dùng mã/ID ổn định để kiểm tra bản ghi tồn tại, chỉ thêm mẫu còn thiếu.
Product mẫu dùng JDBC INSERT với UUID cố định để tránh merge ghi đè dữ liệu đã sửa.
Seeder Product đọc `demo/products.json`, không tạo ActivityLog và không chạy với prod.

## 8. Frontend

- Page và layout dùng function component/hooks; component dùng lại đặt trong `components`.
- API theo module đặt trong `src/api` và gọi qua `axiosClient`.
- Không hardcode URL backend trong page/component.
- Xử lý loading, lỗi, dữ liệu rỗng và dữ liệu có kết quả.
- Form có kiểm tra đầu vào và hiển thị lỗi field từ backend.
- Không mutate trực tiếp state; cập nhật bằng setter.
- Request cũ không được ghi đè kết quả của bộ lọc/trang mới.
- Component nhận props khai báo type/interface TypeScript; không dùng PropTypes. API và state phải có kiểu dữ liệu rõ ràng, không dùng any để bỏ qua lỗi type.

Interceptor trả thẳng payload ApiResponse. Ví dụ:

```typescript
const response = await productApi.getAll({ page: 0, size: 12 });
const products = response.data.content;
```

Không đọc thêm `response.data.data` vì Axios response đã được unwrap.
Kiểu dùng chung đặt trong `src/types`; API khai báo `ApiResponse<T>` và `PageResponse<T>`.
Lỗi từ axiosClient là `ApiError`. Trong `catch`, dùng `getApiError(error)` để đọc
`message`, `code`, `errors` mà không bỏ qua kiểm tra kiểu của TypeScript.

## 9. Kiểm thử, cấu hình và đóng góp

Viết test cho nghiệp vụ quan trọng: đường thành công, lỗi, validation, bộ lọc, phân trang,
transaction/rollback. Không cần test chỉ để lặp lại getter/setter.
Đặt test backend trong `server/src/test/java`; tên class kết thúc bằng `Test`,
tên method mô tả hành vi.

Trước khi gửi Pull Request, chạy từ thư mục gốc:

```powershell
npm run test:server
npm run typecheck --prefix client
npm run lint --prefix client
npm run build:client
```

Test JPA dùng H2; kiểm tra SQL Server/MongoDB thật khi thay đổi truy vấn hoặc schema.
Dockerfile backend chạy test, frontend chạy lint/build.

Biến môi trường tham khảo `.env.example`; không commit mật khẩu, file .env, logs,
node_modules, dist hoặc target. Trong container dùng sqlserver:1433/mongodb:27017;
chạy trên máy dùng localhost với cổng đã cấu hình.

Nhánh chức năng theo `feature/<ma-uc>-<ten-chuc-nang>`, bắt đầu từ develop.
Mã UC lấy từ sheet `04_UC`, giữ chữ hoa `UC`, dấu gạch nối và hai chữ số `UC-xx`.
Phần mô tả không dấu, kebab-case. Theo SPEC: `feature/UC-03-dang-nhap`,
`feature/UC-12-tao-don`; chọn mã từ [danh mục UC](USE-CASES.md), không tự đặt mã.
Việc tài liệu hoặc hạ tầng không thuộc UC dùng `docs/<mo-ta>` hoặc `chore/<mo-ta>`.
Commit mô tả thay đổi; có thể dùng `feat(order): UC-12 create order`,
`fix(product): validate price range`, `docs: update setup instructions`.
Pull Request vào develop ghi rõ thay đổi và cách kiểm tra.
PR chức năng có mã UC trong tiêu đề, ví dụ `[UC-12] Create order`, và tham chiếu UC/BR,
API/màn hình ảnh hưởng, cách kiểm tra và kết quả trong mô tả để đối chiếu với sheet.
Không gộp sửa định dạng toàn dự án vào một thay đổi nghiệp vụ nhỏ.

Base hiện cung cấp Product/Activity mẫu. Yêu cầu còn lại theo nghiệp vụ nhóm và hướng dẫn
giảng viên được theo dõi trong [checklist](SBA301-CHECKLIST.md).

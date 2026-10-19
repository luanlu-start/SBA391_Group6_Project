# Quy ước code và đóng góp

Tài liệu thống nhất cách viết backend, frontend và tích hợp chức năng cho nhóm.
Product là module mẫu SQL Server/JPA; Activity là module mẫu MongoDB.

Khi triển khai FPTPost, đọc [thông tin dự án](PROJECT-INFO.md),
[UC được phân công](USE-CASES.md) và [contract API](API.md).
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
| Nhánh chức năng | feature/mã-UC-mã-thành-viên-mô-tả | `feature/UC-03-LuanLH-dang-nhap` |

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
├── enums/
├── dto/
│   ├── request/
│   ├── response/
│   └── internal/     # Kết quả giữa service/controller, không phải response public
├── mapper/
├── exception/
├── security/         # Khi có limiter hoặc xử lý xác thực riêng
├── util/             # Hàm hỗ trợ không giữ trạng thái
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
| Enums | Trạng thái và kiểu nghiệp vụ của module, ví dụ `UserStatus` |
| DTO | Request/response contract; validation đầu vào |
| Mapper | Ánh xạ DTO/model bằng MapStruct |
| Exception | Enum mã lỗi của module triển khai `ErrorCode` |
| Seeder | Nạp mẫu cho module, tách khỏi service nghiệp vụ |

Enum nghiệp vụ đặt ở `modules.<module>.enums`, tách khỏi entity trong `model`.
Enum mã lỗi như `IdentityErrorCode` vẫn ở `exception` cùng cơ chế xử lý lỗi.
Enum lưu trong SQL dùng `@Enumerated(EnumType.STRING)`, tránh lưu ordinal.
Validate request tại API; khai báo validation trên entity cho bất biến dữ liệu
như email không trống/đúng định dạng, cùng constraint SQL phù hợp.

Service dùng một class `@Service` khi chỉ có một implementation.
Chỉ tách interface/implementation khi có nhu cầu thực tế.
Dependency injection qua constructor, có thể dùng `@RequiredArgsConstructor` và field `final`.
Identity dùng `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`
cùng `@RequiredArgsConstructor`; tên dependency đầy đủ như `userRepository`, `otpService`.

API AUTH nhóm trong `AuthenticationController`. Đăng ký gọi `UserService.createUser()`,
service tài khoản gọi `OtpService` để sinh/lưu mã băm và `common.mail.service.EmailService`
để gửi email theo template. Service gửi mail dùng chung không phụ thuộc vào Identity
hoặc mã lỗi nghiệp vụ của module. Xem phần Email và template trong [README](../README.md).
`AuthenticationService` phụ trách xác thực và phiên đăng nhập.
Identity hiện có 5 service: UserService (tài khoản), AuthenticationService
(login/refresh/logout/current user), OtpService (sinh/gửi/xác thực mã), JwtTokenService
(cấp access token), PolicyService (nội dung/phiên bản chính sách).
AuthenticationRateLimiter và LoginAttemptLimiter nằm trong `security`; ContactUtils
nằm trong `util`; AuthenticationResult nằm trong `dto.internal` và không trả trực tiếp qua API.
Chỉ tạo method cho chức năng đã có, không tạo endpoint hoặc service rỗng.

Module khác giao tiếp qua facade/interface hoặc sự kiện, không gọi repository nội bộ của nhau.
Ranh giới theo [kiến trúc FPTPost](LOGISTICS-BASE.md). Nhóm chọn package com.fptpost
với các tầng controller/service/repository/model/dto/mapper, không thêm bốn tầng trùng chức năng.

## 3. DTO, mapping và validation

Request và response tách khỏi entity/document. Dùng tên `ProductRequest`, `ProductResponse`,
`ProductSearchRequest`; tách request tạo/sửa khi hai thao tác có dữ liệu hoặc validation khác nhau.
Module nghiệp vụ chia DTO thành `dto.request` và `dto.response`, dùng class Lombok
`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
Mỗi DTO đặt trong file cùng tên, dùng `@FieldDefaults(level = AccessLevel.PRIVATE)`.
Kiểu dữ liệu con cũng tách thành DTO riêng, ví dụ `PolicyResponse` được dùng trong
`RegistrationPoliciesResponse`, giúp dễ tìm và tái sử dụng.
Identity tham khảo `UserCreationRequest`, `RegisterResponse` và `UserMapper`.
Product/Activity demo giữ DTO hiện tại. Request chứa mật khẩu không được đưa mật khẩu
vào `toString()` hoặc serialize trả về client.

- Request khai báo `@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Digits`... theo dữ liệu.
- Controller dùng `@Valid`; kiểm tra nghiệp vụ cần đọc database nằm trong service.
- Validation cần thông báo riêng dùng `message = "PASSWORD_INVALID"` và khai báo
  `validation.PASSWORD_INVALID` trong messages/errors.properties. Mã nằm trong errors[].code.
  Không ghi câu tiếng Việt trực tiếp trong annotation; constraint mặc định vẫn có mã dự phòng.
  Checkbox bắt buộc dùng cả `@NotNull` và `@AssertTrue`; `@Size` dành cho độ dài chuỗi/collection.
- Giới hạn DTO phải khớp độ dài, precision và scale của database.
- Dùng MapStruct `componentModel = "spring"`, `unmappedTargetPolicy = ERROR`.
- Bỏ qua ID và timestamps trong mapping từ input; các trường này do server quản lý.
- Quy định rõ field bỏ qua, field null để xóa và field null để giữ khi update.

Product mẫu giữ ID/createdAt và giữ status khi không gửi; description có thể xóa.

## 4. REST API, response và mã lỗi

Endpoint theo `/api/v1/<resources>`; dùng GET/POST/PUT/DELETE phù hợp.
ID của entity SQL dùng UUID khi phù hợp; dùng typed UUID trong controller để validate giá trị.

API module trả `ResponseEntity<ApiResponse<T>>` khi thành công; lỗi dùng `ApiProblem` kế thừa Spring `ProblemDetail`.
Danh sách trả `ApiResponse<PageResponse<T>>`, không trả trực tiếp `Page` của Spring hoặc entity.
HTTP status phải khớp trường `status` trong response; `code` thể hiện kết quả nghiệp vụ.

Service ném `AppException(<Module>ErrorCode...)` cho lỗi nghiệp vụ.
Identity dùng chung IdentityException cho lỗi có Retry-After hoặc cần giữ lượt thử sai/
thu hồi phiên khi refresh token bị dùng lại. Chỉ các method login, refresh, verifyOtp
chủ động noRollbackFor với loại này; lỗi database, gửi mail và lỗi ngoài dự kiến vẫn rollback.
Không dùng noRollbackFor cho mọi AppException hoặc mọi Exception.
`GlobalExceptionHandler` xử lý response; không lặp try/catch ở mỗi controller/service
và không trả chi tiết exception nội bộ cho client.

| Code hiện có | Ý nghĩa |
| --- | --- |
| 1000 | Chỉ response thành công |
| SYS_INVALID_REQUEST, SYS_UNAUTHORIZED, SYS_FORBIDDEN | Request, xác thực, quyền truy cập |
| SYS_RESOURCE_NOT_FOUND, SYS_RESOURCE_ALREADY_EXISTS | Không tìm thấy, trùng tài nguyên |
| PRODUCT_NOT_FOUND | Product không tồn tại |
| AUTH_* | Lỗi đăng ký Identity |
| RATE_LIMITED | Quá nhiều request |
| SYS_INTERNAL_ERROR, SYS_UPSTREAM_UNAVAILABLE | Lỗi hệ thống hoặc dịch vụ phụ thuộc |

Module mới thống nhất mã với nhóm và cập nhật tài liệu, tránh trùng mã.
Frontend đọc `code` chữ và `errors` danh sách; `ApiError.fieldErrors` ánh xạ sang form.
Không so sánh câu chữ để quyết định nghiệp vụ. Thêm `CODE.title`, `CODE.detail` trong
`resources/messages/errors.properties` và câu chữ frontend trong `src/i18n/apiErrors.ts`.
Không tự trả ApiResponse cho lỗi, không trả `exception.getMessage()` hoặc rejected value.
Service gửi mã lỗi cho handler dùng chung; chi tiết xem [contract lỗi](API-ERRORS.md).
Thêm mô tả API bằng OpenAPI theo controller mẫu.
Spring Security/JWT và phiên đã có; endpoint mới mặc định cần xác thực.
Kiểm tra role/ownership tại service, giao diện chỉ điều chỉnh menu theo quyền.
Xem [API xác thực và token](API.md); không lưu access/refresh token trong localStorage.

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

Entity JPA thống nhất dùng `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`
và `@AllArgsConstructor`; giữ constructor rỗng cho JPA. Không dùng `@Data` trên entity
để tránh tự sinh toString/equals/hashCode cho quan hệ và dữ liệu nhạy cảm.
Thuộc tính có initializer dùng `@Builder.Default`, ví dụ `roles = new HashSet<>()`
hoặc `status = "ACTIVE"`, để builder giữ giá trị mặc định.
Tạo entity trong service bằng builder khi gán nhiều thuộc tính; ánh xạ DTO vẫn dùng
MapStruct, giá trị do server quyết định như quyền, trạng thái và mật khẩu băm do service gán.
Builder không thay thế validation hoặc ràng buộc database. Mongo document cũng có builder,
nhưng dùng annotation mapping của Spring Data MongoDB.

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
worker retry/idempotency theo eventId như SPEC yêu cầu. Cơ chế này đã có cho UC-46 qua
`common.audit.AuditOutboxService`; UC-60 cung cấp query và retention của cùng collection.
Các module nhạy cảm tiếp theo gọi producer trong transaction nghiệp vụ của owner;
xem [AuditOutboxService](../server/src/main/java/com/fptpost/common/audit/AuditOutboxService.java)
và [schema audit](DATABASE.md#index-và-retention-uc-60-đã-triển-khai), không tạo thêm kho audit riêng.

Không dùng ActivityLog mẫu làm bằng chứng duy nhất cho trạng thái đơn, ví/ký quỹ hoặc đối soát.
Chỉ module ledger được ghi bút toán; ghi lịch sử trạng thái và bút toán liên quan trong
cùng transaction SQL, có kiểm tra quyền và điều kiện. Xem [database](DATABASE.md).

## 7. Code first và seed data

Cấu hình trong một `application.yml`; Hibernate dùng `ddl-auto: update`.
Sửa entity kèm unique/foreign key/check/index cần thiết trong cùng PR; chạy lại backend
để cập nhật schema. Không dùng các file SQL đánh số V1/V2.
Filtered unique index, trigger và quyền bảo vệ ledger cần thiết kế riêng và test
SQL Server khi module tương ứng được tạo; chưa có các bảng ledger trong base.

Seed demo tách khỏi việc tạo schema. Dữ liệu nền bắt buộc nằm trong initializer riêng;
Identity khởi tạo role bằng `IdentityDataInitializer`, không phụ thuộc profile demo.
Thay đổi tên cột/kiểu/nullability có dữ liệu cần kiểm tra theo
[hướng dẫn code first](DATABASE.md).

Mỗi module có seeder riêng nếu cần. Không gom mẫu của mọi module vào service hoặc một AppInit quá lớn.
Seed dùng mã/ID ổn định để kiểm tra bản ghi tồn tại, chỉ thêm mẫu còn thiếu.
Product mẫu dùng JDBC INSERT với UUID cố định để tránh merge ghi đè dữ liệu đã sửa.
Seeder Product đọc `demo/products.json`, không tạo ActivityLog và không chạy với prod.

## 8. Frontend

### Component và bố cục dùng chung

Xem `/ui-kit`, `/ui-kit/list`, `/ui-kit/components` khi chạy `npm run dev:client`.
Đây là mẫu phát triển, không gọi API đơn hàng và không có trong bản production.

- Màu, font, spacing, border/radius lấy từ `src/styles/tokens.css`; không tạo theme
  hoặc bộ input/button riêng trong từng module. CSS component chung ở `styles/ui.css`.
- `MainLayout` dùng cho trang có navbar, `AuthLayout` cho đăng nhập/đăng ký,
  `WorkspaceLayout` cho màn hình làm việc có sidebar. Điều hướng chính được quản lý
  tại layout/navbar và router chung, bổ sung menu khi chức năng đã có.
- Trang có `PageHeader`; nội dung chia `SectionCard`. Form chia `FormSection`
  hoặc `ui-form-grid`: hai cột desktop, một cột mobile, `ui-span-full` cho trường dài.
- Dùng `Input`, `Select`, `Textarea`, `Checkbox` với label và error; các component
  hỗ trợ ref của React Hook Form, tự sinh ID và liên kết thông báo cho screen reader.
  Quy tắc dữ liệu nằm trong schema Zod; lỗi API chuyển qua `setError` hoặc `ApiErrorNotice`.
- Nút chính dùng `Button`, mặc định `type="button"`; nút gửi form phải có `type="submit"`.
  Nút hủy dùng `secondary`, xóa dùng `danger`; dùng `loading` để chặn gửi lặp khi lưu.
- Bảng dùng `DataTable<T>` và `Pagination`. Page API từ 0, giao diện hiển thị từ 1.
  Bộ lọc thay đổi thì về trang 0; bảng cuộn ngang trong khung trên mobile.
- Tách tải/lỗi/rỗng/thành công; không hiển thị dữ liệu cũ hoặc trạng thái rỗng khi API lỗi.
  Dùng `Alert`, `EmptyState`, `LoadingSpinner`; `Badge` nhận nhãn và tone theo nghiệp vụ.
- Dùng `Modal` cho hộp thoại để giữ focus, hỗ trợ Esc và trả focus về nút mở.
  Khi đang lưu, đặt `closeDisabled` và vô hiệu nút hủy để không đóng giữa request.

Ví dụ trường dùng chung với React Hook Form:

```tsx
<Input label="Họ và tên" required
  {...register('fullName')} error={errors.fullName?.message} />
<Button type="submit" loading={isSubmitting} loadingText="Đang lưu…">Lưu</Button>
```

Tham khảo `features/product` cho CRUD gọi API thật; `features/ui-kit` chỉ chứa ví dụ
giao diện. `styles/legacy.css` giữ CSS của trang giới thiệu/demo cũ; module mới dùng
component chung và token, CSS riêng chỉ xử lý bố cục đặc thù.

### Tổ chức chức năng và API

- Page và layout dùng function component/hooks; component dùng lại đặt trong `components`.
- API theo module đặt trong `src/api` và gọi qua `axiosClient`.
- Chức năng có nhiều logic form tách trong `features/<feature>/components`, `hooks`,
  `schemas`. Page chỉ ghép layout/component; schema Zod và React Hook Form kiểm tra input;
  hook gọi service và xử lý loading/lỗi/chuyển trang.
- Auth dùng `services/authService.ts` gọi qua `axiosClient`, dùng chung cho đăng ký,
  chính sách, OTP, login, refresh, logout và khôi phục phiên. Không tạo thêm wrapper API trùng nhiệm vụ.
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

Nhánh bắt đầu từ `develop`, PR vào `develop`. Nhóm bổ sung mã thành viên vào mẫu
nhánh trong SPEC; mã UC lấy từ sheet `04_UC`, giữ đúng `UC-xx`.
Chọn mã từ [danh mục UC](USE-CASES.md), không tự đặt mã.

Mỗi thành viên dùng một tên viết tắt cố định, không dấu, không khoảng trắng và không trùng
thành viên khác. Lư Hữu Luân dùng `LuanLH`; giữ đúng chữ hoa/thường của mã này.
Phần mô tả dùng chữ thường không dấu, kebab-case.

| Công việc | Mẫu nhánh | Ví dụ |
| --- | --- | --- |
| Chức năng có UC | `feature/<ma-uc>-<ma-tv>-<mo-ta>` | `feature/UC-03-LuanLH-dang-nhap` |
| Sửa lỗi có UC | `fix/<ma-uc>-<ma-tv>-<mo-ta>` | `fix/UC-02-LuanLH-otp-het-han` |
| Tài liệu chung | `docs/<ma-tv>-<mo-ta>` | `docs/LuanLH-quy-uoc-git` |
| Base, cấu hình, bảo trì chung | `chore/<ma-tv>-<mo-ta>` | `chore/LuanLH-frontend-ui-base` |

Sửa lỗi dùng chung không thuộc UC có thể dùng `fix/<ma-tv>-<mo-ta>`.
Không gán UC cho công việc chung chỉ để đặt tên nhánh.

Commit theo [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
Scope là module hoặc phần code thay đổi, ví dụ `identity`, `order`, `payment`, `ui`, `git`.
Nhóm thêm UC và mã thành viên vào phần mô tả để nhận biết công việc và người thực hiện:

```text
<type>(<scope>): [UC-xx] [<ma-tv>] <mo-ta-thay-doi>
```

Commit không thuộc UC bỏ `[UC-xx]`. Mô tả ngắn bằng tiếng Anh, nói rõ thay đổi.

| Type | Khi dùng | Ví dụ |
| --- | --- | --- |
| `feat` | Thêm chức năng hoặc hành vi mới | `feat(identity): [UC-03] [LuanLH] add login form` |
| `fix` | Sửa lỗi | `fix(identity): [UC-02] [LuanLH] reject expired OTP` |
| `refactor` | Đổi cấu trúc code, giữ hành vi | `refactor(ui): [LuanLH] extract shared form fields` |
| `docs` | Sửa tài liệu | `docs(git): [LuanLH] define branch conventions` |
| `test` | Thêm/sửa test | `test(identity): [UC-02] [LuanLH] cover OTP retry limit` |
| `chore` | Bảo trì, cấu hình công cụ | `chore(dev): [LuanLH] update local environment examples` |
| `ci` | Sửa workflow CI | `ci(frontend): [LuanLH] run lint before build` |
| `style` | Định dạng code, không đổi hành vi | `style(identity): [LuanLH] format imports` |

Thay đổi giao diện có hành vi hoặc bố cục mới dùng `feat`/`fix` theo mục đích;
`style` không dùng để chỉ mọi thay đổi CSS. Type commit phản ánh thay đổi trong commit,
không bắt buộc trùng prefix nhánh. Có thay đổi phá vỡ contract thì thêm `!` và giải thích
`BREAKING CHANGE` trong commit body/footer.
Mã thành viên trong tên nhánh/commit bổ sung khả năng nhận diện;
tác giả thực tế vẫn được Git lưu trong author name/email.

PR chức năng dùng `[UC-03] [LuanLH] Đăng nhập`; PR công việc chung dùng
`[LuanLH] Giao diện dùng chung`. Mô tả PR tham chiếu UC/BR, API/màn hình ảnh hưởng,
cách kiểm tra và kết quả để đối chiếu với sheet.
Không gộp sửa định dạng toàn dự án vào một thay đổi nghiệp vụ nhỏ.

Base hiện cung cấp Product/Activity mẫu và UC-01 đăng ký Identity. Yêu cầu còn lại theo nghiệp vụ nhóm và hướng dẫn
giảng viên được theo dõi trong [checklist](SBA301-CHECKLIST.md).

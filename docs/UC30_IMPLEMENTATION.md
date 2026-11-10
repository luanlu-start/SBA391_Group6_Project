# UC30 — Giao thất bại và hoàn hàng

## Nguồn và phạm vi

Nguồn chính: [workbook BR/UC v1.0](sources/fptpost-br-uc-v1.0.xlsx), dòng UC-30 (sheet5, row34), BR-F-055, BR-R-044, PRM-025, PRM-052 và các trạng thái/chuyển trạng thái của sheet9. API-078 và API-079 trong [API.md](API.md).

- Actor chính: Courier được phân công; Sender xác nhận đã nhận lại hàng.
- Điều kiện: đơn đang giao (`IN_DELIVERY` trong source hiện tại), không liên lạc được người nhận, đủ bằng chứng liên hệ.
- BR-R-044: 3 lần gọi trong 30 phút; phí hoàn bằng 50% phí giao theo mặc định. Đọc giá trị hiện hành từ `ParameterService.getSnapshot()` với `CONTACT_TRIES`, `RETURN_FEE`, không hardcode hai tham số này.
- Main flow: ghi nhận liên hệ → ảnh bằng chứng → ghi giao thất bại và bắt đầu hoàn → Sender xác nhận bằng mã → hàng được ghi nhận đã trả.
- Alternative A1: người nhận liên lạc lại khi đang hoàn → Courier tiếp tục giao. Không được tiếp tục giao hoặc xác nhận lại sau khi đã trả hàng.
- Chỉ có lý do `RECIPIENT_UNREACHABLE` được triển khai vì đây là lý do có quy tắc xác định trong UC30. Không tự thêm lỗi do Courier, hỏng hàng hoặc các quy tắc bồi thường.
- Moderator/Admin chỉ đọc lịch sử/bằng chứng; UC30 không mô tả phán quyết của Moderator nên không có API xử lý thay Sender/Courier.
- UC29 là ghi nhận sự cố, không tự chuyển trạng thái. UC30 dùng hồ sơ hoàn hàng riêng; không đổi entity, API hoặc nghiệp vụ UC29. Tái sử dụng `ImageValidator`, `PrivateFileStorage`, `IdentityUserAccess`, `NotificationPublisher` và `OrderRepository`.

## Giới hạn tài chính — cần chốt trước khi coi UC30 hoàn chỉnh toàn bộ

BR-R-044 đã quy định Courier được đủ phí giao và Sender chịu thêm phí hoàn; sheet9 còn mô tả giữ thêm tiền và tất toán. Tuy nhiên Payment hiện chỉ có giải ngân ký quỹ/cancel-refund thông thường, chưa có hợp đồng settlement UC30, xử lý thiếu số dư, commission phí hoàn, tiền đang frozen/disputed hoặc hủy khoản giữ khi tiếp tục giao.

Theo yêu cầu không tự sửa Payment/Escrow, bản này **chỉ lưu dự kiến phí**, không gọi ledger/wallet/refund/escrow và không tự thu/giải ngân tiền. `financialSettlementPending=true` thể hiện rõ việc chưa tất toán. `RETURNED` ở bản triển khai này xác nhận **hàng đã được người gửi nhận lại**, chưa cam kết định nghĩa "đã tất toán" của trạng thái trong tài liệu dự thảo. Chưa nên nghiệm thu trọn vẹn phần tài chính UC30.

Phí được chốt trên hồ sơ tại thời điểm báo thất bại: dùng `priceAgreed`, fallback `priceSuggested` khi chưa có giá thỏa thuận; làm tròn VND `HALF_UP`; lưu phí giao, tỷ lệ, phí hoàn và version `RETURN_FEE`. Nếu thiếu giá hợp lệ/tham số thì trả 503, không dùng giá 0 hoặc tỷ lệ mặc định âm thầm. Hồ sơ đã resume không có khoản phí hoàn cần thu.

## Thiết kế trạng thái và lịch sử

```text
IN_DELIVERY --bằng chứng hợp lệ--> DELIVERY_FAILED --tự động cùng transaction--> RETURNING
RETURNING --Sender xác nhận mã + đã nhận hàng--> RETURNED
RETURNING --Courier: người nhận liên lạc lại--> IN_DELIVERY
```

`DELIVERY_FAILED` là mốc lịch sử; API báo thất bại lưu cả hai mốc trong cùng transaction và trạng thái cuối của Order là `RETURNING`. Không có khoảng thời gian trung gian để request khác sửa đơn.

Mỗi lần báo thất bại tạo một `DeliveryReturnCase`. Resume kết thúc hồ sơ cũ (`RESUMED`), không xóa ảnh/lịch sử; lần thất bại tiếp theo tạo hồ sơ mới và cần bằng chứng liên hệ mới. Thêm `DELIVERY_FAILED`, `RETURNING`, `RETURNED` vào enum hiện có, giữ nguyên các constant khác.

Ba bảng mới:

- `delivery_contact_attempts`: order, courier, UUID request chống gửi trùng, ghi chú, thời điểm server, hồ sơ đã sử dụng bằng chứng.
- `delivery_return_cases`: lý do, mô tả, Courier, ảnh riêng, trạng thái hồ sơ, snapshot phí, thời điểm, hash/salt/expiry/bộ đếm mã.
- `delivery_return_history`: order, hồ sơ, actor, action, trạng thái trước/sau, ghi chú, thời điểm.

Các timestamp dùng UTC; mô tả tiếng Việt dùng `@Nationalized`; FK liên kết hồ sơ/Order và liên hệ/hồ sơ. Lịch sử API giới hạn 50 mốc mới nhất; không có API sửa/xóa bằng chứng hoặc lịch sử.

Chỉ thêm kiểm tra UC30 vào Batch: đơn đang/đã hoàn không xuất hiện trong eligible và không được submit trực tiếp để optimize/create batch. Bộ đếm đơn đang hoạt động của chuyến bỏ qua `RETURNED`. Không thay quy tắc của các trạng thái cũ.

## Bằng chứng liên hệ và giả định cần xác nhận

- Courier ghi nhận **sau mỗi cuộc gọi không thành công**, timestamp do server tạo, không nhận giờ do client khai báo.
- Khi báo thất bại, server yêu cầu đủ cuộc gọi **chưa dùng** trong cửa sổ thời gian gần nhất theo `CONTACT_TRIES`; các cuộc gọi đã dùng không được tái sử dụng sau resume.
- Hiểu "3 lần trong 30 phút" là đủ số lần trong cửa sổ gần nhất, không tự thêm điều kiện phải đợi hết 30 phút. Nếu nhóm muốn chờ tối thiểu 30 phút hoặc giãn cách từng lần, cần xác nhận riêng.
- Một ảnh bằng chứng call log/tin nhắn bắt buộc cho báo thất bại; JPG/PNG/WebP tối đa 5 MB theo validator chung. Mô tả bắt buộc tối đa 1.000 ký tự; ghi chú cuộc gọi tối đa 500.
- Đây là nhật ký khai báo trong ứng dụng kèm ảnh. Không có tích hợp nhà mạng/telephony để chứng thực cuộc gọi đã diễn ra hoặc đọc nội dung ảnh. Moderator có thể xem bằng chứng, chưa có workflow duyệt.

## Mã xác nhận nhận lại hàng

Sender của đơn tự tạo mã một lần tại `/return/code`, xem mã/QR trên trang hoàn hàng và chỉ xác nhận sau khi thực sự nhận hàng. Chọn cách này theo actor SENDER của API-079 và "mã của người gửi"; chưa tích hợp SMS hoặc quét chéo thiết bị để chứng minh sự có mặt của hai bên.

- Mã ngẫu nhiên 6 chữ số, hiệu lực 10 phút, cooldown tạo mới 60 giây, khóa mã sau 5 lần nhập sai. Đây là các giá trị tối thiểu được chọn vì UC30 chưa định nghĩa TTL/lockout.
- Chỉ Sender của đơn được tạo/nhập mã; Courier và Moderator không được thực hiện thay.
- Chỉ lưu SHA-256 với salt; response thường/lịch sử/log không chứa mã/hash. Response mã `no-store`, code không lưu ở localStorage.
- Mã mới vô hiệu mã cũ. Xác nhận thành công hoặc resume xóa hash/expiry. Bắt buộc `received=true`.
- Hash mã ngắn không thay thế quyền truy cập database: vẫn cần bảo vệ database. Chưa có kênh OTP độc lập hoặc xác minh đồng hiện diện.

## API (prefix `/api/v1/orders/{orderId}`)

| Method | Path | Quyền / input | Kết quả |
| --- | --- | --- | --- |
| GET | `/return` | Sender/Courier của đơn, Moderator/Admin | Trạng thái, quyền thao tác, cuộc gọi còn hiệu lực, hồ sơ mới nhất, 50 mốc lịch sử |
| POST | `/delivery-contact-attempts` | Courier phân công, `IN_DELIVERY`; JSON `requestId`, `note` | Ghi nhận cuộc gọi; replay UUID không tăng số lần |
| POST | `/delivery-failed` | Courier phân công; multipart `request` JSON và `photo` | 201, chuyển `RETURNING` |
| POST | `/return/resume` | Courier phân công, `RETURNING`; JSON `description` | Tiếp tục `IN_DELIVERY` |
| POST | `/return/code` | Sender của đơn, `RETURNING` | Mã/expiry, `no-store` |
| POST | `/return/confirm` | Sender của đơn; JSON `code`, `received=true` | `RETURNED`, ghi thời điểm kết thúc vật lý |
| GET | `/return/{caseId}/evidence` | Quyền đọc theo đơn; case phải thuộc đúng đơn | Bytes ảnh riêng, `no-store`, `nosniff` |

Ví dụ `request` part của `/delivery-failed`:

```json
{"reason":"RECIPIENT_UNREACHABLE","description":"Không liên lạc được người nhận sau các cuộc gọi đã ghi nhận."}
```

Envelope thành công dùng `ApiResponse`. `data` của workflow gồm `orderId`, `orderStatus`, `canReport`, `canConfirm`, `canResume`, `requiredCalls`, `contactWindowMinutes`, `contacts`, `latestCase`, `history`. `latestCase` có trạng thái hồ sơ, phí dự kiến và `financialSettlementPending`; không trả storage key, hash hoặc salt.

Lỗi theo `GlobalExceptionHandler`/`application/problem+json`: 400 validation/bằng chứng/mã; 401 xác thực từ filter; 403 sai actor/quyền; 404 không có đơn/hồ sơ; 409 sai trạng thái/đã xử lý; 429 mã bị khóa/cooldown; 503 thiếu chính sách/phí. Lỗi ảnh/storage theo mã chung của dự án.

## Transaction, duplicate và thông báo

Mọi ghi giữ pessimistic lock trên **Order trước**, sau đó mới đọc/sửa hồ sơ. Hai báo cáo hoặc xác nhận đồng thời không tạo lần chuyển trạng thái thứ hai. Contact có unique `(order_id, request_id)` và replay được trả lại an toàn.

Hồ sơ, tiêu thụ bằng chứng, Order, lịch sử và notification outbox cùng transaction. Lỗi outbox rollback toàn bộ; file riêng dùng rollback cleanup của storage chung. Chỉ `ReturnCodeRejectedException` được `noRollbackFor` để giữ bộ đếm mã sai, không chuyển trạng thái hoặc tạo thông báo trong nhánh này.

Gửi notification ORDER cho Sender/Courier khi bắt đầu hoàn, tiếp tục giao và xác nhận nhận lại. Worker có sẵn giao sau commit. Link `/orders/{id}/return` dùng chung cho hai bên; tài khoản đã xóa không nhận thông báo. Không gửi mã nhận lại hàng qua notification.

## Frontend

- Courier Assigned Orders thêm link UC30, giữ nguyên chat/pickup/incident.
- Sender Order Detail thêm link xem tiến trình/xác nhận nhận lại hàng.
- Trang `/orders/:id/return` dùng Axios client, PageHeader, SectionCard, Button, Alert và ApiErrorNotice có sẵn.
- Nút hành động dựa trên quyền do Backend trả về; Backend vẫn kiểm tra lại role/ownership/status.
- Có form liên hệ, chọn lý do, mô tả/ảnh, resume, QR/mã Sender, checkbox nhận hàng, snapshot phí, lịch sử và ảnh riêng.
- Hiển thị rõ chưa thu phí/hoàn tiền/giải ngân. Nội dung người dùng render dạng text, không inject HTML.

## Kiểm thử đã chạy

Để không cập nhật `target/classes` mà DevTools của Backend thật có thể đang theo dõi, Maven chạy bằng bản sao POM tại `server/target/uc30-validation/pom.xml`, output `server/target/uc30-validation/build`. Source/dependency/plugin lấy từ POM gốc; không sửa POM gốc. Build tạm không thuộc phần commit.

```text
# Trong server; POM kiểm thử riêng đã được tạo ở máy này
mvn -B -f target/uc30-validation/pom.xml compile -Dtest=FailedDeliveryServiceTest,FailedDeliveryControllerTest,FailedDeliveryTransactionTest,ReturnOrderEligibilityTest test
# Trong client
npm run typecheck
```

Ở môi trường test tách biệt, không có Backend thật theo dõi target/classes, có thể dùng lệnh Maven thông thường với cùng danh sách `-Dtest`.

Kết quả cuối: Java 21 compile 678 source files PASS; tổng **62/62 test UC30 PASS**, không skip; TypeScript PASS. Các test transaction dùng Hibernate/JPA và URL H2 ngẫu nhiên trong bộ nhớ, không Boot/seeders/scheduler hoặc SQL Server thật. Bộ test bao phủ wrong role/Courier/Sender, trạng thái, validation, duplicate, contact window, fee snapshot, ảnh, history/outbox, code/cooldown/expiry/lockout, resume, rollback, concurrent reports và batch/trip integration. Không chạy lại test UC24/UC25/UC29.

## File tạo mới và chỉnh sửa

Tạo mới dưới `server/src/main/java/com/fptpost/modules/deliveryreturn/`:

- `controller/FailedDeliveryController.java`
- `service/FailedDeliveryService.java`
- `dto/ReturnRequests.java`, `dto/ReturnResponse.java`
- `exception/DeliveryReturnErrorCode.java`, `exception/ReturnCodeRejectedException.java`
- `model/FailureReason.java`, `model/ReturnState.java`, `model/DeliveryReturnCase.java`, `model/DeliveryContactAttempt.java`, `model/DeliveryReturnHistory.java`
- `repository/DeliveryReturnRepository.java`, `repository/DeliveryContactAttemptRepository.java`, `repository/DeliveryReturnHistoryRepository.java`

Tạo mới dưới `server/src/test/java/com/fptpost/modules/deliveryreturn/`:

- `FailedDeliveryServiceTest.java`, `FailedDeliveryControllerTest.java`, `FailedDeliveryTransactionTest.java`, `ReturnOrderEligibilityTest.java`

Tạo mới Frontend/tài liệu:

- `client/src/api/deliveryReturnApi.ts`
- `client/src/pages/DeliveryReturnPage.tsx`
- `docs/UC30_IMPLEMENTATION.md`

Chỉnh sửa hỗ trợ trực tiếp UC30:

- `server/src/main/java/com/fptpost/modules/order/model/OrderStatus.java`: ba trạng thái mới.
- `server/src/main/java/com/fptpost/modules/order/repository/OrderRepository.java`: không tính RETURNED là đơn đang hoạt động của chuyến.
- `server/src/main/java/com/fptpost/modules/batch/service/BatchService.java`: chặn ba trạng thái UC30 vào batch giao hàng.
- `server/src/main/java/com/fptpost/config/SecurityConfig.java`: xác thực các path UC30 trước matcher Order public cũ.
- `server/src/main/resources/messages/errors.properties`: thông báo lỗi UC30.
- `client/src/types/order.ts`: bổ sung DELIVERY_FAILED; RETURNING/RETURNED đã tồn tại.
- `client/src/pages/CourierAssignedOrdersPage.tsx`, `client/src/pages/OrderDetailPage.tsx`, `client/src/routes/AppRoutes.tsx`: link và route UC30.

## Checklist kiểm thử thủ công trên môi trường test

1. Dùng database SQL Server **test** và thư mục ảnh riêng. Kiểm tra ba bảng JPA mới được tạo theo quy trình schema update của dự án. Nếu Order có CHECK constraint enum cũ, cần migration phù hợp trên DB test trước khi dùng các giá trị UC30; chưa chạy DDL trên DB thật.
2. Đơn test `IN_DELIVERY`, phân công Courier A; Sender là B. Mở link UC30 từ hai giao diện.
3. Courier A ghi đủ cuộc gọi theo CONTACT_TRIES, tải ảnh call log/tin nhắn, gửi báo thất bại. Kiểm tra trạng thái RETURNING, hai mốc lịch sử và thông báo cho hai bên.
4. Thử Courier khác, Sender/Moderator báo thất bại; thiếu ảnh, thiếu/trống mô tả, thiếu cuộc gọi hoặc cuộc gọi quá cửa sổ: phải bị từ chối. Gửi cùng UUID contact hai lần không tăng count.
5. Sender B tạo mã, nhập sai/hết hạn hoặc không đánh dấu đã nhận hàng: không chuyển trạng thái; 5 lần sai bị khóa. Tạo mới theo cooldown, xác nhận bằng mã mới: RETURNED, không xác nhận lại được.
6. Với đơn test khác đang RETURNING, Courier A chọn người nhận liên lạc lại: IN_DELIVERY; mã cũ hết hiệu lực; báo thất bại lần nữa phải có cuộc gọi mới. Ảnh/lịch sử lần cũ vẫn xem được.
7. Kiểm tra notification worker test, tải lại trang để xác minh dữ liệu lưu; Moderator/Admin đọc được nhưng không có nút thao tác thay hai bên; người không thuộc đơn không đọc được ảnh/lịch sử.
8. Kiểm tra ví, ledger và escrow **không thay đổi** do UC30. Các phí chỉ là dự kiến. Không thao tác giải ngân thủ công để mô phỏng tất toán khi chưa chốt hợp đồng tài chính.
9. Kiểm tra đơn đang/đã hoàn không vào eligible batch; đơn RETURNED không giữ chuyến ở trạng thái còn đơn đang hoạt động.

Chưa chạy trình duyệt với SQL Server test hoặc kênh notification thật; chưa có settlement UC30, xác minh cuộc gọi từ nhà mạng, OTP/SMS hoặc QR quét chéo thiết bị. Thống kê tài chính/hiệu suất của module Payment hiện có chưa được mở rộng để phân loại RETURNED, vì phạm vi không cho tự sửa Payment; không dùng các chỉ số này để nghiệm thu tất toán UC30.

Trạng thái bàn giao: phần vận hành đã qua kiểm thử tự động, sẵn sàng review và commit với phạm vi được ghi rõ. Chưa nên tuyên bố UC30 tài chính hoàn chỉnh trước khi chốt/tích hợp settlement và smoke test trên môi trường test. Không commit/push/PR hoặc sửa dữ liệu thật trong phiên triển khai này.

## Chẩn đoán và xử lý HTTP 404 trang hoàn hàng

Đã xác minh trên Backend localhost:8080:

- `GET /api/v1/orders/5` trả 200; đơn #5 có status `IN_DELIVERY`, Sender ID 1, Courier ID 2.
- Lúc phát hiện lỗi, `GET /api/v1/orders/5/return` trả 404 và OpenAPI `/api-docs` không có mapping UC30.
- Source `FailedDeliveryController` đã có mapping đúng; `deliveryReturnApi.detail` gọi đúng path/method.
- Controller chỉ có trong `target/uc30-validation/build/classes`, chưa có trong `target/classes`. Build riêng đã kiểm thử được code nhưng không triển khai vào Backend live.
- Ba bảng UC30 cũng chưa có trong database. Đây là bản Backend đang chạy cũ, không phải lỗi `latestCase` vắng mặt hoặc sai path Frontend.

Sau khi compile POM gốc vào `target/classes`, Backend DevTools đã nạp lại phiên bản mới. OpenAPI hiện đăng ký đủ bảy endpoint UC30; Hibernate tạo ba bảng mới. Không gửi POST/PATCH/PUT/DELETE cho đơn #5. Kiểm tra chỉ đọc cho thấy checksum bản ghi Order #5 trước/sau reload không đổi và `delivery_return_cases WHERE order_id=5` vẫn có 0 bản ghi.

`GET /api/v1/orders/5/return` không kèm xác thực hiện trả **401**, đúng với matcher mới, thay cho 404. Không đọc JWT hoặc giả mạo quyền để lấy 200 trên tài khoản thật. Chưa xác minh được tài khoản đang đăng nhập trong trình duyệt; quyền đọc theo service dành cho Sender 1, Courier 2 hoặc Moderator/Admin. Tài khoản không thuộc đơn phải nhận 403, không biến lỗi này thành lịch sử rỗng.

Chưa có hồ sơ là trạng thái hợp lệ: service trả 200 với `latestCase=null`, `contacts=[]`, `history=[]`; Courier được phân công có `canReport=true` khi đơn `IN_DELIVERY`. Frontend hiện đã hiển thị "Chưa có yêu cầu hoàn hàng" và form báo thất bại tương ứng, không cần sửa UI để che 404.

Đã bổ sung ba regression test cho GET đúng `/orders/5/return` và quyền của Sender/Courier trước khi tồn tại hồ sơ. Sau bổ sung: Maven compile PASS; **65/65 test UC30 PASS** (37 service, 22 controller, 3 transaction, 3 eligibility); `npm run typecheck` PASS. Không chạy lại UC24/25/29.

Nếu dùng build kiểm thử riêng trong các lượt sau, cần nhớ đó không phải deploy. Để kiểm tra giao diện với Backend live, phải nạp build mới và chuẩn bị schema trên môi trường được phép trước. Với đơn #5 hiện tại, chỉ reload trang bằng tài khoản hợp lệ để kiểm tra GET; không tạo cuộc gọi/báo thất bại/xác nhận nhận lại trên đơn thật nếu chưa được cho phép.

# UC29 — Báo cáo sự cố giao hàng

## Phạm vi và nguồn nghiệp vụ

- `docs/sources/fptpost-br-uc-v1.0.xlsx`: sheet UC, dòng UC-29; BR-F-054; BR-F-110.
- `docs/API.md`: API-076 `POST /orders/{id}/incidents`.
- Courier được phân công báo cáo khi đơn đã lấy hàng/đang giao. Trong enum hiện tại, trạng thái tương ứng là `IN_DELIVERY`.
- Bốn loại sự cố theo BR-F-054: kẹt xe (`TRAFFIC_JAM`), hư hỏng hàng (`DAMAGED_GOODS`), thay đổi thời gian (`SCHEDULE_CHANGE`), không liên lạc được người nhận (`RECIPIENT_UNREACHABLE`).
- Lưu sự cố vào lịch sử theo đơn, thông báo Sender và các Moderator đang hoạt động. Không thay đổi `OrderStatus`, phí, ký quỹ hoặc logic giao hàng.
- BR-F-110 xếp SOS vào Pha 2/Should, dù danh mục API ghi API-077 Pha 1. Bản triển khai này theo ưu tiên BR, chỉ triển khai báo cáo sự cố Pha 1; chưa có SOS/vị trí/liên hệ khẩn cấp.

## Lựa chọn tối thiểu cần nhóm xác nhận

Tài liệu chưa chốt hợp đồng DTO, độ dài và số ảnh. Triển khai dùng mô tả bắt buộc, không chỉ chứa khoảng trắng, tối đa 1.000 ký tự; tối đa một ảnh JPG/PNG/WebP 5 MB, tùy chọn cho mọi loại sự cố. Không bắt buộc ảnh hư hỏng vì BR-F-054 ghi "kèm ảnh nếu có". Được tạo nhiều báo cáo cho cùng đơn; không tự gộp hoặc tự chuyển trạng thái.

Chưa có hạ tầng timeline giao hàng tổng quát. Bảng `delivery_incidents` là lịch sử sự cố bất biến theo thời điểm tạo; chưa ghép với sự kiện GPS/giao thất bại của UC khác. Lịch sử phân trang được xem bởi Sender, Courier được phân công hiện tại và Moderator/Admin. Không có API sửa/xóa/xử lý sự cố trong phạm vi này.

## Luồng

1. Courier mở **Đơn hàng được phân công**; đơn `IN_DELIVERY` có nút **Báo cáo sự cố**, trạng thái khác có **Lịch sử sự cố**.
2. Chọn loại, nhập mô tả, chọn ảnh tùy ý và gửi multipart.
3. JWT subject UUID được tra thành User đang hoạt động bằng `IdentityUserAccess`. Controller kiểm tra role, service xác nhận role Courier và `order.courierId`.
4. Khóa dòng Order khi ghi để trạng thái/phân công không thay đổi giữa lúc kiểm tra và lưu. Chỉ chấp nhận `IN_DELIVERY`.
5. Kiểm tra ảnh bằng `ImageValidator` có sẵn và lưu `PrivateFileStorage`. Ảnh không có URL công khai; file được dọn nếu transaction rollback.
6. Lưu incident và notification outbox trong cùng transaction. Notification worker của dự án giao thông báo sau commit theo cấu hình sẵn có. Không gọi dịch vụ ngoài trong transaction, không gửi SMS/email trực tiếp từ UC29.
7. Trả 201; form thông báo thành công và cập nhật lịch sử. Sender/Moderator mở link `/orders/{id}/incidents` trong thông báo để xem **Lịch sử sự cố** và ảnh. Sender cũng có nút lịch sử tại trang chi tiết đơn.

## API (prefix `/api/v1`)

### `POST /orders/{orderId}/incidents`

Role `COURIER`, phải là Courier được phân công, đơn `IN_DELIVERY`.

Content-Type `multipart/form-data`:

- `request`: part `application/json`, ví dụ `{"type":"TRAFFIC_JAM","description":"Kẹt xe tại điểm giao, dự kiến trễ 20 phút."}`.
- `photo`: file ảnh tùy chọn.

201, envelope `ApiResponse`:

```json
{
  "success": true,
  "status": 201,
  "code": 1000,
  "message": "Đã ghi nhận sự cố giao hàng",
  "data": {
    "id": 1,
    "orderId": 290,
    "reporterId": 29,
    "type": "TRAFFIC_JAM",
    "description": "Kẹt xe tại điểm giao, dự kiến trễ 20 phút.",
    "hasEvidence": false,
    "createdAt": "2026-11-11T00:00:00Z"
  }
}
```

### `GET /orders/{orderId}/incidents?page=0&size=20`

Trả `ApiResponse<PageResponse<IncidentResponse>>`; sort `createdAt DESC, id DESC`. Page không âm, size 1–50. Lịch sử tiếp tục đọc được sau khi đơn hoàn tất.

### `GET /orders/{orderId}/incidents/{incidentId}/evidence`

Trả bytes với media type ảnh, `Cache-Control: no-store` và `nosniff`; kiểm tra quyền theo đơn và incident phải thuộc đúng đơn. Không trả khóa lưu trữ trong response.

### Lỗi

Tái sử dụng `GlobalExceptionHandler` và `application/problem+json` như dự án hiện tại:

- 400: thiếu/sai loại, mô tả trống/quá dài, JSON sai, thiếu request part, page/size sai.
- 401: JWT subject không hợp lệ hoặc người dùng không còn hợp lệ; filter xác thực áp dụng theo cấu hình dự án.
- 403: sai role hoặc không thuộc đơn (`INCIDENT_ACCESS_DENIED`).
- 404: đơn không tồn tại (`ORDER_NOT_FOUND`), incident/ảnh không tồn tại (`INCIDENT_NOT_FOUND`).
- 409: chưa lấy hàng/đã kết thúc/không đang giao (`INCIDENT_INVALID_STATUS`).
- 415: POST không dùng multipart.
- Lỗi ảnh/storage theo `FileErrorCode` sẵn có. Notification outbox lỗi thì transaction không ghi nhận thành công một phần.

## Database và vận hành

Entity JPA `DeliveryIncident` tạo bảng `delivery_incidents`, có FK `order_id`, reporter, type, mô tả Unicode, thời gian UTC và khóa ảnh riêng tùy chọn. Index `(order_id, created_at)`. Dự án đang dùng Hibernate schema update (xem `DATABASE.md`), không có migration runner riêng.

Không chạy ứng dụng hoặc DDL trên database thật trong quá trình triển khai. Trước demo, chỉ khởi động ứng dụng với database test để Hibernate tạo bảng mới và kiểm tra lưu/đọc lại. Dùng thư mục file riêng của môi trường test. Cần bật notification worker và có tài khoản Moderator hoạt động để kiểm tra giao thông báo thực tế. Nếu không có Moderator, không có người nhận staff; báo cáo vẫn lưu và Sender vẫn được thông báo.

## Kiểm thử tự động

```text
# Trong server
mvn -B compile
mvn -B -Dtest=DeliveryIncidentServiceTest,DeliveryIncidentControllerTest test
# Trong client
npm run typecheck
```

Hai lớp test chỉ dùng mock; MVC dùng validation thật, exception advice và method security thật, JWT giả của test. Không test chữ ký bearer/filter chain, không kết nối DB, không ghi file hoặc gửi thông báo thật. Test bao phủ 4 loại sự cố, tất cả trạng thái bị cấm, sai role, sai phân công, đơn mất, thiếu/trống/dài mô tả, ảnh bị từ chối, ảnh tùy chọn, thông báo các bên, không đổi trạng thái, phân trang, quyền lịch sử/ảnh, binding JWT và multipart.

Kết quả thực chạy:

| Kiểm tra | Kết quả |
| --- | --- |
| Maven compile, Java 21, 664 source files | PASS |
| DeliveryIncidentServiceTest | 36/36 PASS |
| DeliveryIncidentControllerTest | 17/17 PASS |
| Tổng test UC29 | 53/53 PASS, không skip, BUILD SUCCESS |
| npm run typecheck | PASS, exit code 0 |

Maven còn cảnh báo deprecated `ensureIndex` ở module chat/notification và unchecked ở test có sẵn; không thay đổi những phần đó. Test anonymous trong standalone MockMvc dùng `AnonymousAuthenticationToken` như anonymous filter của Spring; HTTP filter thật yêu cầu xác thực riêng cho `/orders/*/incidents` và các tài nguyên con.

## Danh sách file triển khai

Tạo mới:

- `server/src/main/java/com/fptpost/modules/incident/model/IncidentType.java`
- `server/src/main/java/com/fptpost/modules/incident/model/DeliveryIncident.java`
- `server/src/main/java/com/fptpost/modules/incident/dto/IncidentRequest.java`
- `server/src/main/java/com/fptpost/modules/incident/dto/IncidentResponse.java`
- `server/src/main/java/com/fptpost/modules/incident/repository/DeliveryIncidentRepository.java`
- `server/src/main/java/com/fptpost/modules/incident/repository/IncidentRecipientRepository.java`
- `server/src/main/java/com/fptpost/modules/incident/exception/IncidentErrorCode.java`
- `server/src/main/java/com/fptpost/modules/incident/service/DeliveryIncidentService.java`
- `server/src/main/java/com/fptpost/modules/incident/controller/DeliveryIncidentController.java`
- `server/src/test/java/com/fptpost/modules/incident/DeliveryIncidentServiceTest.java`
- `server/src/test/java/com/fptpost/modules/incident/DeliveryIncidentControllerTest.java`
- `client/src/api/incidentApi.ts`
- `client/src/components/order/DeliveryIncidentPanel.tsx`
- `client/src/pages/DeliveryIncidentsPage.tsx`
- `docs/UC29_IMPLEMENTATION.md`

Chỉnh sửa để nối UC29 vào cấu trúc hiện có:

- `server/src/main/java/com/fptpost/config/SecurityConfig.java`: chỉ thêm matcher xác thực đường dẫn incident trước matcher public orders hiện có.
- `server/src/main/resources/messages/errors.properties`: thêm thông báo lỗi UC29 bằng tiếng Việt.
- `client/src/pages/CourierAssignedOrdersPage.tsx`: thêm nút/form/lịch sử sự cố; không đổi phần xác nhận lấy hàng hoặc chat.
- `client/src/pages/OrderDetailPage.tsx`: thêm nút đọc lịch sử cho người đang đăng nhập, quyền cuối cùng do API kiểm tra.
- `client/src/routes/AppRoutes.tsx`: thêm route lịch sử UC29 cho Sender/Courier/Moderator/Admin.

## Checklist thủ công trên môi trường test

1. Tạo đơn test đã `IN_DELIVERY` và phân công cho Courier A; đăng nhập A, mở danh sách phân công, báo cáo từng loại với/không có ảnh.
2. Kiểm tra thông báo thành công, lịch sử mới nhất ở đầu, mô tả tiếng Việt xuống dòng và ảnh đọc được; tải lại trang để kiểm tra dữ liệu đã lưu.
3. Đăng nhập Sender của đơn và Moderator: mở thông báo, đọc lịch sử và ảnh. Kiểm tra tài khoản không thuộc đơn không đọc được bằng API.
4. Đăng nhập Courier B: gọi POST cho đơn A phải 403. Sender/Moderator không được POST. JWT hết hạn/thiếu token không ghi được dữ liệu.
5. Đơn `SHIPPER_ASSIGNED`, `COMPLETED`, `CANCELLED`: không có form báo cáo, POST trực tiếp bị 409 khi đúng Courier. Order ID không tồn tại trả 404.
6. Bỏ loại/mô tả, nhập toàn khoảng trắng hoặc >1.000 ký tự, gửi sai enum qua API, chọn file không phải ảnh hoặc >5 MB: báo lỗi, không tạo incident.
7. Kiểm tra đơn không đổi trạng thái, tiền/ký quỹ không thay đổi; dùng >10 incident test để kiểm tra nút chuyển trang.
8. Ngắt kết nối API hoặc làm storage test thất bại: form hiện lỗi, giữ nội dung để thử lại. Không chạy các thao tác này trên đơn thật.

Chưa chạy kiểm thử trình duyệt, lưu trữ SQL thật hoặc giao notification qua kênh thật; đây là các bước kiểm tra tích hợp trên môi trường test trước demo.

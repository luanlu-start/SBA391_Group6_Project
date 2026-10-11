# Contract lỗi API

Lỗi trả Problem Details qua Content-Type `application/problem+json`.
`ApiProblem` kế thừa Spring `ProblemDetail`, thêm code nghiệp vụ, correlationId
và errors theo field. Áp dụng cho Identity, Product, Activity và các lỗi Spring MVC.
`ApiResponse<T>` chỉ dùng cho thành công, giữ `code: 1000` và payload hiện tại.

```json
{
  "type": "https://fptpost.vn/errors/sys-invalid-request",
  "title": "Yêu cầu không hợp lệ",
  "status": 400,
  "code": "SYS_INVALID_REQUEST",
  "detail": "Vui lòng kiểm tra dữ liệu và tham số gửi lên.",
  "correlationId": "7f3c9a52-1d4e-4b8a-9c21-0a6e5d3f7b10",
  "errors": [
    { "field": "email", "code": "EMAIL_INVALID", "message": "Email không hợp lệ hoặc vượt quá 254 ký tự." }
  ]
}
```

`type` định danh loại lỗi; URI hiện theo namespace trong SPEC, chưa có website tài liệu
public tại các URI này. Client không cần gọi URI để xử lý lỗi.
`title` mô tả ngắn, `detail` hướng dẫn xử lý; HTTP status phải khớp `status` trong body.
Spring có thể thêm `instance` là đường dẫn request theo Problem Details.
`errors` chỉ xuất hiện khi có lỗi field; không gửi rejected value hoặc thông tin mật.
Không bọc object lỗi trong `data`, không có `success`, `message`, `timestamp` của ApiResponse.

## Mã đang sử dụng

| Code | HTTP | Trường hợp |
| --- | --- | --- |
| SYS_INVALID_REQUEST | 400 | JSON, tham số, UUID hoặc validation không hợp lệ |
| SYS_UNAUTHORIZED | 401 | Chưa xác thực; mã nền cho UC xác thực tiếp theo |
| SYS_FORBIDDEN | 403 | Không có quyền hoặc CORS không được phép |
| SYS_RESOURCE_NOT_FOUND | 404 | Không tìm thấy tài nguyên/đường dẫn |
| SYS_RESOURCE_ALREADY_EXISTS | 409 | Xung đột tài nguyên |
| SYS_METHOD_NOT_ALLOWED | 405 | HTTP method không được hỗ trợ; giữ header Allow |
| SYS_NOT_ACCEPTABLE | 406 | Accept không được hỗ trợ |
| SYS_PAYLOAD_TOO_LARGE | 413 | Dữ liệu vượt giới hạn |
| SYS_UNSUPPORTED_MEDIA_TYPE | 415 | Content-Type không phù hợp |
| SYS_UPSTREAM_UNAVAILABLE | 503 | Dịch vụ phụ thuộc không khả dụng |
| SYS_INTERNAL_ERROR | 500 | Lỗi ngoài dự kiến |
| PRODUCT_NOT_FOUND | 404 | Không tìm thấy Product demo |
| AUTH_EMAIL_ALREADY_EXISTS | 409 | Email đã đăng ký |
| AUTH_PHONE_ALREADY_EXISTS | 409 | SĐT đã đăng ký |
| AUTH_PASSWORD_INVALID | 400 | Mật khẩu không đạt chính sách |
| AUTH_POLICY_CHANGED | 409 | Phiên bản chính sách không còn hiện hành |
| AUTH_REGISTRATION_BLOCKED | 403 | Thông tin bị blacklist |
| AUTH_OTP_DELIVERY_FAILED | 503 | Không gửi được OTP; rollback đăng ký |
| AUTH_REGISTRATION_CONFLICT | 409 | Xung đột unique khi đăng ký đồng thời |
| RATE_LIMITED | 429 | Quá nhiều request; giữ Retry-After |

Validation riêng dùng mã khai báo trong annotation và câu chữ trong resource bundle:
`@Size(min = 8, message = "PASSWORD_INVALID")` → `validation.PASSWORD_INVALID`.
Identity có EMAIL_INVALID, PHONE_INVALID, PASSWORD_REQUIRED, PASSWORD_INVALID,
ADULT_CONFIRMATION_REQUIRED, TERMS_NOT_ACCEPTED, PRIVACY_NOT_ACCEPTED và các mã OTP.
Mã nằm trong `errors[].code`; validation đầu vào vẫn trả HTTP 400 và code tổng SYS_INVALID_REQUEST.
Đây là mã lỗi của field, không cần thêm HTTP status vào enum cho mỗi field.
Nếu annotation không khai báo mã riêng, dùng mã ràng buộc ổn định như NOT_BLANK,
NOT_NULL, SIZE, EMAIL, PATTERN, MIN, MAX, ASSERT_TRUE. Mã chưa đăng ký hoặc câu chữ
trực tiếp cũng dùng mã ràng buộc dự phòng; lỗi chuyển kiểu dùng TYPE_MISMATCH.
Một field có thể có nhiều lỗi; wire format giữ danh sách, form chọn câu chữ để hiển thị.
Các mã Order/Payment chỉ thêm khi triển khai nghiệp vụ tương ứng.

## Backend

1. Thêm mã String và HTTP status vào enum `<Module>ErrorCode` triển khai `ErrorCode`.
2. Thêm `CODE.title` và `CODE.detail` bằng tiếng Việt vào
   `server/src/main/resources/messages/errors.properties`.
3. Service ném `new AppException(<Module>ErrorCode.SOME_ERROR)`.
4. `GlobalExceptionHandler` dùng `ProblemResponseFactory` để tạo response.
   Handler riêng của module cũng gọi factory, không tự dựng JSON.
5. Bổ sung test HTTP status, code, content type và dữ liệu nhạy cảm không xuất hiện.

Với DTO validation, đặt `message = "FIELD_ERROR_CODE"` rồi thêm
`validation.FIELD_ERROR_CODE` trong cùng resource. Không ghi câu tiếng Việt trực tiếp
trong annotation và không đưa rejected value vào message. `@NotNull` cùng `@AssertTrue`
cho các checkbox bắt buộc để từ chối cả thiếu giá trị và false.

Nội dung public lấy từ resource bundle theo code; không dùng exception.getMessage()
hoặc customMessage để dựng detail. Các exception nội bộ, SQL, tên lớp và stack trace
không được đưa ra response. `/error` và lỗi CORS cũng dùng contract này.
Các lỗi ở proxy/server ngoài Spring vẫn do hạ tầng đó xử lý.

## Correlation ID

Frontend gửi UUID qua `X-Correlation-Id` cho mỗi request.
Backend giữ UUID hợp lệ, chuẩn hóa chữ thường; nếu thiếu hoặc sai thì sinh UUID mới.
Header response có cùng ID, body lỗi có correlationId và log dùng MDC correlationId.
Filter dọn MDC sau request, không để ID bị dùng lẫn giữa các request.
CORS cho phép header gửi lên và expose X-Correlation-Id/Retry-After cho frontend.
ID chỉ phục vụ tra cứu, không thay thế token, quyền truy cập hoặc Idempotency-Key.

## Frontend và OpenAPI

Axios nhận Problem Details và chuyển thành
`ApiError{status, code, errors, fieldErrors, correlationId}`.
`errors` giữ mảng gốc; `fieldErrors` là map cho form.
Từ điển câu chữ tiếng Việt ở `client/src/i18n/apiErrors.ts`, ánh xạ theo code.
Code chưa biết dùng detail/title an toàn từ backend hoặc câu chữ mặc định.
`ApiErrorNotice` hiển thị lỗi và nút sao chép mã theo dõi, kể cả khi trình duyệt không cho copy tự động.

Swagger khai báo schema ApiProblem, application/problem+json cho response lỗi
và header X-Correlation-Id. Response thành công giữ schema hiện tại.
Đây là thay đổi contract lỗi của base đang phát triển: client cũ đọc mã số/errors map
phải cập nhật cùng backend. Không trả xen kẽ hai định dạng.

Tham khảo: [Problem Details RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html),
[Spring error responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).
SPEC dùng tên RFC 7807; RFC 9457 là bản thay thế, các trường mở rộng vẫn theo contract dự án.

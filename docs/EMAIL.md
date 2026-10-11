# Email dùng chung và template

`common/mail` cung cấp service SMTP và render template cho các module.
Module nghiệp vụ quyết định người nhận, loại email và dữ liệu;
`EmailService` render nội dung rồi gửi qua `JavaMailSender`.
Service không truy cập repository, sinh OTP hoặc phụ thuộc vào Identity.

## Cấu trúc

```text
common/mail/
├── config/MailProperties.java
├── config/MailTemplateConfig.java
├── dto/EmailRequest.java
├── enums/EmailTemplateType.java
├── exception/EmailDeliveryException.java
└── service/EmailService.java

resources/templates/mail/
├── html/fragments.html
├── html/verify-email.html
├── text/verify-email.txt
└── subject/verify-email.txt
```

Hiện có template `VERIFY_EMAIL`, đang dùng cho đăng ký và gửi lại mã ở UC-01–02.
HTML và bản text được gửi cùng một email multipart/alternative, encoding UTF-8.
Người nhận có thể đọc bản text khi ứng dụng email không hiển thị HTML.

## Gọi từ module

```java
emailService.sendEmail(EmailRequest.builder()
        .email(user.getEmail())
        .templateType(EmailTemplateType.VERIFY_EMAIL)
        .variables(Map.of(
                "fullName", fullName,
                "otp", code,
                "expirationMinutes", 5))
        .build());
```

`EmailRequest` là contract nội bộ, không phải endpoint cho frontend gửi email tùy ý.
Loại template được chọn từ enum; không nhận đường dẫn template từ client.
Mỗi loại khai báo các biến bắt buộc trong enum, service kiểm tra trước khi gửi.
`appName` lấy từ cấu hình người gửi và không bị ghi đè bởi biến của module.

## Tùy chỉnh nội dung

- Giao diện, màu sắc và nội dung email đăng ký: sửa `html/verify-email.html`.
- Header/footer dùng lại giữa các email: sửa `html/fragments.html`.
- Bản text tương ứng: sửa `text/verify-email.txt`.
- Tiêu đề: sửa `subject/verify-email.txt`; giữ tiêu đề trên một dòng.
- Dùng `th:text="${fullName}"` cho dữ liệu người dùng để escape HTML.
  Không dùng `th:utext` với họ tên hoặc dữ liệu người dùng.
- Trong bản text, biến có dạng `[(${otp})]`.

HTML có dữ liệu mẫu để mở trực tiếp trong trình duyệt khi sửa giao diện.
Template được đóng gói trong JAR; sửa file rồi build/restart backend để áp dụng.
Đây là template trong repository, chưa có chức năng quản trị/sửa template trong database.

Thêm một loại email khi triển khai UC cần nó:

1. Tạo ba file HTML, text, subject cùng tên trong các thư mục tương ứng.
2. Thêm enum, ví dụ `PASSWORD_RESET("password-reset", Set.of("fullName", "otp", "expirationMinutes"))`.
3. Module gọi cùng `EmailService.sendEmail()` với loại và biến tương ứng.
4. Kiểm tra nội dung, biến động, tiếng Việt, escape HTML và lỗi gửi.

## Cấu hình và kiểm tra

SMTP tiếp tục nằm ở `spring.mail`, cấu hình qua các biến `SMTP_*` hiện có.
Cấu hình dùng chung ở `app.mail`:

| Biến môi trường | Mặc định | Ý nghĩa |
| --- | --- | --- |
| MAIL_FROM | no-reply@fptpost.local | Địa chỉ người gửi |
| MAIL_FROM_NAME | FPTPost | Tên người gửi, cũng là appName trong template |
| MAIL_TEMPLATE_CACHE | false | Cache kết quả resolve/parse template, có thể bật khi phát hành |

Docker Compose chuyển ba biến này từ `.env` vào backend.
Compose cũng đọc SMTP_HOST/PORT/USERNAME/PASSWORD/AUTH/STARTTLS từ `.env`.
Mặc định gửi qua Mailpit; SMTP thật dùng thông số provider và MAIL_FROM tương ứng.
MAILPIT_SMTP_PORT (1025) là cổng Mailpit local, độc lập với SMTP_PORT của provider.
Chạy `docker compose up --build -d`, đăng ký tại `/register`, xem email tại
`http://localhost:8025` (Mailpit local). Kiểm tra cả HTML, text và tiêu đề.

Lỗi SMTP hoặc render template được chuyển thành `EmailDeliveryException` với thông báo
chung; không lưu/log OTP, nội dung email hoặc exception của provider.
Module gọi quyết định cách xử lý nghiệp vụ. UC-01 chuyển lỗi này thành code AUTH_OTP_DELIVERY_FAILED/HTTP 503
và rollback SQL; vẫn gửi đồng bộ, chưa có outbox/retry của hệ thống thông báo.

Tham khảo: [Thymeleaf email templates](https://www.thymeleaf.org/doc/articles/springmail.html),
[Spring email](https://docs.spring.io/spring-framework/reference/integration/email.html).

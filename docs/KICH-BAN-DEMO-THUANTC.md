# Kịch bản demo phần quản trị và khiếu nại (Trần Công Thuận)

Kịch bản đi qua 15 UC của Thuận theo thứ tự dễ trình bày. UC-49, UC-52, UC-56 cần các PR #97, #96, #99 đã merge.

## 1. Chuẩn bị

1. Bật Docker Desktop, rồi chạy server và client bằng `scripts/dev.ps1` (profile mặc định `dev`).
2. Khi khởi động, `UserDemoSeeder` tạo bốn tài khoản demo, dùng chung mật khẩu `UserDemoSeeder.SIMPLE_PASSWORD`:

   | Vai trò | Email |
   |---|---|
   | Người gửi | `sender@fptpost.vn` |
   | Người giao | `courier@fptpost.vn` |
   | Quản trị viên | `admin@fptpost.vn` |
   | Kiểm duyệt viên | `moderator@fptpost.vn` |

3. `DisputeDemoSeeder` bổ sung dữ liệu khiếu nại vì luồng giao hàng (UC-28) và tạo khiếu nại (UC-50) chưa có:
   - 6 đơn `DEMO-HT-*` đã hoàn tất của người giao demo, để điểm uy tín có dữ liệu;
   - một khiếu nại **đang mở** trên đơn `DEMO-KN-*` (tai nghe, có bảo hiểm, ký quỹ 120.000 ₫). Mỗi lần khởi động lại,
     nếu không còn khiếu nại demo nào đang mở, seeder tạo thêm một hồ sơ mới để demo lại được;
   - một khiếu nại **đã đóng** trên đơn `DEMO-BT-01` có đề nghị bồi thường 400.000 ₫ cho Quỹ bảo đảm.
   Tắt seeder bằng `app.demo.complaints-enabled=false`.
4. Nên demo trên database mới, hoặc tạo lại dữ liệu ngay trước buổi demo: tác vụ định kỳ (UC-63) chuyển các đơn đang tìm
   người giao quá 24 giờ sang "Hết hạn".

## 2. Khiếu nại, bồi thường và uy tín (UC-51, UC-52, UC-49)

Đây là chuỗi nên demo liền mạch, mất khoảng 5 phút.

1. **Kiểm duyệt viên** mở `/mod/complaints`: hồ sơ `KN-…` của đơn `DEMO-KN-*` nằm đầu hàng đợi, có hạn phản hồi 24 giờ.
2. Mở hồ sơ, bấm **Tiếp nhận xử lý**. Xem ảnh bằng chứng, bấm **Xem lịch sử** để đọc chat của đơn, gửi một tin trao đổi.
3. Phán quyết **Chia ký quỹ theo tỷ lệ**, người gửi 40%, bồi thường 300.000 ₫, nhập lý do. Phần xem trước hiện
   hoàn 48.000 ₫ / trả người giao 72.000 ₫. Hồ sơ chuyển "Đã phán quyết", tiền **chưa** di chuyển.
4. **Người gửi** mở `/me/complaints` → hồ sơ → thấy phán quyết và hạn kháng nghị 48 giờ, gửi **kháng nghị**.
5. **Quản trị viên** mở `/mod/complaints/…` (lọc "Đang kháng nghị") → **Xem xét kháng nghị** → "Giữ nguyên phán quyết"
   → **Xác nhận và xử lý tiền**. Hồ sơ đóng: ví người gửi nhận 48.000 ₫, người giao nhận 72.000 ₫, đơn chuyển hoàn tất.
   Người ra phán quyết không được tự xem xét kháng nghị, nên bước 3 dùng kiểm duyệt viên và bước 5 dùng Admin.
6. **Quản trị viên** mở `/admin/guarantee`: có hai đề nghị chờ duyệt (400.000 ₫ từ dữ liệu demo và 300.000 ₫ vừa
   tạo), số dư quỹ 0 ₫.
   - Duyệt khoản 400.000 ₫ → chuyển "Chờ quỹ", trang báo quỹ còn thiếu.
   - Trích vào quỹ 500.000 ₫ → khoản 400.000 ₫ được chi ngay theo thứ tự phán quyết, phần biến động hiện cả hai giao dịch.
7. **Người giao** mở `/courier/trust` (hoặc lối tắt "Uy tín và hạng" ở trang tài khoản khi đang ở chế độ người giao):
   điểm, hạng, quyền lợi, tiến độ lên hạng và lịch sử. Nếu điểm chưa cập nhật, Admin bấm **Tính lại ngay** ở `/admin/trust`.
8. Trang thu nhập `/courier/earnings` hiện cùng điều kiện lên hạng; đúng hạn và đánh giá hiện "Chưa có dữ liệu" cho tới
   khi UC-28 và UC-47 có dữ liệu thật.

## 3. Các UC quản trị khác

| UC | Tài khoản | Thao tác demo |
|---|---|---|
| UC-46 Tham số | Admin | `/admin/params`: sửa một tham số, xem lịch sử phiên bản và mô phỏng biểu phí |
| UC-60 Kiểm toán | Admin | `/admin/audit`: lọc theo hành động, ví dụ `COMPLAINT_RULED` vừa tạo ở mục 2 |
| UC-45 Khuyến mãi | Admin | `/admin/promotions`: tạo, sửa và tạm dừng mã khuyến mãi |
| UC-09, UC-53 KYC | Người gửi, Kiểm duyệt viên | Người gửi nộp CCCD ở `/me/kyc`; kiểm duyệt viên duyệt hoặc từ chối ở `/mod/kyc` |
| UC-54 Kiểm duyệt đơn | Người gửi, Kiểm duyệt viên | Tạo đơn có tên hàng chứa "rượu" hoặc "pin lithium" → đơn vào `/mod/orders`; duyệt hoặc từ chối kèm lý do |
| UC-55 Báo cáo vi phạm | Người gửi, Kiểm duyệt viên | Bấm "Báo cáo" trên chi tiết đơn hoặc một tin nhắn chat; kiểm duyệt viên xử lý ở `/mod/reports` |
| UC-57 Người dùng, nhân sự | Admin | `/admin/users` tìm và xem chi tiết người dùng; `/admin/staff` phân quyền nhân sự |
| UC-56 Khóa tài khoản | Admin | Ở chi tiết người dùng, khóa 3 ngày kèm lý do rồi mở khóa; thẻ danh sách đen ở `/admin/users` |
| UC-41 Duyệt rút tiền | Người giao, Admin | Người giao tạo yêu cầu rút ở `/wallet`; Admin duyệt và đánh dấu đã chi ở `/admin/withdrawals` |
| UC-63 Tác vụ định kỳ | Admin | `/admin/jobs`: xem lần chạy gần nhất, chạy tay một tác vụ |
| UC-59 Dashboard, báo cáo | Admin | `/admin/dashboard` chọn 7/30/90 ngày, xuất CSV; `/admin/reports` doanh thu người giao theo tháng |

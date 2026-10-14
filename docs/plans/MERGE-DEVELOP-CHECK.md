# Kiểm tra bản merge develop — UC-46

Ngày 14/10/2026. Nhánh feature/UC-46-ThuanTC-cau-hinh-tham-so ghép
origin/develop `13eeb41` (UC-12 tạo đơn và UC-34 VNPay) theo yêu cầu của Thuận.

## Xử lý xung đột

- AppRoutes.tsx giữ Navigate/AdminLayout/AdminParametersPage và cả ba trang đơn hàng.
  Giữ /admin/params cùng /sender/orders, /sender/orders/new và /sender/orders/:id.
- SecurityConfig.java giữ GET uploads, matcher orders/payment của develop,
  đồng thời /api/v1/admin/** và actuator nội bộ tiếp tục yêu cầu ADMIN.
- SMTP username/password lấy từ biến môi trường, bỏ fallback credentials trong source mới.
- Bỏ dòng trống thừa cuối VNPAY_INTEGRATION_GUIDE.md để diff check đạt.

Không thay đổi logic Order/VNPay của đồng đội. Module tạo đơn mới ghép chưa chuyển
sang ParameterSnapshot/PricingSimulationPort; UC-46 vẫn READY_WITH_MOCK về Pricing.

## Kết quả kiểm tra

Frontend typecheck/lint/build PASS. Maven backend PASS: 177 tests được phát hiện,
172 tests chạy, 0 failures/errors, 5 live tests bỏ qua vì chưa bật opt-in.
Các tests quyền ADMIN, UC-46, identity, order và payment cùng chạy trong bộ này.
Git diff check đạt và không còn file unmerged/conflict marker trong source.

Kết quả Docker/runtime sau build và tổng hợp test nằm trong
[merge-develop-verification.json](evidence/merge-develop-verification.json).
Ảnh UAT và 5 live tests của bàn giao đầu là bằng chứng trước merge,
không được tính thành kết quả live chạy lại sau merge.

Runtime dùng nguyên .env của nhóm và các volume SQL/Mongo đang có.
Profile dev,demo bổ sung dữ liệu demo của develop qua UserDemoSeeder/PaymentDemoSeeder:
identity theo role, đơn mẫu và payment mẫu chỉ thêm khi chưa tồn tại. Không sửa
role/password của tài khoản đã có, không đăng ký/gửi OTP qua Gmail và không gọi VNPay.

Commit merge/push tiếp tục trên nhánh chức năng của Thuận; không merge trực tiếp vào develop.

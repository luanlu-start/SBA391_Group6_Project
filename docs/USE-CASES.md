# Danh mục Use Case FPTPost

Nguồn: [Excel v1.0](sources/fptpost-br-uc-v1.0.xlsx), sheet `04_UC`, dòng 5–67.
63 UC (50 Must, 6 Should, 7 Could). **Toàn bộ trạng thái trong nguồn là Dự thảo**;
phân công/sprint dưới đây là kế hoạch, chưa phản ánh tiến độ code.
BE/FE là mã thành viên TV1–TV6; `—` là ô trống trong nguồn. Sprint lấy cột
`Sprint hoàn thành`; xem riêng sprint BE/FE trong sheet nếu cần điều phối phụ thuộc.

## Quy ước nhánh và PR

Giữ mã `UC-xx` đúng sheet và ví dụ SPEC; mô tả không dấu, dùng gạch nối:

- UC-03 Đăng nhập: `feature/UC-03-dang-nhap`, PR `[UC-03] Đăng nhập`.
- UC-12 Tạo đơn giao hàng: `feature/UC-12-tao-don`, PR `[UC-12] Tạo đơn`.
- Công việc chung không thuộc UC: `docs/<mo-ta>` hoặc `chore/<mo-ta>`.

Nhánh bắt đầu từ `develop`, PR vào `develop`. Mã UC là mã yêu cầu;
`API-xxx`, `DB-xxx`, `SCR-xx`, `PRM-xxx` là mã tra cứu thiết kế, không phải mã lỗi API.

## Danh mục

| UC | Tên Use Case | Phân hệ | Ưu tiên | BE | FE | Hoàn thành dự kiến |
| --- | --- | --- | --- | --- | --- | --- |
| UC-01 | Đăng ký tài khoản | AUTH | Must | TV2 | TV5 | S1 |
| UC-02 | Xác thực OTP email/SĐT | AUTH | Must | TV2 | TV5 | S1 |
| UC-03 | Đăng nhập | AUTH | Must | TV2 | TV5 | S1 |
| UC-04 | Đăng xuất và quản lý phiên/thiết bị | AUTH | Must | TV2 | TV5 | S1 |
| UC-05 | Quên và đặt lại mật khẩu | AUTH | Must | TV2 | TV5 | S1 |
| UC-06 | Đổi mật khẩu | AUTH | Must | TV2 | TV5 | S1 |
| UC-07 | Quản lý hồ sơ cá nhân và sổ địa chỉ | KYC | Must | TV2 | TV5 | S2 |
| UC-08 | Gửi hồ sơ xác minh danh tính (KYC) | KYC | Must | TV2 | TV5 | S2 |
| UC-09 | Duyệt hồ sơ xác minh | KYC | Must | TV2 | TV2 | S3 |
| UC-10 | Đăng ký làm Người giao | KYC | Must | TV2 | TV5 | S3 |
| UC-11 | Ước tính giá và phí | ORD | Must | TV3 | TV4 | S2 |
| UC-12 | Tạo đơn giao hàng | ORD | Must | TV3 | TV4 | S2 |
| UC-13 | Xem danh sách và chi tiết đơn của tôi | ORD | Must | TV3 | TV5 | S4 |
| UC-14 | Sửa đơn | ORD | Must | TV3 | TV4 | S2 |
| UC-15 | Hủy đơn | ORD | Must | TV3 | TV4 | S4 |
| UC-16 | Duyệt và tìm kiếm sàn đơn | MKT | Must | TV3 | TV4 | S3 |
| UC-17 | Nhận đơn (giá niêm yết) | MKT | Must | TV3 | TV4 | S3 |
| UC-18 | Gửi chào giá | MKT | Must | TV3 | TV4 | S3 |
| UC-19 | Xử lý chào giá (chấp nhận, từ chối, phản giá) | MKT | Must | TV3 | TV4 | S4 |
| UC-20 | Mời trực tiếp người giao | MKT | Could | TV3 | TV4 | S6 |
| UC-21 | Gợi ý ghép đơn–chuyến | MKT | Should | TV3 | TV4 | S6 |
| UC-22 | Đăng chuyến đi | TRP | Should | TV3 | TV3 | S5 |
| UC-23 | Quản lý chuyến đi | TRP | Should | TV3 | TV3 | S6 |
| UC-24 | Chat theo đơn | NEG | Must | TV6 | TV4 | S3 |
| UC-25 | Xác nhận lấy hàng (QR + ảnh) | TRK | Must | TV6 | TV4 | S4 |
| UC-26 | Chia sẻ vị trí và cập nhật hành trình | TRK | Must | TV6 | TV4 | S5 |
| UC-27 | Theo dõi đơn thời gian thực | TRK | Must | TV6 | TV4 | S5 |
| UC-28 | Xác nhận giao hàng (OTP/QR + ảnh) | TRK | Must | TV6 | TV4 | S4 |
| UC-29 | Báo sự cố khi giao | TRK | Must | TV6 | TV4 | S5 |
| UC-30 | Giao thất bại và hoàn hàng | TRK | Must | TV6 | TV4 | S6 |
| UC-31 | Gộp đơn và gợi ý lộ trình | REL | Could | TV3 | TV4 | S7 (backlog) |
| UC-32 | Tạo đơn giao tiếp sức | REL | Could | TV3 | TV4 | S7 (backlog) |
| UC-33 | Bàn giao giữa các chặng | REL | Could | TV3 | TV4 | S7 (backlog) |
| UC-34 | Nạp tiền vào ví qua VNPay | PAY | Must | TV1 | TV5 | S2 |
| UC-35 | Thanh toán đơn và ký quỹ | PAY | Must | TV1 | TV5 | S3 |
| UC-36 | Giải ngân cho người giao | PAY | Must | TV1 | TV5 | S4 |
| UC-37 | Hoàn tiền | PAY | Must | TV1 | TV5 | S4 |
| UC-38 | Xem ví và lịch sử giao dịch | PAY | Must | TV1 | TV5 | S3 |
| UC-39 | Quản lý tài khoản ngân hàng nhận tiền | PAY | Must | TV1 | TV5 | S2 |
| UC-40 | Yêu cầu rút tiền | PAY | Must | TV1 | TV5 | S4 |
| UC-41 | Duyệt và chi trả rút tiền | PAY | Must | TV1 | TV1 | S5 |
| UC-42 | Xử lý IPN và kết quả từ VNPay | PAY | Must | TV1 | — | S2 |
| UC-43 | Đối soát giao dịch hằng ngày | PAY | Should | TV1 | TV1 | S5 |
| UC-44 | Áp dụng mã khuyến mãi | FEE | Could | TV2 | TV5 | S6 |
| UC-45 | Quản lý khuyến mãi | FEE | Could | TV2 | TV5 | S6 |
| UC-46 | Cấu hình tham số hệ thống và biểu phí | ADM | Must | TV2 | TV5 | S2 |
| UC-47 | Đánh giá hai chiều | REP | Must | TV2 | TV2 | S5 |
| UC-48 | Xem hồ sơ công khai và điểm uy tín | REP | Must | TV6 | TV6 | S6 |
| UC-49 | Tính điểm uy tín và xếp hạng | REP | Must | TV6 | TV6 | S6 |
| UC-50 | Tạo khiếu nại | DSP | Must | TV1 | TV5 | S5 |
| UC-51 | Xử lý khiếu nại và phán quyết | DSP | Must | TV1 | TV5 | S6 |
| UC-52 | Xét duyệt bồi thường từ Quỹ bảo đảm | DSP | Must | TV1 | TV5 | S6 |
| UC-53 | Nhận và cấu hình thông báo | NTF | Must | TV6 | TV5 | S3 |
| UC-54 | Kiểm duyệt đơn hàng | SAF | Must | TV2 | TV5 | S4 |
| UC-55 | Báo cáo vi phạm | SAF | Must | TV2 | TV5 | S4 |
| UC-56 | Khóa và mở khóa tài khoản | SAF | Must | TV2 | TV5 | S5 |
| UC-57 | Quản lý người dùng và nhân sự | ADM | Must | TV2 | TV2 | S4 |
| UC-58 | Quản lý danh mục | ADM | Must | TV2 | TV2 | S2 |
| UC-59 | Dashboard và báo cáo | ADM | Must | TV2 | TV3 | S6 |
| UC-60 | Xem nhật ký kiểm toán | ADM | Should | TV2 | TV2 | S4 |
| UC-61 | Xem thu nhập và thống kê | GAM | Should | TV6 | TV5 | S5 |
| UC-62 | Huy hiệu, thưởng và giới thiệu bạn bè | GAM | Could | TV6 | TV5 | S7 (backlog) |
| UC-63 | Xử lý tác vụ định kỳ | ADM | Must | TV6 | — | S2 |

## Truy vết khi triển khai

Với mỗi UC, đọc tiền/hậu điều kiện, luồng chính và ngoại lệ tại sheet `04_UC`;
đối chiếu BR và tiêu chí nghiệm thu ở `03_BR`, ma trận `05_Truy_vết`, trạng thái ở
`08_Trạng_thái`, dữ liệu ở `09_Dữ_liệu`, API ở `10_API` và màn hình ở `11_Màn_hình`.

Mô tả PR ghi UC, các BR ảnh hưởng, endpoint/màn hình thay đổi, cách kiểm tra và kết quả.
Chỉ cập nhật trạng thái hoàn thành khi luồng nghiệp vụ cùng các ngoại lệ liên quan đã
được kiểm thử và review. Danh mục được chụp từ v1.0; khi nguồn thay đổi phải cập nhật cùng PR.

# FPTPost — thông tin dự án

FPTPost kết nối người có hàng cần gửi với người có thể giao hàng tiện đường (cowork/P2P).
Nền tảng hỗ trợ xác minh danh tính, tìm người giao, ký quỹ, theo dõi giao nhận và thanh toán.

Nguồn: [Excel v1.0](sources/fptpost-br-uc-v1.0.xlsx), sheet `00_Bìa`, `01_Tổng_quan`,
`02_Tác_nhân_Phân_quyền`; [SPEC v1.0](sources/fptpost-spec-v1.0.pdf), mục 1–2.
Tài liệu nguồn đang là dự thảo; phạm vi dưới đây là **kế hoạch nghiệp vụ**.

## Thông tin và quy mô

| Nội dung | Theo tài liệu v1.0 |
| --- | --- |
| Mã dự án | FPTPOST |
| Nhóm và thời gian | TV1–TV6; 12 tuần, 6 sprint, mỗi sprint 2 tuần |
| Ngày tài liệu | 08/10/2026 |
| Yêu cầu BR | 286 |
| Use Case | 63: 50 Must, 6 Should, 7 Could |
| Tham số | 109 mục PRM |
| Từ điển dữ liệu | 55 mục: 49 SQL Server, 6 MongoDB |
| Danh mục API | 167 mục: 165 HTTP, 2 WebSocket |
| Màn hình | 64 mục SCR |
| Phạm vi địa lý giả định | Cộng đồng FPT và khu vực lân cận campus; cần nhóm xác nhận |

Một mục từ điển có thể gồm nhiều bảng; 55 mục không phải số bảng vật lý.
Môn học trong tài liệu là SBA301; tên repository hiện là SBA391. Nhóm cần xác nhận
mã môn dùng khi nộp bài, chưa đổi tên repository/artifact.

## Phạm vi

| Mức | Nhóm chức năng |
| --- | --- |
| Must | Tài khoản/OTP, KYC, tạo đơn, sàn đơn/chào giá, chat, ví/VNPay sandbox, ký quỹ, QR/OTP giao nhận, theo dõi, giải ngân/hoàn/rút, tranh chấp, kiểm duyệt và quản trị |
| Should | Chuyến đi/ghép đơn, đối soát, audit, thống kê thu nhập; một số luồng phụ được mô tả trong BR/UC |
| Could | Mời trực tiếp, khuyến mãi, gộp đơn/giao tiếp sức, huy hiệu/giới thiệu |

Ngoài phạm vi v1.0: COD và thanh toán phí giao bằng tiền mặt; bưu cục/kho và tích hợp
hãng vận chuyển; vận chuyển quốc tế, hàng cấm, hàng quá khổ/chuỗi lạnh; ứng dụng native;
AI/eKYC tự động; chi trả ngân hàng thật trong demo; tự động hóa hóa đơn/kê khai thuế.
Demo theo tài liệu dùng sandbox/tiền mô phỏng, KYC thủ công, payout Mock/thủ công.

## Tác nhân và quyền

| Mã | Tác nhân | Vai trò |
| --- | --- | --- |
| A01 | Guest | Xem công khai, ước tính giá, đăng ký |
| A02 | Sender | Tạo đơn, chọn người giao, ký quỹ, theo dõi |
| A03 | Courier/Cowork | Đã xác minh, nhận và giao hàng, rút thu nhập |
| A04 | Recipient | Theo dõi/xác nhận bằng liên kết và OTP/QR, không cần tài khoản |
| A05 | Moderator | Duyệt KYC, kiểm duyệt, xử lý báo cáo/tranh chấp |
| A06 | Admin | Cấu hình, quản lý người dùng, duyệt rút, đối soát |
| A07 | Scheduler/System | Tác vụ hết hạn, giải ngân, tính điểm, đối soát |
| A08 | VNPay | Tích hợp thanh toán sandbox theo thiết kế |
| A09 | Bản đồ/định tuyến | Địa chỉ, quãng đường, bản đồ |
| A10 | Email/SMS | OTP và thông báo |
| A11 | PayoutProvider | Chi trả Mock/thủ công theo thiết kế demo |

User có thể có nhiều role `SENDER`, `COURIER`, `MODERATOR`, `ADMIN`.
Kiểm tra cả role và quyền sở hữu/tham gia tài nguyên; UUID không thay thế kiểm tra quyền.
Người giao phải đạt điều kiện KYC/trạng thái; duyệt rút tiền có tách biệt người duyệt.

## Luồng xuyên suốt cần hoàn thiện

```text
Đăng ký/OTP → KYC người giao
Người gửi tạo đơn → kiểm duyệt/mở sàn → nhận giá hoặc chào giá
→ ký quỹ → phân công → QR/ảnh lấy hàng → theo dõi vị trí
→ OTP/QR/ảnh giao hàng → giải ngân → đánh giá
```

Nhánh hủy, hết hạn, giao thất bại/hoàn hàng, khiếu nại và hoàn tiền phải tuân theo
sheet `08_Trạng_thái` và các BR liên quan. Sơ đồ chính không thay thế bảng chuyển trạng thái.
Tiền, trạng thái đơn và lịch sử nghiệp vụ nằm trong SQL; sự kiện/đồng bộ đi qua outbox.

## Kế hoạch và hiện trạng

Phân công TV1–TV6, sprint và ưu tiên trong [USE-CASES.md](USE-CASES.md) là kế hoạch
từ sheet `04_UC`; chi tiết giờ công ở sheet `13_Kế_hoạch_Nhân_sự`, `14_Phân_bổ_việc`.
Sprint 7 trong sheet là backlog ngoài 6 sprint chính. Chưa có danh sách tên thật của TV1–TV6.

Repository hiện có base Product CRUD (SQL), Activity Log (Mongo), DTO/MapStruct,
validation, mã lỗi, paging/filter/sort, Swagger, seed demo và Docker.
Các module FPTPost trong đặc tả chưa triển khai; không tính Product CRUD là UC tạo đơn.

Tra cứu tiếp: [kiến trúc/module](LOGISTICS-BASE.md), [database](DATABASE.md),
[API](API.md), [đối chiếu SPEC](SPEC-ALIGNMENT.md), [quy ước code](CONTRIBUTING.md).

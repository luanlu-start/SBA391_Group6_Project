# Kế hoạch triển khai FPTPost cho Trần Công Thuận

Baseline audit: **13/10/2026**. Ngày cập nhật: **14/10/2026**, múi giờ Asia/Saigon. Kế hoạch gốc bên dưới được giữ để đối chiếu; kết quả thực hiện đợt 1 nằm trong [ROUND1-HANDOFF.md](ROUND1-HANDOFF.md) và [PROGRESS.md](PROGRESS.md).

## Quyết định đã được người dùng xác nhận

- Excel `FPTPost_Dac_ta_BR_UC (1).xlsx` do người dùng cung cấp là **nguồn yêu cầu và phân công chính thức đang áp dụng**. Không thay bằng bản Excel cũ trong `docs/sources`, kế hoạch TV1–TV6 hay phân công PAY cũ.
- Người dùng là **Trần Công Thuận**. Lúc lập plan, phần Thuận chưa triển khai và bắt đầu ở `NOT_STARTED`; ngày 14/10 đã hoàn tất nền P00 và bản độc lập P01/UC-46. Các gói P02–P15 vẫn chưa bắt đầu.
- Giữ nguyên 15 UC và người phụ trách trong sheet `15_Chia task(UC)`. Bảng `04_UC` lấy người/sprint từ sheet này. Bảng phân hệ tổng hợp không thu hẹp phạm vi xuống 11 UC.
- Người dùng cho phép lập lại thứ tự và thời gian thực hiện **phần Thuận** theo phụ thuộc; có thể triển khai độc lập bằng contract và dữ liệu giả lập, thay adapter khi đồng đội bàn giao.
- Mục tiêu vẫn đủ **63 UC của dự án**, bao gồm Should/Could đã có trong Excel. Plan này không giao 48 UC của đồng đội cho agent của Thuận.

## Bắt đầu từ đâu

**Làm P00 và P01 trước: nền tối thiểu phục vụ một chức năng thật, rồi UC-46 tham số và mô phỏng biểu phí.** Không dựng trước toàn bộ framework, không làm payout/khiếu nại trước khi có contract tiền và trạng thái.

| Tài liệu | Mục đích |
| --- | --- |
| [AGENT-START.md](AGENT-START.md) | Prompt có thể đưa ngay cho agent, giới hạn đợt đầu P00/P01 |
| [ROUND1-HANDOFF.md](ROUND1-HANDOFF.md) | Kết quả code, chạy Docker, kiểm thử và bàn giao đợt 1 |
| [UC46-CONTRACT.md](UC46-CONTRACT.md) | Contract C01/C02 thực tế và API UC-46 để đồng đội tích hợp |
| [THUAN-IMPLEMENTATION-PLAN.md](THUAN-IMPLEMENTATION-PLAN.md) | 5 đợt triển khai, backlog P00–P15, đầu ra FE/BE, fixture và tiêu chí nghiệm thu |
| [CONTRACTS-AND-INTEGRATION.md](CONTRACTS-AND-INTEGRATION.md) | Interface với đồng đội, dữ liệu giả lập, quy trình thay bằng module thật |
| [PROJECT-ROADMAP.md](PROJECT-ROADMAP.md) | Roadmap tích hợp toàn dự án và ma trận đủ 63 UC theo phân công chính thức |
| [SOURCE-TRACEABILITY.md](SOURCE-TRACEABILITY.md) | API/màn hình/BR nguồn liên quan 15 UC; danh mục job và quyết định thiết kế cần chốt |
| [PROGRESS.md](PROGRESS.md) | Tracker riêng từng gói P00–P15, ban đầu đều chưa bắt đầu |
| [plan-manifest.json](plan-manifest.json) | Snapshot yêu cầu có cấu trúc để agent đối chiếu tự động |
| [sources/applied-br-uc.xlsx](sources/applied-br-uc.xlsx) | Bản sao nguyên byte của Excel chính thức, kèm SHA-256 trong manifest |
| [sources/applied-spec.pdf](sources/applied-spec.pdf) | Bản SPEC tham chiếu đi kèm để có đủ context khi chuyển gói plan cho agent |

## Lịch mới của Thuận

Các nhãn **T1–T5** bên dưới là 5 đợt thực hiện riêng của Thuận sau khi replan. Chúng khác **S1–S5** của Excel; không tự đổi lịch/phân công đồng đội trong file nguồn.

| Đợt | Thời gian 2026 | Trọng tâm |
| --- | --- | --- |
| T1 | 14–16/10 | P00 nền tối thiểu; P01 UC-46; audit capture/outbox cho thay đổi tham số |
| T2 | 17–23/10 | P02 UC-60; P03 UC-57; P04 UC-56; P05 UC-53 |
| T3 | 24–30/10 | P06 UC-45; P07 UC-09; P08 UC-54; P09 UC-55; P10 UC-49 |
| T4 | 31/10–06/11 | P11 UC-41; P12 UC-51; P13 UC-52; P14 UC-59; P15 UC-63 bản chạy độc lập |
| T5 | 07–13/11 | Đổi sang adapter thật; tích hợp và kiểm tra Web Push/batch/export đã có bản độc lập ở T4; kiểm thử xuyên module và UAT đủ 15 UC |

T1 không được ghi lùi ngày 10–13/10 hoặc coi task quá khứ đã xong. UC-59/60 có mốc nguồn 10/11; plan đưa phần độc lập lên trước và giữ mốc tích hợp chúng trước 10/11. 14/11 là mốc deploy dự kiến từ Excel, không phải hành động được thực hiện trong lần lập plan này.

## Theo dõi tiến độ

Mỗi Pxx dùng: `NOT_STARTED → IN_PROGRESS → READY_WITH_MOCK → INTEGRATED → VERIFIED`. Có thêm `WAITING_FOR_INTEGRATION` nếu phần độc lập xong nhưng module nguồn chưa bàn giao. `READY_WITH_MOCK` không được báo thành hoàn tất cả luồng thật. Mock Payout/Manual và VNPay sandbox là chế độ demo được Excel cho phép; mock Order/KYC/Ledger của đồng đội phải được thay khi nghiệm thu tích hợp.

Các DTO, tên interface và mốc bàn giao trong plan là **đề xuất triển khai**, phải đối chiếu interface thật trước khi tạo. Các số PRM, quyền, UC/BR, trạng thái và phân công là yêu cầu nguồn, không được tự giảm để vừa lịch.

## Công suất và điều chỉnh

Sheet `13_Kế_hoạch_Nhân_sự` giả định 24 giờ/người/tuần. Phần Thuận được tính 90 giờ UC + 10 giờ chung + 20 giờ họp = 120 giờ trong lịch gốc. Từ 14/10 đến 13/11 còn 31 ngày, tương đương khoảng 106 giờ ở nhịp 24 giờ/tuần; lịch có chênh khoảng 14 giờ trước cả phát sinh. Con số 6 giờ/UC trong Excel là đầu vào kế hoạch, không chứng minh mọi UC phức tạp đều mất đúng 6 giờ.

Plan giữ đầy đủ phạm vi, dùng agent cho các gói độc lập và tái sử dụng base để giảm thao tác lặp. Cuối T1 phải cập nhật thời gian thực tế, chia tiếp task nếu lớn; nếu vẫn giữ toàn bộ ngân sách họp và ước lượng gốc thì cần bù khoảng 14 giờ. Không tự tăng công suất của người dùng trong tracker, không bỏ Could/Should và không đánh dấu mock thành nghiệm thu thật để đạt mốc.

## Nguồn và hiện trạng

Nguồn Excel gốc: `C:/Users/tranc/Downloads/FPTPost_Dac_ta_BR_UC (1).xlsx`; SHA-256 `d68b1ca13edcf2fe73720afa49442ef759c725b29c40cef5be78457bf2ef69fb`. Bản sao trong gói plan giữ nguyên nội dung, không sửa workbook.

SPEC tham chiếu: [applied-spec.pdf](sources/applied-spec.pdf), 46 trang, SHA-256 `ab3b9410ae1e91459ed88af108c5bb42075a5d3baa6fba9b15d60bae53fc2b91`. Excel quyết định nghiệp vụ; SPEC giải thích kỹ thuật. Quy ước code của checkout hiện tại được giữ: `com.fptpost`, controller/service/repository/model/dto/mapper, DTO/MapStruct, `ApiResponse`/`PageResponse`, lỗi Problem Details, code first. Không tự đổi sang kiến trúc hay migration framework khác trong P00.

Baseline lúc lập plan tại `develop`, commit `b2893d9`: có Identity đăng ký/OTP email/login/refresh/logout/session, Product/Activity mẫu, UI components và health. Đây là lịch sử trước khi thực hiện đợt 1, không phải trạng thái hiện tại.

Cấu hình đang áp dụng ngày 14/10 theo bàn giao của nhóm: SQL Server 2022 và MongoDB riêng trong Docker, backend `18080`, frontend `15173`, SQL `31433`, Mongo `37017`, profile `dev,demo`. `.env` gốc giữ cấu hình leader. Nhánh thực hiện là `feature/UC-46-ThuanTC-cau-hinh-tham-so`. Kết quả test thực tế và giới hạn tích hợp được ghi riêng trong bàn giao đợt 1.

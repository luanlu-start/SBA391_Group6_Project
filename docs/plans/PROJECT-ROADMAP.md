# Roadmap tích hợp toàn dự án FPTPost

Đích theo Excel: **63 UC, 50 Must / 6 Should / 7 Could**, deploy dự kiến ngày **14/11/2026**. Người phụ trách và sprint gốc giữ nguyên; W1–W5 là **mốc tích hợp đề xuất**, không phân công lại task của đồng đội. Agent của Thuận triển khai 15 UC và phần nền tảng được giao, không nhận 48 UC còn lại.

Kế hoạch được xuất ngày **14/10/2026**; ngày bắt đầu thực thi đề xuất là **14/10**. Khảo sát ngày 13/10 là baseline, không phải bằng chứng đã hoàn thành task.

## 1. Baseline và chiến lược

Theo khảo sát ngày 13/10, Thuận chưa code phần được giao; repo đã có nền tảng Identity, Product, Activity, health và UI. Đến khi xuất kế hoạch ngày 14/10, phần việc của Thuận vẫn bắt đầu từ chưa triển khai. Cần kiểm chứng lại toàn bộ yêu cầu xác thực theo Excel; code có sẵn chưa đồng nghĩa với UC đã đạt 100% tiêu chí nghiệm thu. Sprint 1 gốc (10–12/10) đã qua; không ghi lùi tiến độ hoặc coi task ngày 13/10 là đã xong.

Phát triển từng domain độc lập bằng fixtures, đồng thời tích hợp **một luồng giao hàng thật** sớm: Identity → Params/Pricing → Order/moderation → Market → Ledger/Payment → Tracking → release → Rating/Trust. Chia sẻ contract, OpenAPI và schema trước khi code. Audit, outbox và kiểm tra quyền tham gia ngay từ phần chức năng đầu tiên.

## 2. Mốc toàn nhóm

| Wave | Thời gian năm 2026 | Đầu ra mục tiêu |
| --- | --- | --- |
| W1 | 14–19/10 | Kiểm chứng nền tảng xác thực; params/snapshot, nguồn sự kiện audit và contract thông báo; fixtures cùng chuẩn state/error/ID/event; contract ký quỹ/VNPay |
| W2 | 20–26/10 | Tạo đơn → kiểm duyệt → sàn → nhận/chào giá → ký quỹ → lấy hàng bằng QR → giao hàng bằng OTP → giải ngân; trip/catalog/promo. Có thể dùng tài khoản KYC đã seed, chưa coi là luồng onboarding thật |
| W3 | 27/10–02/11 | Nộp/duyệt KYC → đăng ký courier thật; ngân hàng → yêu cầu rút → admin duyệt → Mock/Manual Payout và Ledger thật; ratings → trust; safety/user admin; đối soát |
| W4 | 03–10/11 | Huỷ/hoàn tiền, khiếu nại/đóng băng/phán quyết/kháng nghị/quỹ; batch/relay/invite/referral/promo; report/audit; đầy đủ job handlers. Đóng tính năng trước 10/11 để dành thời gian UAT |
| W5 | 11–13/11 | Đối chiếu tiêu chí nghiệm thu của 63 UC/BR, 8 E2E và scope Should/Could; kiểm thử concurrency trên SQL/Mongo, chạy lại giao dịch tiền và jobs; UAT theo vai trò/mobile; release candidate và tài liệu deploy/rollback |
| Deploy | 14/11 | Mốc Excel; cần yêu cầu triển khai riêng sau khi bản cụ thể đã được review. Lần lập kế hoạch này chưa deploy |

Thời gian W và T (Thuận) không cùng lịch: T là thứ tự thực thi cá nhân, W là kỳ vọng ghép luồng. Consumer ở W1/W2 có thể dùng fixtures trước. Nếu Thuận hoặc người phụ trách chưa hoàn tất gói tương ứng, phải cập nhật mốc tích hợp thật; không báo luồng thật đã đạt khi vẫn dùng giả lập.

## 3. Mốc contract và bàn giao đề xuất

| Người phụ trách/nguồn | Chốt contract + fixtures | Bàn giao cần để nghiệm thu | Thuận sử dụng |
| --- | --- | --- | --- |
| Luân — Identity/session/roles | 14/10 | Kiểm chứng nền tảng hiện có; nối dần staff/password/privacy | P03/P04 và mọi Admin API |
| Luân — KYC model/upload | 15/10 | KYC thật từ 27/10 đến 02/11 | P07 |
| Thành Huy — Order/Pricing/catalog | 15/10 | Order/price ở W1–W2; exception/cancel ở W4 | P01/P04/P08/P12/P14/P15 |
| Quốc An — Market/trip/offer | 16/10 | Offers ở W2, eligibility/matching ở W3, relay/batch ở W4 | P10/P05/P15 |
| Nhựt Huy — Ledger/Payment | 16/10 | Escrow/release ở W2; withdrawal ở W3; refund/fund ở W4 | P11/P12/P13/P14/P15 |
| Phi Long — evidence/metrics | 16/10 | Chat/QR/OTP ở W2; ratings/giao thất bại ở W3 | P09/P10/P12 |
| Phi Long — complaint UC-50 | 21/10 | Đề xuất bàn giao case thật ngày 03–05/11, trước hạn chót nguồn 13/11 | P12/P13 |
| Thuận — parameters/events/audit | 14–15/10 | P01 ở T1; notification/audit hoàn thiện ở T2 | Tất cả publisher/consumer |
| Thuận — promotion UC-45 ↔ Thành Huy — UC-44 | 20/10 | Contract cho consumer ở W2, management ở T3; ghép thật ở W3 nếu chưa đủ | Promotions/order/payment |
| Mỗi người phụ trách — job handler | 16/10 | Phát hành theo W2–W4; đầy đủ ngày 10/11 | P15 |
| Mỗi người phụ trách — dashboard metrics | 21/10 | Định nghĩa và projections thật ngày 08–10/11 | P14 |

Các mốc này **chưa có cam kết của đồng đội**. Không tự nhắn hoặc giao việc bên ngoài. Agent vẫn tiến hành với contract đề xuất; khi provider thật được bàn giao, tiếp nhận và chạy lại cùng các kịch bản kiểm thử. Nếu người phụ trách chỉ hoàn tất vào cuối Sprint 5 gốc, thời gian tích hợp thật có thể chưa đủ; tracker phải ghi rõ phần đang chờ.

## 4. Các luồng kiểm chứng cuối

| Luồng | UC chính và phần Thuận |
| --- | --- |
| E01 Tài khoản | 01 → 02 → 03 → 04; dùng lại role/session/base; UC-57 quản lý nhân sự và UC-56 khoá tài khoản không phá luồng xác thực |
| E02 Onboarding courier | 08 → 09 → 10; privacy/blacklist/audit và quyền truy cập ảnh |
| E03 Giao hàng thành công | 11 → 12 → 54 → 16 → 17 → 35 → 25 → 26/27 → 28 → 36 → 47 → 49; sự kiện UC-53 và audit UC-60 |
| E04 Thương lượng | 18 → 19 (phản giá/chấp nhận) → 35; biên giá, hết hạn giữ chỗ và nhận đơn đồng thời |
| E05 Nạp tiền VNPay | 34 → 42 → 38; IPN trùng không cộng hai lần; truy vấn giao dịch quá hạn và đối soát UC-43 |
| E06 Rút tiền | 39 → 40 → 41; từ 5 triệu đồng cần 2 Admin; Mock/Manual Payout đúng scope và Ledger thật; hoàn bút toán khi từ chối/chi thất bại |
| E07 Khiếu nại | 50 → 51 → 52/37; đóng băng ngăn tự giải ngân; kháng nghị, thu hồi/nợ và hàng đợi quỹ FIFO |
| E08 Huỷ đơn | UC-15 theo từng thời điểm/trạng thái → UC-37/penalty UC-49; ký quỹ, số dư và khoá tài khoản đang có đơn chạy qua UC-56 |
| E09 Scope mở rộng | 20/21/22/23, 31/32/33, 44/45, 48/61/62; không bỏ Could/Should |
| E10 Backoffice/nền tảng | 46/53/55/56/57/59/60/63; permissions/privacy/audit/export/alerts/replay |

## 5. Điều kiện đóng dự án

- Từng UC được đối chiếu luồng chính, ngoại lệ, BR, API và SCR trong snapshot; không chỉ đếm endpoint hoặc screenshot.
- Cả 63 UC đều có bằng chứng FE/BE hoặc system handler phù hợp vai trò; UC hệ thống không cần tạo màn hình ngoài nguồn đặc tả.
- Domain mocks của đồng đội đã tắt trong staging nghiệm thu; Mock/Manual Payout và VNPay sandbox được giữ đúng scope demo theo Excel.
- Transaction/ledger/state/outbox và các trường hợp từ chối do thiếu quyền được kiểm tra trên SQL Server/Mongo thật, không chỉ H2.
- Có bằng chứng rõ về tests, UAT, cấu hình deploy/rollback và các vấn đề còn lại. Nếu thiếu provider thật, không đổi trạng thái “chờ tích hợp” thành “hoàn thành”.

## 6. Ma trận chính thức đủ 63 UC

| Thành viên | Số UC | UC phụ trách |
| --- | --- | --- |
| Lư Hữu Luân | 10 | UC-01, UC-02, UC-03, UC-04, UC-05, UC-06, UC-07, UC-08, UC-10, UC-62 |
| Trần Công Thuận | 15 | UC-09, UC-41, UC-45, UC-46, UC-49, UC-51, UC-52, UC-53, UC-54, UC-55, UC-56, UC-57, UC-59, UC-60, UC-63 |
| Nguyễn Thành Huy | 8 | UC-11, UC-12, UC-13, UC-14, UC-15, UC-32, UC-44, UC-58 |
| Võ Quốc An | 10 | UC-16, UC-17, UC-18, UC-19, UC-20, UC-21, UC-22, UC-23, UC-31, UC-33 |
| Huỳnh Phi Long | 10 | UC-24, UC-25, UC-26, UC-27, UC-28, UC-29, UC-30, UC-47, UC-48, UC-50 |
| Lê Nhựt Huy | 10 | UC-34, UC-35, UC-36, UC-37, UC-38, UC-39, UC-40, UC-42, UC-43, UC-61 |

| UC | Tên theo sheet 04 | Owner chính thức | Ưu tiên | Sprint nguồn | Hạn nguồn | Wave ghép đề xuất | Gói Thuận / đợt |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UC-01 | Đăng ký tài khoản | Lư Hữu Luân | Must | Sprint 1 | 2026-10-12 | W1 | Owner đồng đội |
| UC-02 | Xác thực OTP email/SĐT | Lư Hữu Luân | Must | Sprint 1 | 2026-10-12 | W1 | Owner đồng đội |
| UC-03 | Đăng nhập | Lư Hữu Luân | Must | Sprint 2 | 2026-10-19 | W1 | Owner đồng đội |
| UC-04 | Đăng xuất và quản lý phiên/thiết bị | Lư Hữu Luân | Must | Sprint 2 | 2026-10-19 | W1 | Owner đồng đội |
| UC-05 | Quên và đặt lại mật khẩu | Lư Hữu Luân | Must | Sprint 5 | 2026-11-13 | W4 | Owner đồng đội |
| UC-06 | Đổi mật khẩu | Lư Hữu Luân | Must | Sprint 3 | 2026-10-26 | W2 | Owner đồng đội |
| UC-07 | Quản lý hồ sơ cá nhân và sổ địa chỉ | Lư Hữu Luân | Must | Sprint 4 | 2026-11-02 | W3 | Owner đồng đội |
| UC-08 | Gửi hồ sơ xác minh danh tính (KYC) | Lư Hữu Luân | Must | Sprint 4 | 2026-11-02 | W3 | Owner đồng đội |
| UC-09 | Duyệt hồ sơ xác minh | Trần Công Thuận | Must | Sprint 1 | 2026-10-12 | W3 | P07 / T3 |
| UC-10 | Đăng ký làm Người giao | Lư Hữu Luân | Must | Sprint 3 | 2026-10-26 | W3 | Owner đồng đội |
| UC-11 | Ước tính giá và phí | Nguyễn Thành Huy | Must | Sprint 1 | 2026-10-12 | W1 | Owner đồng đội |
| UC-12 | Tạo đơn giao hàng | Nguyễn Thành Huy | Must | Sprint 1 | 2026-10-12 | W2 | Owner đồng đội |
| UC-13 | Xem danh sách và chi tiết đơn của tôi | Nguyễn Thành Huy | Must | Sprint 2 | 2026-10-19 | W2 | Owner đồng đội |
| UC-14 | Sửa đơn | Nguyễn Thành Huy | Must | Sprint 2 | 2026-10-19 | W2 | Owner đồng đội |
| UC-15 | Hủy đơn | Nguyễn Thành Huy | Must | Sprint 5 | 2026-11-13 | W4 | Owner đồng đội |
| UC-16 | Duyệt và tìm kiếm sàn đơn | Võ Quốc An | Must | Sprint 1 | 2026-10-12 | W2 | Owner đồng đội |
| UC-17 | Nhận đơn (giá niêm yết) | Võ Quốc An | Must | Sprint 1 | 2026-10-12 | W2 | Owner đồng đội |
| UC-18 | Gửi chào giá | Võ Quốc An | Must | Sprint 2 | 2026-10-19 | W2 | Owner đồng đội |
| UC-19 | Xử lý chào giá (chấp nhận, từ chối, phản giá) | Võ Quốc An | Must | Sprint 2 | 2026-10-19 | W2 | Owner đồng đội |
| UC-20 | Mời trực tiếp người giao | Võ Quốc An | Could | Sprint 4 | 2026-11-02 | W3 | Owner đồng đội |
| UC-21 | Gợi ý ghép đơn–chuyến | Võ Quốc An | Should | Sprint 4 | 2026-11-02 | W3 | Owner đồng đội |
| UC-22 | Đăng chuyến đi | Võ Quốc An | Should | Sprint 3 | 2026-10-26 | W2 | Owner đồng đội |
| UC-23 | Quản lý chuyến đi | Võ Quốc An | Should | Sprint 3 | 2026-10-26 | W2 | Owner đồng đội |
| UC-24 | Chat theo đơn | Huỳnh Phi Long | Must | Sprint 1 | 2026-10-12 | W2 | Owner đồng đội |
| UC-25 | Xác nhận lấy hàng (QR + ảnh) | Huỳnh Phi Long | Must | Sprint 3 | 2026-10-26 | W2 | Owner đồng đội |
| UC-26 | Chia sẻ vị trí và cập nhật hành trình | Huỳnh Phi Long | Must | Sprint 1 | 2026-10-12 | W2 | Owner đồng đội |
| UC-27 | Theo dõi đơn thời gian thực | Huỳnh Phi Long | Must | Sprint 2 | 2026-10-19 | W2 | Owner đồng đội |
| UC-28 | Xác nhận giao hàng (OTP/QR + ảnh) | Huỳnh Phi Long | Must | Sprint 2 | 2026-10-19 | W2 | Owner đồng đội |
| UC-29 | Báo sự cố khi giao | Huỳnh Phi Long | Must | Sprint 3 | 2026-10-26 | W3 | Owner đồng đội |
| UC-30 | Giao thất bại và hoàn hàng | Huỳnh Phi Long | Must | Sprint 4 | 2026-11-02 | W3 | Owner đồng đội |
| UC-31 | Gộp đơn và gợi ý lộ trình | Võ Quốc An | Could | Sprint 5 | 2026-11-13 | W4 | Owner đồng đội |
| UC-32 | Tạo đơn giao tiếp sức | Nguyễn Thành Huy | Could | Sprint 4 | 2026-11-02 | W4 | Owner đồng đội |
| UC-33 | Bàn giao giữa các chặng | Võ Quốc An | Could | Sprint 5 | 2026-11-10 | W4 | Owner đồng đội |
| UC-34 | Nạp tiền vào ví qua VNPay | Lê Nhựt Huy | Must | Sprint 1 | 2026-10-12 | W2 | Owner đồng đội |
| UC-35 | Thanh toán đơn và ký quỹ | Lê Nhựt Huy | Must | Sprint 1 | 2026-10-12 | W2 | Owner đồng đội |
| UC-36 | Giải ngân cho người giao | Lê Nhựt Huy | Must | Sprint 2 | 2026-10-19 | W2 | Owner đồng đội |
| UC-37 | Hoàn tiền | Lê Nhựt Huy | Must | Sprint 5 | 2026-11-13 | W4 | Owner đồng đội |
| UC-38 | Xem ví và lịch sử giao dịch | Lê Nhựt Huy | Must | Sprint 3 | 2026-10-26 | W2 | Owner đồng đội |
| UC-39 | Quản lý tài khoản ngân hàng nhận tiền | Lê Nhựt Huy | Must | Sprint 4 | 2026-11-02 | W3 | Owner đồng đội |
| UC-40 | Yêu cầu rút tiền | Lê Nhựt Huy | Must | Sprint 3 | 2026-10-26 | W3 | Owner đồng đội |
| UC-41 | Duyệt và chi trả rút tiền | Trần Công Thuận | Must | Sprint 2 | 2026-10-19 | W3 | P11 / T4 |
| UC-42 | Xử lý IPN và kết quả từ VNPay | Lê Nhựt Huy | Must | Sprint 2 | 2026-10-19 | W1 | Owner đồng đội |
| UC-43 | Đối soát giao dịch hằng ngày | Lê Nhựt Huy | Should | Sprint 4 | 2026-11-02 | W3 | Owner đồng đội |
| UC-44 | Áp dụng mã khuyến mãi | Nguyễn Thành Huy | Could | Sprint 3 | 2026-10-26 | W2 | Owner đồng đội |
| UC-45 | Quản lý khuyến mãi | Trần Công Thuận | Could | Sprint 3 | 2026-10-26 | W2 | P06 / T3 |
| UC-46 | Cấu hình tham số hệ thống và biểu phí | Trần Công Thuận | Must | Sprint 1 | 2026-10-12 | W1 | P01 / T1 |
| UC-47 | Đánh giá hai chiều | Huỳnh Phi Long | Must | Sprint 4 | 2026-11-02 | W3 | Owner đồng đội |
| UC-48 | Xem hồ sơ công khai và điểm uy tín | Huỳnh Phi Long | Must | Sprint 5 | 2026-11-13 | W4 | Owner đồng đội |
| UC-49 | Tính điểm uy tín và xếp hạng | Trần Công Thuận | Must | Sprint 2 | 2026-10-19 | W3 | P10 / T3 |
| UC-50 | Tạo khiếu nại | Huỳnh Phi Long | Must | Sprint 5 | 2026-11-13 | W4 | Owner đồng đội |
| UC-51 | Xử lý khiếu nại và phán quyết | Trần Công Thuận | Must | Sprint 3 | 2026-10-26 | W4 | P12 / T4 |
| UC-52 | Xét duyệt bồi thường từ Quỹ bảo đảm | Trần Công Thuận | Must | Sprint 4 | 2026-11-02 | W4 | P13 / T4 |
| UC-53 | Nhận và cấu hình thông báo | Trần Công Thuận | Must | Sprint 1 | 2026-10-12 | W1 | P05 / T2 |
| UC-54 | Kiểm duyệt đơn hàng | Trần Công Thuận | Must | Sprint 2 | 2026-10-19 | W2 | P08 / T3 |
| UC-55 | Báo cáo vi phạm | Trần Công Thuận | Must | Sprint 4 | 2026-11-02 | W3 | P09 / T3 |
| UC-56 | Khóa và mở khóa tài khoản | Trần Công Thuận | Must | Sprint 4 | 2026-11-02 | W3 | P04 / T2 |
| UC-57 | Quản lý người dùng và nhân sự | Trần Công Thuận | Must | Sprint 3 | 2026-10-26 | W3 | P03 / T2 |
| UC-58 | Quản lý danh mục | Nguyễn Thành Huy | Must | Sprint 3 | 2026-10-26 | W2 | Owner đồng đội |
| UC-59 | Dashboard và báo cáo | Trần Công Thuận | Must | Sprint 5 | 2026-11-10 | W4 | P14 / T4 |
| UC-60 | Xem nhật ký kiểm toán | Trần Công Thuận | Should | Sprint 5 | 2026-11-10 | W1 | P02 / T2 |
| UC-61 | Xem thu nhập và thống kê | Lê Nhựt Huy | Should | Sprint 5 | 2026-11-13 | W4 | Owner đồng đội |
| UC-62 | Huy hiệu, thưởng và giới thiệu bạn bè | Lư Hữu Luân | Could | Sprint 5 | 2026-11-10 | W4 | Owner đồng đội |
| UC-63 | Xử lý tác vụ định kỳ | Trần Công Thuận | Must | Sprint 5 | 2026-11-13 | W4 | P15 / T4 |

W là lần ghép mục tiêu, không phải bằng chứng đã tích hợp. T5/W5 kiểm chứng lại toàn bộ phạm vi. Danh sách được đối chiếu trực tiếp 63 UC duy nhất trong Excel, không thiếu hoặc trùng.

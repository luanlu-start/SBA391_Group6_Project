# Tiến độ triển khai phần Thuận

Cập nhật ngày 15/10/2026; baseline audit 13/10. P00/P01 đã bàn giao ngày 14/10; P02/UC-60 hoàn tất phạm vi tra cứu và retention ngày 15/10. Nhánh hiện tại `feature/UC-60-ThuanTC-tra-cuu-audit` kế thừa nền UC-46 tại cef0f4e. Bằng chứng: [ROUND1-HANDOFF.md](ROUND1-HANDOFF.md), [UC60-HANDOFF.md](UC60-HANDOFF.md). Trạng thái commit/remote tra bằng Git.

| Gói | UC | Đợt mục tiêu | Trạng thái | Provider còn giả lập | Bằng chứng / bước tiếp |
| --- | --- | --- | --- | --- | --- |
| P00 | Nền | T1 | VERIFIED | Không: auth/session/ADMIN guard, SQL outbox và Mongo audit thật | Backend 149 tests PASS; FE typecheck/lint/build PASS; SQL/Mongo và browser UAT PASS; workflow CI đã tạo, chưa chạy trên GitHub |
| P01 | UC-46 | T1 | READY_WITH_MOCK | C02 PricingSimulationPort = REFERENCE ở dev/demo; chờ Pricing của Nguyễn Thành Huy | 109 params, edit/batch/history/version/snapshot thật; 20 ca giá nguồn PASS; preview không ghi SQL; chưa tích hợp Order/Identity consumers |
| P02 | UC-60 | T2 | VERIFIED | Không: query Mongo, auth và producer UC-46 thật | 200 tests thường + 6 live SQL/Mongo PASS; frontend checks và browser UAT PASS; retention/index có kiểm tra; producer KYC/Order/Ledger/role tiếp tục theo UC của owner |
| P03 | UC-57 | T2 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P04 | UC-56 | T2 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P05 | UC-53 | T2 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P06 | UC-45 | T3 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P07 | UC-09 | T3 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P08 | UC-54 | T3 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P09 | UC-55 | T3 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P10 | UC-49 | T3 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P11 | UC-41 | T4 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P12 | UC-51 | T4 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P13 | UC-52 | T4 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P14 | UC-59 | T4 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |
| P15 | UC-63 | T4 | NOT_STARTED | Chưa cấu hình | Chưa thực hiện |

T5: intake adapter thật, integration/UAT và cập nhật VERIFIED cho từng gói khi đủ kiểm tra. Trạng thái hợp lệ: NOT_STARTED, IN_PROGRESS, READY_WITH_MOCK, WAITING_FOR_INTEGRATION, INTEGRATED, VERIFIED.

Khi cập nhật mỗi gói, bổ sung ngày/branch/commit nếu có, file đổi, acceptance đã kiểm, lệnh và kết quả kiểm tra, thời gian thực tế, provider mode, upstream còn chờ và lỗi còn lại. Chỉ P00 nền được phép không có mã UC.

Đợt 1 thực hiện trong ngày 14/10, sớm hơn cửa sổ dự kiến 14–16/10. P02 thực hiện ngày 15/10, sớm hơn cửa sổ T2 17–23/10. Không quy đổi thời gian agent thành số giờ lao động của Thuận. Bước tiếp theo là P03/UC-57, sau đó P04/UC-56; giữ các mốc tích hợp T2–T5 và không đổi phân công đồng đội.

VERIFIED của P02 áp dụng API/UI tra cứu, quyền, masking, index/retention và luồng capture UC-46 đã kiểm thử. Chưa xác nhận mọi hành động nhạy cảm của toàn dự án đã có producer audit; coverage BR-N-026, KYC và lịch sử trạng thái đơn tiếp tục được tích hợp/kiểm tra tại UC của từng owner.

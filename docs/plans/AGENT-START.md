# Prompt bắt đầu triển khai

Copy phần dưới cho coding agent làm việc trong repository này.

---

Tôi là **Trần Công Thuận**. Hãy triển khai **P00 và P01 / UC-46** theo `docs/plans/THUAN-IMPLEMENTATION-PLAN.md`. Đây là yêu cầu thực hiện code cho đợt đầu, không chỉ phân tích.

Đọc theo thứ tự:

1. `AGENTS.md` áp dụng nếu có, rồi `docs/plans/README.md`.
2. `docs/plans/THUAN-IMPLEMENTATION-PLAN.md`, đặc biệt P00/P01 và Definition of Done.
3. `docs/plans/CONTRACTS-AND-INTEGRATION.md`, contract C01/C02 và cơ chế mock.
4. `docs/plans/SOURCE-TRACEABILITY.md`, `plan-manifest.json`, và Excel `docs/plans/sources/applied-br-uc.xlsx` khi cần đối chiếu. Đây là bản chính thức, không dùng phân công PAY cũ hoặc workbook cũ của repo.
5. Quy ước hiện tại trong `docs/CONTRIBUTING.md`, `docs/API-ERRORS.md`, các lớp Identity, UI và API client hiện có. Đọc SPEC mục tham số/giá/transaction/outbox làm thiết kế tham chiếu; Excel thắng nếu khác nghiệp vụ.

Trước khi sửa, kiểm tra branch, working tree và implementation hiện tại. Tôi chưa code phần Thuận khi plan được lập, nhưng agent phải kiểm tra lại vì checkout có thể đã thay đổi. Giữ các thay đổi sẵn có của người dùng, đặc biệt không ghi đè/xóa `HANDOFF.md`, `.env`, cấu hình SQL Server chung hoặc source của đồng đội. Không commit/push/merge/deploy trong yêu cầu đợt này.

Đầu ra phải là một lát cắt FE–BE–database chạy được:

- Endpoint UC-46 đúng sheet 10: `GET /api/v1/admin/params`, `PUT /api/v1/admin/params/{key}`, `POST /api/v1/admin/params/simulate`.
- SQL `system_parameters`/`parameter_history` theo source; seed đủ 109 PRM đúng key/kiểu/đơn vị/giá trị. Seed chỉ thêm thiếu, không reset cấu hình đã sửa. Tham số kiểu chuỗi tổng hợp giữ đủ ngữ nghĩa.
- Quản lý ADMIN, validation, optimistic concurrency, cập nhật và lịch sử chung transaction; snapshot tham số cho consumer sau này. Không sửa ngược đơn hiện hữu.
- API simulation có request/response được ghi thành contract, tái sử dụng pricing thật nếu đã có; nếu chưa có dùng pricing adapter giả lập với phép tính tham chiếu Excel và nhận được input tùy ý cùng bộ tham số đề xuất. 20 ca sheet 07 là dữ liệu kiểm chứng, không phải bảng tra kết quả cố định. Không lập module Order/Pricing trùng với Thành Huy; production không được dùng adapter giả.
- Giao diện `/admin/params`, layout/components có sẵn, tìm nhóm/key, editor đúng loại dữ liệu, trước/sau, preview và xác nhận; đủ loading/error/empty/success và xử lý conflict.
- Nền audit capture/outbox tối thiểu cho hành động thay đổi tham số; Mongo không tham gia transaction SQL. Actor/correlationId và dữ liệu nhạy cảm được xử lý đúng. Không làm sẵn toàn bộ hệ thống jobs, chat hay admin cho các UC chưa được yêu cầu.
- Interface chỉ tạo khi P01 thực sự sử dụng. Mock chỉ profile rõ ràng, dữ liệu riêng, không fallback khi API thật lỗi. Auth của ứng dụng vẫn thật và kiểm tra quyền tại backend.

Nhánh chức năng theo quy định nhóm: `feature/<ma-uc>-<ma-tv>-<mo-ta>`; đợt UC-46 dùng `feature/UC-46-ThuanTC-cau-hinh-tham-so`. Không dùng prefix `codex/` hoặc tên không liên quan. Chỉ sửa vùng cần cho P00/P01 và tài liệu contract/test đi kèm. Nếu có interface của đồng đội, dùng interface đó thay cho tên đề xuất trong plan.

Viết và chạy test cho hành vi thực: role/validation, seed không ghi đè, rollback/history, version conflict, preview không lưu, 20 ca biểu phí với adapter đang dùng, thay một tham số đề xuất làm preview thay đổi nhưng không lưu cấu hình, outbox retry/dedupe khi Mongo lỗi. Kiểm tra schema/query cần thiết trên SQL Server test riêng, không tạo/xóa database đang chứa dữ liệu chung. Chạy backend tests liên quan và frontend typecheck/lint/build theo toolchain của repo. Ghi riêng kiểm tra nào chưa chạy hoặc simulation còn là mock; không báo PASS nếu chưa có bằng chứng.

Cập nhật trạng thái P00/P01 trong plan bằng kết quả thực tế. Báo cáo file đổi, kết quả kiểm tra, endpoints/UI demo, adapter còn giả lập và contract bàn giao cho Thành Huy. Hoàn tất P00/P01 trước; các P02–P15 là backlog cho lượt giao việc sau.

---

## Prompt giao gói tiếp theo

> Triển khai Pxx trong `docs/plans/THUAN-IMPLEMENTATION-PLAN.md`, dùng Excel chính thức trong gói plan. Kiểm tra prerequisites và source hiện tại; làm đủ BE/FE/validation/negative cases của gói. Nếu upstream chưa có, dùng contract + adapter giả lập theo `CONTRACTS-AND-INTEGRATION.md` và báo `READY_WITH_MOCK`. Không nhận UC của đồng đội, không fallback mock trên production. Tích hợp adapter thật nếu đã bàn giao và chạy kiểm tra tương ứng; cập nhật tiến độ bằng bằng chứng thực tế.

## Prompt đổi mock sang module thật

> Tích hợp Pxx bằng implementation mới của đồng đội. Đọc diff và contract thực tế; cập nhật adapter/contract tests, giữ service và UI hiện có nếu contract không đổi. Kiểm tra quyền, state/version, transaction, idempotency và failure cases trên SQL Server/Mongo test. Chạy cùng bộ scenario trước đây dùng mock trên adapter thật; xác minh domain mocks đã tắt. Chỉ chuyển sang `INTEGRATED`/`VERIFIED` khi có bằng chứng; liệt kê phần còn chờ upstream.

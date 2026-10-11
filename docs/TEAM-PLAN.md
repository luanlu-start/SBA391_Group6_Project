# Phân công triển khai FPTPost

Mỗi thành viên phụ trách cả frontend và backend của nhóm chức năng được giao:
màn hình, API, validation, phân quyền, migration, kiểm thử và tài liệu liên quan.
Hữu Luân phụ trách Identity và điều phối tích hợp. Các thành viên cùng review code,
kiểm thử luồng xuyên suốt và sửa lỗi trong phần mình phụ trách.

Mục tiêu khởi động là cả 6 người cùng triển khai. Các module có phụ thuộc nghiệp vụ
khi chạy luồng thật; trong lúc code, contract và mock/fixture cho phép từng người
làm phần của mình mà chưa cần implementation của module khác hoàn tất.

Mã UC và mức ưu tiên lấy từ [danh mục Use Case](USE-CASES.md).
Đây là kế hoạch triển khai của team; các cột BE/FE trong bản tài liệu nguồn được
giữ nguyên để truy vết. Lịch 6 sprint bên dưới là đề xuất theo khung 12 tuần trong
SPEC, cần điều chỉnh theo tiến độ thực tế.

## Phân công chức năng

| Phụ trách | Nhóm chức năng FE + BE | UC | Must / Should / Could |
| --- | --- | --- | --- |
| Hữu Luân | Identity: đăng ký, OTP, đăng nhập/phiên, mật khẩu, hồ sơ/sổ địa chỉ, KYC, đăng ký người giao; khóa tài khoản và quản lý người dùng/nhân sự | UC-01–UC-10, UC-56–UC-57 | 12 / 0 / 0 |
| Lê Huy | Catalog/Pricing: danh mục, tham số/biểu phí, ước tính giá, khuyến mãi; đánh giá/uy tín, dashboard, audit, thống kê thu nhập và huy hiệu | UC-11, UC-44–UC-49, UC-58–UC-62 | 7 / 2 / 3 |
| Quốc An | Order/Dispute: tạo, xem, sửa, hủy đơn; kiểm duyệt, báo cáo vi phạm, khiếu nại, phán quyết và bồi thường | UC-12–UC-15, UC-50–UC-52, UC-54–UC-55 | 9 / 0 / 0 |
| Công Thuận | Ledger/Payment: ví/sổ cái, VNPay, ký quỹ, giải ngân, hoàn tiền, tài khoản ngân hàng, rút/duyệt rút và đối soát | UC-34–UC-43 | 9 / 1 / 0 |
| Phi Long | Market/Trip: sàn đơn, nhận đơn, chào giá/phản giá, mời người giao, chuyến đi và ghép đơn; gộp đơn/giao tiếp sức | UC-16–UC-23, UC-31–UC-33 | 4 / 3 / 4 |
| Nguyễn Huy | Messaging/Tracking: chat, thông báo, lấy/giao hàng QR/OTP/ảnh, vị trí/theo dõi, sự cố, giao thất bại/hoàn hàng và tác vụ định kỳ | UC-24–UC-30, UC-53, UC-63 | 9 / 0 / 0 |

Tổng cộng: 63 UC, gồm 50 Must, 6 Should, 7 Could; mỗi UC có một người chịu trách nhiệm chính.
Số UC không đại diện cho độ khó: tiền, cạnh tranh nhận đơn và giao nhận có nhiều
ngoại lệ cần kiểm thử. Lê Huy và Phi Long ưu tiên xong phần Must, sau đó hỗ trợ
review/tích hợp cho các phần nặng trước khi bắt đầu Could.

Công Thuận phụ trách xuyên suốt Payment để tận dụng kinh nghiệm tích hợp VNPay.
Hữu Luân giữ Identity và contract xác thực chung. Phân công bốn nhóm còn lại dựa
trên ranh giới dữ liệu và khả năng triển khai song song. Điều chỉnh khối lượng sau
đợt tích hợp đầu theo tiến độ thực tế.

## Khởi động đồng thời

Trước đợt code đầu, dành một buổi để review/tích hợp base UC-01, thống nhất các
contract tối thiểu bên dưới và tạo các nhánh từ cùng một mốc `develop`.
Mỗi người bàn giao sớm interface/DTO và migration nền mình sở hữu trong PR nhỏ;
các bảng được tham chiếu và thứ tự foreign key cần được review/tích hợp trước
khi chạy test database chung. Không cần triển khai hết nghiệp vụ mới công bố contract.

## Task đầu tiên cho cả 6 người

Các dòng dưới được bắt đầu cùng lúc, không phải sáu bước làm nối tiếp.
Mỗi người làm màn hình và API của phần mình, kèm kiểm thử phù hợp.

| Phụ trách | Task hiện tại | Phần FE + BE tự triển khai | Đầu vào để tự kiểm thử |
| --- | --- | --- | --- |
| Hữu Luân | UC-02, tiếp UC-03/04 | Nhập/gửi lại OTP, kích hoạt tài khoản; login/JWT/phiên và contract người dùng hiện tại | Tài khoản/OTP từ UC-01, mail adapter hiện có; chưa cần Order hoặc Payment |
| Lê Huy | UC-58, tiếp UC-46 | CRUD loại hàng trước; danh mục, tham số/biểu phí và màn hình quản trị; công bố contract cho UC-11 | Seed danh mục/tham số; mock người dùng ADMIN trong test, chưa cần có đơn hàng |
| Quốc An | UC-12/13: phần tạo và xem đơn | Entity/migration, form tạo đơn, danh sách/chi tiết, validation và contract đọc/chuyển trạng thái đơn | Fixture người gửi; mock facade danh mục/tính giá theo contract trong test khi nhà cung cấp chưa xong |
| Công Thuận | Nền ledger + UC-39 và phần đọc UC-38; chuẩn bị VNPay sandbox cho UC-34/42 | Ví, lịch sử giao dịch, tài khoản ngân hàng và màn hình tương ứng; chuẩn bị thông tin tích hợp sandbox; công bố contract ký quỹ/hoàn/giải ngân | Fixture tài khoản, ví/bút toán trong database test; chưa cần đơn đã giao hoặc VNPay thật |
| Phi Long | Phần CRUD UC-22/23, tiếp UC-16 | Form đăng chuyến, danh sách/chi tiết, cập nhật theo trạng thái; sau đó giao diện/API sàn, tìm kiếm/lọc/phân trang theo contract Order | Fixture người giao đủ điều kiện để test Trip; mock facade đọc đơn khi làm Market; không dựng lại entity/bảng Order |
| Nguyễn Huy | Nền thông báo/outbox + phần đầu UC-53/63 | Danh sách/đọc/cấu hình thông báo; worker đọc sự kiện thử, retry/chống trùng; công bố contract lưu tệp | Fixture thông báo và sự kiện test; chưa cần Order/Payment phát sự kiện thật |

Phi Long bắt đầu phần CRUD chuyến đi để có thể code độc lập với Order. UC-22/23
thuộc Should; chỉ làm phần nền trong đợt đầu và công bố contract sàn sớm với Quốc An,
sau đó ưu tiên UC-16/17/18 trước ghép chuyến và các phần Could. Luồng Trip thật vẫn
phải kiểm tra xác thực, quyền sở hữu và điều kiện người giao theo tài liệu.

Công Thuận chuẩn bị kết nối VNPay sandbox song song với nền ví/sổ cái. Khi triển
khai UC-34/42, xác minh kết quả thanh toán và chống xử lý lặp trước khi ghi bút toán;
không cộng tiền chỉ theo URL quay về frontend.

Mock được đặt trong test hoặc chế độ phát triển riêng, theo contract của module
cung cấp. Frontend có thể dùng mock HTTP; backend dùng mock interface/facade khi
unit/component test. Khi tích hợp phải nối implementation thật, migration thật
và quyền thật. Không tạo token giả, tự bỏ phân quyền trong luồng thật hoặc dùng
mock làm fallback khi database/dịch vụ lỗi. Kết quả chạy với mock là tiến độ phần
việc riêng, chưa phải nghiệm thu toàn bộ UC.

Ví dụ Market cần đọc đơn: hai người chốt DTO, bộ lọc và contract đọc trước.
Người Order triển khai nguồn dữ liệu thật; người Market đồng thời làm màn hình,
API và kiểm thử với mock facade. Khi cả hai sẵn sàng, thay phần test/mock bằng
adapter thật trong cấu hình tích hợp và kiểm tra quyền/dữ liệu xuyên suốt.

Mỗi người dùng database local/test riêng và seed dữ liệu phục vụ nhóm chức năng
của mình. Các bảng dùng chung lấy từ migration đã thống nhất, không tự tạo bản
User/Role/Order khác. Chỉ sửa module được giao; thay đổi `common`, JWT, contract
response hoặc migration có phụ thuộc cần review để cả nhóm cập nhật cùng nhau.

Sau đợt đầu: Lê Huy làm UC-11; Quốc An nối giá/danh mục thật và tiếp
UC-14/54; Công Thuận làm UC-34/42 rồi UC-35; Phi Long tích hợp UC-16 rồi
UC-17/18/19; Nguyễn Huy nối sự kiện thật, bổ sung upload và UC-24 trước giao nhận.
Hữu Luân tiếp tục AUTH/hồ sơ/KYC và nghiệm thu phần tích hợp với từng người.

UC-42 và một phần UC-63 là xử lý hệ thống, không cần tạo màn hình riêng chỉ để
có frontend. Người phụ trách vẫn làm giao diện ví/thanh toán hoặc thông báo/chat
thuộc nhóm chức năng của mình.

## Những điểm cần thống nhất trước khi code song song

Leader tổ chức một buổi chốt contract với cả nhóm, sau đó mỗi người cập nhật phần
API/DTO/schema mình sở hữu để người khác triển khai theo cùng quy ước.

- Identity: public user ID, role, trạng thái tài khoản/người giao, JWT và cách lấy
  người dùng hiện tại; kiểm tra quyền sở hữu/tham gia tài nguyên.
- Catalog/Pricing: ID danh mục, tham số/biểu phí, request ước tính giá và kết quả
  snapshot. Mã tiền VND dùng `long`/`BIGINT` theo thiết kế.
- Order: public order ID, trạng thái và các chuyển trạng thái hợp lệ theo sheet
  `08_Trạng_thái`; contract lịch sử và lệnh thao tác đơn.
- Ledger: đầu vào/kết quả ký quỹ, giải ngân, hoàn/rút; idempotency, trạng thái
  giao dịch và cách xử lý thất bại. Chỉ Ledger ghi bút toán/số dư.
- Tệp/sự kiện: file ID, quyền upload/đọc, giới hạn theo SPEC; tên sự kiện, payload,
  event ID, thời điểm và quy tắc retry/chống trùng.
- Common: giữ một contract response/phân trang, Problem Details, correlation ID,
  chiến lược ID và cách quản lý migration đã thống nhất trong repository.

Các module gọi facade/interface hoặc nhận sự kiện; không đọc/ghi repository nội bộ
của module khác. Người phụ trách giao diện quản trị vẫn gọi nghiệp vụ của module
sở hữu dữ liệu, ví dụ khóa tài khoản qua Identity hoặc bồi thường qua Ledger.

## Phụ thuộc khi tích hợp

| Luồng | Các phần phải sẵn sàng |
| --- | --- |
| Tạo và mở đơn lên sàn | Identity xác thực/phân quyền; danh mục + giá; Order và kiểm duyệt theo quy tắc |
| Nhận đơn/chấp nhận chào giá | Người giao đủ điều kiện KYC; đơn hợp lệ; Market phối hợp Order và ký quỹ Ledger; chống hai người nhận cùng đơn |
| Lấy/giao hàng | Đơn đã phân công hợp lệ; token/QR/OTP/ảnh; Tracking gọi lệnh của Order và phát sự kiện |
| Giải ngân/hoàn tiền | Trạng thái và điều kiện Order/Dispute; Ledger xử lý idempotent theo quy tắc, không chỉ dựa vào nút trên frontend |
| Uy tín, dashboard, thu nhập/audit | Dữ liệu đã phát sinh từ Order/Payment/Tracking; truy vấn hoặc sự kiện do module sở hữu cung cấp |

UC-02 tiếp theo của Hữu Luân là xác thực đăng ký. OTP giao hàng ở UC-28 do Tracking
sở hữu: khác mục đích, quyền kiểm tra và luồng sử dụng, không dùng challenge đăng ký
để xác nhận giao hàng. Email dùng lại service/template trong `common/mail`.

## Thứ tự nghiệm thu theo 6 sprint

1. **Sprint 1:** hoàn thiện AUTH, chốt contract; các thành viên khác dựng phần nền
   của nhóm mình, danh mục/tham số/giá, Order, ledger, sàn, lưu tệp/outbox.
2. **Sprint 2:** hồ sơ/KYC, tạo/xem/sửa đơn, ví và VNPay sandbox; tích hợp giá,
   thông báo và dữ liệu cần cho sàn. Ưu tiên có thể tạo tài khoản và đơn thật.
3. **Sprint 3:** đăng ký người giao, kiểm duyệt/mở sàn, nhận đơn/chào giá, ký quỹ,
   chat; nghiệm thu từ tài khoản đã xác thực tới đơn đã phân công hợp lệ.
4. **Sprint 4:** lấy hàng/giao hàng, giải ngân, hủy/hoàn tiền và yêu cầu rút tiền;
   nghiệm thu một đơn từ đầu tới cuối cùng các ngoại lệ ưu tiên.
5. **Sprint 5:** quản trị người dùng, xử lý vi phạm/khiếu nại, duyệt rút/bồi thường,
   sự cố/giao thất bại/hoàn hàng, đánh giá/uy tín; hoàn thiện phần Must còn thiếu.
6. **Sprint 6:** chốt toàn bộ Must; bổ sung Should khi đủ nguồn lực, báo cáo,
   đối soát/audit, kiểm thử cạnh tranh và hồi quy, tài liệu/demo. Could xếp sau
   Must/Should; không coi tính năng chưa code là đã hoàn thành.

Thứ tự sprint là mốc tích hợp/nghiệm thu; các nhóm vẫn phát triển song song theo
task đầu tiên và contract đã chốt, không chờ một nhóm hoàn thành cả module.

Luồng demo chung: đăng ký/xác thực → KYC người giao → tạo/kiểm duyệt đơn → sàn/nhận đơn
→ ký quỹ/phân công → lấy hàng → giao hàng → giải ngân → đánh giá.
Thứ tự chính xác của ký quỹ và các chuyển trạng thái phải theo thiết kế, không tự
rút gọn kiểm tra để làm demo.

## Trách nhiệm Leader và cách bàn giao

Leader tiếp tục UC-02, sau đó UC-03/04; công bố contract AUTH sớm để các nhóm
tích hợp. Chốt giao tiếp giữa module, quản lý thứ tự migration, review các thay đổi
về quyền/trạng thái/tiền và tổ chức kiểm tra luồng chung cuối mỗi sprint.
Leader điều phối review; mỗi thành viên chịu trách nhiệm sửa, test và tài liệu của
phần mình, tránh dồn toàn bộ kiểm thử/tích hợp cho Leader.

Mỗi UC đi qua nhánh `feature/UC-xx-mo-ta`, PR vào `develop`; ví dụ
`feature/UC-02-xac-thuc-otp`. Chia PR vừa đủ review, kèm migration nếu đổi schema,
endpoint/màn hình, BR liên quan và bằng chứng kiểm thử. Không sửa migration đã
chạy hoặc tự đổi các contract dùng chung trong một nhánh chức năng.

Hoàn thành một UC khi luồng chính và ngoại lệ liên quan chạy được FE–API–database,
quyền/validation đúng, test thích hợp đạt, tài liệu/Swagger cập nhật và PR được review.
Luồng tiền, cạnh tranh nhận đơn và trạng thái cần thêm kiểm thử chống trùng/cạnh tranh
và kiểm tra database thật; SQL/Mongo/outbox không tự tạo một transaction phân tán.

UC-01 hiện đã có code và kiểm thử trên nhánh làm việc; các thay đổi mới cần được
review/tích hợp vào `develop` trước khi dùng làm base chung cho nhóm.

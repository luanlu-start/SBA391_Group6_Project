# Truy vết nguồn và các quyết định triển khai

Nguồn nghiệp vụ/phân công: [applied-br-uc.xlsx](sources/applied-br-uc.xlsx), bản sao giữ nguyên toàn bộ byte của Excel được người dùng xác nhận. Sheet 15 quyết định người phụ trách/sprint; sheet 04 cung cấp luồng/priority/BR; sheet 03 cung cấp tiêu chí nghiệm thu; sheet 06 cung cấp PRM; sheet 08 cung cấp state; sheet 09 cung cấp dữ liệu; sheet 10 cung cấp API; sheet 11 cung cấp SCR. SPEC là thiết kế tham chiếu, không thay thế nguồn Excel.

## 1. Đối chiếu phân công Thuận

| Gói | UC / tên | Ưu tiên | Sprint nguồn | Hạn nguồn | Đợt mới | Ô phân công |
| --- | --- | --- | --- | --- | --- | --- |
| P01 | UC-46: Cấu hình tham số hệ thống và biểu phí | Must | Sprint 1 | 2026-10-12 | T1 | 15_Chia task(UC)!D14:I14 |
| P02 | UC-60: Xem nhật ký kiểm toán | Should | Sprint 5 | 2026-11-10 | T2 | 15_Chia task(UC)!D67:I67 |
| P03 | UC-57: Quản lý người dùng và nhân sự | Must | Sprint 3 | 2026-10-26 | T2 | 15_Chia task(UC)!D43:I43 |
| P04 | UC-56: Khóa và mở khóa tài khoản | Must | Sprint 4 | 2026-11-02 | T2 | 15_Chia task(UC)!D42:I42 |
| P05 | UC-53: Nhận và cấu hình thông báo | Must | Sprint 1 | 2026-10-12 | T2 | 15_Chia task(UC)!D40:I40 |
| P06 | UC-45: Quản lý khuyến mãi | Could | Sprint 3 | 2026-10-26 | T3 | 15_Chia task(UC)!D57:I57 |
| P07 | UC-09: Duyệt hồ sơ xác minh | Must | Sprint 1 | 2026-10-12 | T3 | 15_Chia task(UC)!D38:I38 |
| P08 | UC-54: Kiểm duyệt đơn hàng | Must | Sprint 2 | 2026-10-19 | T3 | 15_Chia task(UC)!D41:I41 |
| P09 | UC-55: Báo cáo vi phạm | Must | Sprint 4 | 2026-11-02 | T3 | 15_Chia task(UC)!D61:I61 |
| P10 | UC-49: Tính điểm uy tín và xếp hạng | Must | Sprint 2 | 2026-10-19 | T3 | 15_Chia task(UC)!D58:I58 |
| P11 | UC-41: Duyệt và chi trả rút tiền | Must | Sprint 2 | 2026-10-19 | T4 | 15_Chia task(UC)!D39:I39 |
| P12 | UC-51: Xử lý khiếu nại và phán quyết | Must | Sprint 3 | 2026-10-26 | T4 | 15_Chia task(UC)!D59:I59 |
| P13 | UC-52: Xét duyệt bồi thường từ Quỹ bảo đảm | Must | Sprint 4 | 2026-11-02 | T4 | 15_Chia task(UC)!D60:I60 |
| P14 | UC-59: Dashboard và báo cáo | Must | Sprint 5 | 2026-11-10 | T4 | 15_Chia task(UC)!D66:I66 |
| P15 | UC-63: Xử lý tác vụ định kỳ | Must | Sprint 5 | 2026-11-13 | T4 | 15_Chia task(UC)!D44:I44 |

## 2. API và màn hình theo Excel

| API | UC | Method | Path sau /api/v1 | Quyền | Mô tả |
| --- | --- | --- | --- | --- | --- |
| API-024 | UC-09 | GET | /mod/kyc | MODERATOR | Hàng đợi hồ sơ KYC (phân trang, lọc) |
| API-025 | UC-09 | GET | /mod/kyc/{id} | MODERATOR | Chi tiết hồ sơ và ảnh (liên kết có thời hạn) |
| API-026 | UC-09 | POST | /mod/kyc/{id}/approve | MODERATOR | Duyệt hồ sơ |
| API-027 | UC-09 | POST | /mod/kyc/{id}/reject | MODERATOR | Từ chối kèm lý do |
| API-100 | UC-41 | GET | /admin/withdrawals | ADMIN | Hàng đợi rút tiền |
| API-101 | UC-41 | POST | /admin/withdrawals/{id}/approve | ADMIN | Duyệt (cấp 1/cấp 2) |
| API-102 | UC-41 | POST | /admin/withdrawals/{id}/reject | ADMIN | Từ chối kèm lý do |
| API-103 | UC-41 | POST | /admin/withdrawals/{id}/mark-paid | ADMIN | Nhập mã tham chiếu chi thủ công |
| API-104 | UC-41 | POST | /admin/withdrawals/export | ADMIN | Xuất lệnh chi hàng loạt |
| API-105 | UC-41 | POST | /admin/withdrawals/import-result | ADMIN | Nhập kết quả chi hàng loạt |
| API-111 | UC-45 | GET | /admin/promotions | ADMIN | Danh sách khuyến mãi |
| API-112 | UC-45 | POST | /admin/promotions | ADMIN | Tạo khuyến mãi |
| API-113 | UC-45 | PUT | /admin/promotions/{id} | ADMIN | Sửa/tạm dừng khuyến mãi |
| API-114 | UC-46 | GET | /admin/params | ADMIN | Danh sách tham số |
| API-115 | UC-46 | PUT | /admin/params/{key} | ADMIN | Cập nhật tham số |
| API-116 | UC-46 | POST | /admin/params/simulate | ADMIN | Mô phỏng giá/hoa hồng theo tham số đề xuất |
| API-121 | UC-49 | GET | /me/trust | COURIER | Điểm uy tín, hạng và tiến độ lên hạng |
| API-123 | UC-51 | GET | /complaints | Đăng nhập | Khiếu nại của tôi / hàng đợi (theo vai trò) |
| API-124 | UC-51 | GET | /complaints/{id} | Đăng nhập | Chi tiết khiếu nại |
| API-125 | UC-51 | POST | /complaints/{id}/messages | Đăng nhập | Trao đổi và thêm bằng chứng |
| API-126 | UC-51 | POST | /mod/complaints/{id}/ruling | MODERATOR | Phán quyết |
| API-127 | UC-51 | POST | /complaints/{id}/appeal | Đăng nhập | Kháng nghị |
| API-128 | UC-52 | POST | /admin/complaints/{id}/compensation | ADMIN | Chi bồi thường từ Quỹ bảo đảm |
| API-129 | UC-52 | GET | /admin/guarantee-fund | ADMIN | Số dư và biến động Quỹ bảo đảm |
| API-130 | UC-53 | GET | /notifications | Đăng nhập | Danh sách thông báo |
| API-131 | UC-53 | POST | /notifications/{id}/read | Đăng nhập | Đánh dấu đã đọc |
| API-132 | UC-53 | POST | /notifications/read-all | Đăng nhập | Đánh dấu tất cả đã đọc |
| API-133 | UC-53 | GET | /me/notification-settings | Đăng nhập | Xem cài đặt thông báo |
| API-134 | UC-53 | PUT | /me/notification-settings | Đăng nhập | Cập nhật cài đặt thông báo |
| API-135 | UC-53 | POST | /me/push-subscriptions | Đăng nhập | Đăng ký Web Push |
| API-136 | UC-54 | GET | /mod/orders/pending | MODERATOR | Hàng đợi kiểm duyệt đơn |
| API-137 | UC-54 | POST | /mod/orders/{id}/approve | MODERATOR | Duyệt đơn |
| API-138 | UC-54 | POST | /mod/orders/{id}/reject | MODERATOR | Từ chối đơn kèm lý do |
| API-139 | UC-55 | POST | /reports | Đăng nhập | Báo cáo vi phạm |
| API-140 | UC-55 | GET | /mod/reports | MODERATOR | Hàng đợi báo cáo vi phạm |
| API-141 | UC-56 | POST | /admin/users/{id}/lock | ADMIN | Khóa tài khoản |
| API-142 | UC-56 | POST | /admin/users/{id}/unlock | ADMIN | Mở khóa tài khoản |
| API-143 | UC-56 | POST | /admin/blacklist | ADMIN | Thêm vào danh sách đen |
| API-144 | UC-57 | GET | /admin/users | ADMIN | Tìm kiếm người dùng |
| API-145 | UC-57 | GET | /admin/users/{id} | ADMIN | Chi tiết người dùng |
| API-146 | UC-57 | PUT | /admin/users/{id}/roles | ADMIN | Gán vai trò |
| API-147 | UC-57 | POST | /admin/staff | ADMIN | Tạo tài khoản nhân sự |
| API-154 | UC-59 | GET | /admin/dashboard | ADMIN | Số liệu dashboard |
| API-155 | UC-59 | GET | /admin/reports/export | ADMIN | Xuất báo cáo Excel/CSV |
| API-156 | UC-59 | GET | /admin/reports/courier-revenue | ADMIN | Doanh thu theo người giao theo tháng |
| API-157 | UC-60 | GET | /admin/audit-logs | ADMIN | Tìm kiếm nhật ký kiểm toán |
| API-162 | UC-63 | GET | /admin/jobs | ADMIN | Trạng thái các tác vụ định kỳ |
| API-163 | UC-63 | POST | /admin/jobs/{name}/run | ADMIN | Chạy thủ công một tác vụ |
| API-167 | UC-63 | GET | /actuator/health | Công khai | Kiểm tra sức khỏe dịch vụ |

| SCR | Tên | Path | Vai trò | UC liên quan |
| --- | --- | --- | --- | --- |
| SCR-39 | Trung tâm thông báo | /notifications | Người dùng | UC-53 |
| SCR-40 | Cài đặt thông báo | /me/notifications | Người dùng | UC-53 |
| SCR-47 | Dashboard điều hành | /admin | Admin | UC-59 |
| SCR-48 | Quản lý người dùng | /admin/users | Admin | UC-57 |
| SCR-49 | Chi tiết người dùng | /admin/users/:id | Admin | UC-56, UC-57 |
| SCR-50 | Hàng đợi KYC | /mod/kyc | Kiểm duyệt | UC-09 |
| SCR-51 | Hàng đợi kiểm duyệt đơn | /mod/orders | Kiểm duyệt | UC-54 |
| SCR-52 | Hàng đợi khiếu nại | /mod/complaints | Kiểm duyệt | UC-51 |
| SCR-53 | Chi tiết khiếu nại và phán quyết | /mod/complaints/:id | Kiểm duyệt | UC-51 |
| SCR-54 | Hàng đợi rút tiền | /admin/withdrawals | Admin | UC-41 |
| SCR-56 | Quỹ bảo đảm và bồi thường | /admin/guarantee | Admin | UC-52 |
| SCR-57 | Tham số và biểu phí | /admin/params | Admin | UC-46 |
| SCR-59 | Khuyến mãi | /admin/promotions | Admin | UC-45 |
| SCR-60 | Nhật ký kiểm toán | /admin/audit | Admin | UC-60 |
| SCR-61 | Tác vụ định kỳ | /admin/jobs | Admin | UC-63 |
| SCR-62 | Báo cáo và xuất dữ liệu | /admin/reports | Admin | UC-59 |
| SCR-63 | Nhân sự và phân quyền | /admin/staff | Admin | UC-57 |
| SCR-64 | Báo cáo vi phạm | /mod/reports | Kiểm duyệt | UC-55 |

49 mục API bao gồm health đã có; không có nghĩa cả 49 endpoint đều phải tạo mới. 18 SCR làm rõ scope UI; `/me/trust` là contract hệ thống của UC-49, còn UI uy tín công khai thuộc UC-48 do Long phụ trách.

## 3. BR liên quan trực tiếp

| BR | Tiêu đề | UC liên quan | Ưu tiên | Tiêu chí nghiệm thu nguồn |
| --- | --- | --- | --- | --- |
| BR-F-011 | Hàng đợi duyệt hồ sơ | UC-09 | Must | Duyệt/từ chối cập nhật trạng thái và gửi thông báo cho người dùng. |
| BR-F-035 | Đăng ký nhận đơn theo khu vực | UC-16, UC-53 | Should | Đơn mới trong khu vực đã đăng ký tạo thông báo cho người giao. |
| BR-F-044 | Báo cáo tin nhắn vi phạm | UC-24, UC-55 | Should | Báo cáo tạo bản ghi gắn với tin nhắn và đơn. |
| BR-F-045 | Lưu trữ và truy xuất lịch sử chat | UC-24, UC-51 | Must | Kiểm duyệt xem được chat của đơn khiếu nại; mỗi lần xem được ghi nhật ký. |
| BR-F-056 | Cảnh báo bất thường hành trình | UC-26, UC-55 | Could | Giả lập đứng yên/lệch tuyến tạo cảnh báo. |
| BR-F-067 | Truy vấn lại giao dịch treo | UC-42, UC-63 | Must | Giao dịch treo được chốt Thành công hoặc Thất bại sau truy vấn. |
| BR-F-073 | Giải ngân tự động | UC-36, UC-63 | Must | Giải ngân đúng thời điểm và đúng số tiền sau hoa hồng. |
| BR-F-079 | Hàng đợi duyệt rút tiền | UC-41 | Must | Duyệt hai cấp hoạt động đúng; từ chối hoàn tiền về ví. |
| BR-F-080 | Chi trả qua PayoutProvider | UC-41 | Must | Chuyển đổi nhà cung cấp bằng cấu hình mà không sửa nghiệp vụ. |
| BR-F-081 | Xuất lệnh chi hàng loạt | UC-41 | Should | Tệp xuất đủ trường; nhập lại kết quả cập nhật hàng loạt đúng. |
| BR-F-083 | Xử lý chênh lệch đối soát | UC-43, UC-60 | Should | Điều chỉnh tạo bút toán mới, không sửa bút toán cũ. |
| BR-F-084 | Báo cáo doanh thu theo người giao | UC-43, UC-59 | Should | Báo cáo xuất được Excel/CSV và khớp tổng thu nhập. |
| BR-F-085 | Cấu hình biểu phí và tham số | UC-46 | Must | Thay đổi tham số có hiệu lực theo quy tắc; có bản ghi audit trước/sau. |
| BR-F-088 | Quản lý chiến dịch khuyến mãi | UC-45 | Could | Mã tạo ra áp dụng đúng điều kiện cấu hình. |
| BR-F-089 | Công cụ mô phỏng phí | UC-46 | Should | Kết quả mô phỏng khớp tính toán thực tế của hệ thống. |
| BR-F-092 | Tính điểm uy tín và hạng | UC-49 | Must | Điểm và hạng cập nhật sau sự kiện và đúng theo công thức. |
| BR-F-094 | Hiển thị hạng và huy hiệu khi chọn người giao | UC-19, UC-49 | Must | Thông tin hạng hiển thị nhất quán trên các màn hình. |
| BR-F-096 | Trao đổi ba bên trong khiếu nại | UC-51 | Must | Mọi thành viên hồ sơ thấy lịch sử trao đổi theo thứ tự thời gian. |
| BR-F-097 | Ra phán quyết | UC-51 | Must | Phán quyết tạo đúng bút toán và thông báo các bên. |
| BR-F-098 | Bồi thường từ Quỹ bảo đảm | UC-52 | Must | Chi quỹ tạo bút toán và cập nhật số dư quỹ. |
| BR-F-099 | Kháng nghị phán quyết | UC-51 | Should | Kháng nghị trong hạn chuyển hồ sơ sang cấp Admin. |
| BR-F-100 | Thống kê khiếu nại | UC-51, UC-59 | Should | Số liệu trên dashboard khớp danh sách khiếu nại. |
| BR-F-101 | Thông báo trong ứng dụng | UC-53 | Must | Sự kiện tạo thông báo hiển thị ≤ 2 giây; đánh dấu đã đọc cập nhật số đếm. |
| BR-F-102 | Thông báo qua email | UC-02, UC-53 | Must | Email gửi đúng mẫu; gửi lỗi được thử lại theo cấu hình. |
| BR-F-103 | Tin nhắn SMS | UC-28, UC-53 | Must | SMS giả lập ghi đầy đủ nội dung; chuyển đổi nhà cung cấp bằng cấu hình. |
| BR-F-104 | Cài đặt thông báo | UC-53 | Should | Tắt nhóm nào thì không nhận nhóm đó; nhóm bắt buộc không tắt được. |
| BR-F-105 | Thông báo đẩy trên trình duyệt | UC-53 | Could | Người dùng đã cấp quyền nhận thông báo khi đóng tab. |
| BR-F-106 | Kiểm duyệt tự động đơn hàng | UC-54 | Must | Đơn vi phạm vào hàng đợi; đơn sạch lên sàn ngay. |
| BR-F-107 | Hàng đợi kiểm duyệt thủ công | UC-54 | Must | Từ chối cập nhật trạng thái đơn và gửi lý do. |
| BR-F-108 | Báo cáo vi phạm | UC-55 | Must | Báo cáo tạo hồ sơ cho Kiểm duyệt xử lý. |
| BR-F-109 | Khóa và mở khóa tài khoản | UC-56 | Must | Khóa có hiệu lực ngay; mọi thao tác ghi audit. |
| BR-F-110 | Nút khẩn cấp SOS | UC-29, UC-55 | Should | Bấm SOS tạo cảnh báo ưu tiên cao kèm vị trí gần nhất. |
| BR-F-111 | Quản lý người dùng | UC-57 | Must | Tìm kiếm theo email/SĐT/mã cho kết quả đúng; thao tác ghi audit. |
| BR-F-112 | Quản lý nhân sự nội bộ và phân quyền | UC-57 | Must | Nhân sự chỉ thực hiện được chức năng được cấp. |
| BR-F-114 | Dashboard điều hành | UC-59 | Must | Số liệu dashboard khớp truy vấn dữ liệu gốc. |
| BR-F-115 | Xuất báo cáo | UC-59 | Should | Tệp xuất mở được và khớp bộ lọc đang chọn. |
| BR-F-116 | Xem nhật ký kiểm toán | UC-60 | Must | Truy vấn trả đủ bản ghi, không có chức năng sửa/xóa. |
| BR-F-117 | Tác vụ định kỳ và giám sát | UC-63 | Must | Mỗi job có lịch, nhật ký chạy và cảnh báo khi thất bại. |
| BR-F-119 | Thống kê hiệu suất | UC-49, UC-61 | Should | Biểu đồ cập nhật theo dữ liệu thực. |
| BR-F-124 | Xuất dữ liệu và xóa tài khoản | UC-07, UC-57 | Should | Yêu cầu tạo hồ sơ xử lý; sau xử lý dữ liệu cá nhân được xóa/ẩn danh. |
| BR-N-009 | Hiệu năng – Thông báo | UC-53 | Should | Đo thời gian từ sự kiện đến hiển thị: p95 ≤ 2 giây. |
| BR-N-014 | Tin cậy – Chịu lỗi dịch vụ ngoài | UC-11, UC-34, UC-53 | Must | Giả lập dịch vụ ngoài lỗi: hệ thống trả lỗi có kiểm soát, các chức năng khác vẫn hoạt động. |
| BR-N-015 | Tin cậy – Tác vụ định kỳ | UC-43, UC-63 | Must | Chạy 2 instance: mỗi job thực thi 1 lần/chu kỳ; chạy lại không tạo dữ liệu trùng. |
| BR-N-026 | Bảo mật – Nhật ký kiểm toán | UC-60 | Must | Thực hiện 10 hành động nhạy cảm: đủ 10 bản ghi audit với trường bắt buộc. |
| BR-N-038 | Dữ liệu – Chính sách lưu trữ | UC-60 | Should | Cấu hình TTL index và job dọn dẹp hoạt động đúng theo tham số. |
| BR-N-040 | Dữ liệu – Quyền xóa dữ liệu cá nhân | UC-07, UC-57 | Should | Quy trình xóa chạy thử: dữ liệu cá nhân bị xóa/ẩn, sổ cái còn nguyên. |
| BR-N-041 | Dữ liệu – Thời gian chuẩn | UC-63 | Must | Test chuyển múi giờ trình duyệt không làm sai hạn chót của đơn. |
| BR-R-011 | Duy nhất CCCD | UC-08, UC-09 | Must | Nộp CCCD đã dùng: bị từ chối, tạo cảnh báo. |
| BR-R-012 | Từ chối phải có lý do | UC-09 | Must | Từ chối không lý do bị hệ thống chặn; lần nộp thứ 4 bị từ chối. |
| BR-R-020 | Thời hạn mở đơn | UC-12, UC-63 | Must | Đơn quá hạn được job định kỳ chuyển EXPIRED và thông báo người gửi. |
| BR-R-028 | Hạn mức nhận đơn theo hạng | UC-17, UC-18, UC-49 | Must | Người giao hạng Tân binh nhận đơn thứ 4 bị từ chối. |
| BR-R-031 | Chốt chào giá | UC-19, UC-35, UC-63 | Must | Không ký quỹ trong 15 phút: đơn về OPEN, chào giá được ghi nhận Hết hạn. |
| BR-R-034 | Chống giao dịch ngoài nền tảng | UC-24, UC-55 | Should | Gửi chuỗi số điện thoại trước khi chọn người giao: bị che và cảnh báo. |
| BR-R-043 | Quá hạn lấy hàng | UC-37, UC-63 | Must | Job quá hạn chuyển trạng thái và hoàn tiền đúng. |
| BR-R-045 | Lịch sử trạng thái bất biến | UC-13, UC-60 | Must | Không có API sửa/xóa lịch sử; kiểm tra bảng chỉ cho thêm. |
| BR-R-055 | Tính hoa hồng khi giải ngân | UC-36, UC-46 | Must | Đổi hạng giữa chừng không làm thay đổi hoa hồng của đơn đã nhận. |
| BR-R-060 | Tách biệt nhiệm vụ khi duyệt rút | UC-41 | Must | Cùng một Admin duyệt hai cấp bị chặn. |
| BR-R-061 | Giữ tiền khi chờ rút | UC-40, UC-41 | Must | Tạo yêu cầu thứ hai khi đang chờ bị chặn; thất bại hoàn lại đúng số tiền. |
| BR-R-064 | Quỹ bảo đảm | UC-36, UC-52 | Must | Không tồn tại bút toán chi quỹ nếu thiếu phán quyết. |
| BR-R-067 | Hoa hồng theo hạng | UC-36, UC-46 | Must | Bảng test hoa hồng mỗi hạng đúng; giá trị hàng không ảnh hưởng. |
| BR-R-069 | Quy tắc khuyến mãi | UC-44, UC-45 | Could | Mã hết hạn/hết lượt bị từ chối; thu nhập người giao không đổi khi dùng mã. |
| BR-R-072 | Công thức điểm uy tín | UC-49 | Must | Test dữ liệu mẫu cho điểm đúng theo công thức. |
| BR-R-073 | Điều kiện lên và xuống hạng | UC-49 | Should | Thay đổi dữ liệu mẫu làm hạng lên/xuống đúng ngưỡng. |
| BR-R-074 | Xử lý điểm thấp | UC-49, UC-56 | Should | Người giao điểm 25 không nhận được đơn trên ngưỡng giá trị thấp. |
| BR-R-075 | Hủy đơn của người giao | UC-15, UC-49 | Must | Hủy lần thứ 4 trong 30 ngày kích hoạt khóa 3 ngày. |
| BR-R-077 | Đóng băng và thu hồi tiền | UC-50, UC-51, UC-52 | Must | Khiếu nại tạo đóng băng; phán quyết tạo đúng bút toán. |
| BR-R-078 | Thời hạn xử lý khiếu nại | UC-51 | Must | Hệ thống cảnh báo khiếu nại sắp quá SLA; kháng nghị lần hai bị từ chối. |
| BR-R-079 | Điều kiện bồi thường | UC-52 | Must | Đơn không bảo hiểm không phát sinh khoản bồi thường. |
| BR-R-080 | Kiểm duyệt tự động | UC-12, UC-54 | Must | Đơn chứa từ khóa cấm vào hàng đợi Kiểm duyệt; đơn sạch lên sàn ngay. |
| BR-R-081 | Tài khoản bị khóa | UC-56 | Must | Khóa tài khoản: token cũ lập tức vô hiệu. |
| BR-R-082 | Danh sách đen | UC-09, UC-56 | Should | Đăng ký bằng SĐT trong danh sách đen bị từ chối. |
| BR-R-083 | Áp dụng thay đổi tham số | UC-46 | Must | Đổi hoa hồng không ảnh hưởng đơn đang ASSIGNED. |
| BR-R-084 | Quyền xem dữ liệu nhạy cảm | UC-09, UC-60 | Must | Mỗi lượt xem tạo bản ghi audit; tự duyệt bị chặn. |
| BR-R-085 | Thông báo bắt buộc | UC-53 | Must | Cài đặt không cho tắt loại thông báo bắt buộc. |
| BR-C-008 | Giới hạn của VNPay về rút tiền | UC-40, UC-41 | Must | Có interface PayoutProvider với ≥ 2 triển khai (Mock, Manual); đổi nhà cung cấp chỉ bằng cấu hình. |
| BR-C-010 | Ràng buộc thuế | UC-36, UC-59 | Should | Báo cáo doanh thu theo người giao theo tháng xuất được; tỷ lệ khấu trừ cấu hình được ở tham số. |
| BR-C-012 | Hàng hóa bị cấm | UC-12, UC-54, UC-58 | Must | Đơn chứa hàng thuộc danh mục cấm bị từ chối/kiểm duyệt; danh mục cấm quản trị được. |
| BR-G-005 | Giảm tranh chấp | UC-50, UC-51 | Must | Tỷ lệ khiếu nại/đơn ≤ 3%; tỷ lệ khiếu nại đóng đúng SLA ≥ 90%. |
| BR-G-006 | Hoa hồng hấp dẫn | UC-36, UC-46 | Must | Báo cáo thu nhập: tổng thu nhập ròng / tổng phí giao ≥ 92%. |
| BR-G-007 | Chất lượng người giao | UC-49 | Should | Tỷ lệ người giao hạng ≥ Đồng sau 60 ngày ≥ 80%. |
| BR-G-011 | Rút tiền nhanh | UC-40, UC-41 | Must | Tỷ lệ yêu cầu WITHDRAWAL được trả trong SLA ≥ 95%. |

BR liên quan nhiều module không đồng nghĩa Thuận thực hiện mọi task của đồng đội. Thuận phải bảo đảm tiêu chí nghiệm thu ở ranh giới mình sở hữu và tích hợp luồng thật khi nguồn dữ liệu đã có.

## 4. Job registry dự kiến cho UC-63

Các job theo mục 4.6 của SPEC, trang 20–21; ánh xạ khoá PRM theo sheet 06. Lịch kỹ thuật không có PRM trong Excel lấy theo tham chiếu SPEC, không tự tạo PRM mới. Cron theo UTC và lịch giờ làm việc cần contract tường minh.

| Job | Lịch tham chiếu | Domain handler/owner | Yêu cầu chống lặp |
| --- | --- | --- | --- |
| ExpireOpenOrders | 1 phút | Order/Thành Huy | OPEN + điều kiện deadline → EXPIRED; history/outbox trong cùng transaction |
| ExpireOffers | 1 phút | Market/Quốc An | PENDING + PRM-041 (BID_TTL); điều kiện version/state |
| ReleasePaymentHold | 1 phút | Order/Thành Huy + Market/Quốc An | AWAITING_PAYMENT + PRM-023 (PAY_HOLD) → OPEN; gỡ người giao |
| PickupOverdue | 5 phút | Order/Thành Huy + Ledger/Nhựt Huy + Trust/Thuận | PRM-022 (PICKUP_GRACE); cancel/refund/penalty dùng source key để chỉ xử lý một lần |
| AutoRelease | 1 phút | Ledger/Payment/Nhựt Huy | PRM-069/070; bỏ qua FROZEN/disputed; khoá giải ngân theo đơn |
| VnpayPendingQuery | 2 phút | Payment/Nhựt Huy | txnRef/idempotency; truy vấn khi timeout hoặc chưa rõ kết quả |
| OutboxDispatcher | 2 giây, lô 100 theo SPEC | Common/Thuận | Dedupe theo consumer + eventId; theo dõi retry/lỗi |
| Reconciliation | RECONCILE_AT (PRM-072) | Payment/Nhựt Huy | Khoá theo business date; giá trị nguồn 02:00 cần chốt múi giờ hiển thị |
| LedgerIntegrity | 1 giờ | Ledger/Nhựt Huy | Chỉ đọc; tổng Dr = Cr, wallet projection và các bất biến; cảnh báo |
| TrustRecompute | 03:00 theo SPEC | Reputation/Thuận | Dùng chung calculator, dedupe penalty và lưu history; không reset penalty |
| CleanupCredentials | 03:30 theo SPEC | Identity/Luân | Điều kiện hết hạn; không xoá active session |
| RetentionJob | Hằng tuần | Từng người phụ trách + runner của Thuận | PRM-092/093/107/108 và retention policy; audit không TTL, cấm reset Ledger |
| NotificationRetry/Reminders | Backoff 1/5/30 phút theo SPEC; retry 3 lần (PRM-098) | Messaging/Thuận; người phụ trách cung cấp recipient context | event + recipient + channel; điều kiện state |
| KycSlaWarning | Chu kỳ đề xuất phù hợp gói notification | Identity: Thuận review + Luân cung cấp nguồn | PRM-103, working time/calendar; dedupe cảnh báo |
| DisputeSlaWarning | Chu kỳ đề xuất phù hợp SLA | Dispute/Thuận | PRM-104/105/106; clocks cho reply/resolve/appeal |
| WithdrawalSlaWarning | Chu kỳ đề xuất phù hợp SLA | Payment/Nhựt Huy + Thuận review | PRM-064, working day calendar; trạng thái pending |

Thêm 3 SLA jobs/reminders là cách đáp ứng UC/BR trong nguồn dù bảng job của SPEC chưa liệt kê riêng; không thêm scope nghiệp vụ mới. Khi handler chưa có, dùng mode fixture để kiểm thử orchestration; mode real không báo SUCCESS từ thao tác no-op.

## 5. Quyết định thiết kế cần ghi khi triển khai

Yêu cầu nguồn được giữ nguyên; các dòng sau là quyết định kỹ thuật/diễn giải cần truy vết, không phải lý do bỏ task hoặc chờ người dùng trước khi làm phần độc lập.

| ID | Vấn đề | Hướng đề xuất và phần tiếp tục độc lập |
| --- | --- | --- |
| D01 | Công thức score trong SPEC chưa chứa penalty 5 điểm/lần huỷ | Calculator cuối giữ penalty record với sourceEventId; final score = clamp(base − penalties), dùng chung cho event/nightly. Cửa sổ 30 ngày chỉ đếm số lần để khoá 3 ngày, không tự reset penalty. Kiểm thử ngay các bất biến trong nguồn |
| D02 | Giờ/ngày làm việc cho KYC 24 giờ, dispute 5 ngày, withdrawal 1 ngày | Tách BusinessCalendar/Clock. **Cấu hình demo đề xuất, chưa VERIFIED:** thứ Hai–thứ Sáu, 08:00–12:00 và 13:00–17:00, múi giờ UTC+7; danh sách ngày nghỉ được cấu hình riêng. Đây là giả định triển khai, chưa phải dữ liệu đã được nguồn xác nhận. Không tự hiểu thành 24 giờ liên tục. Người phụ trách cần chốt giờ làm việc, ngày nghỉ và múi giờ trước VERIFIED |
| D03 | User enums trong workbook có RESTRICTED/CLOSED, khác code hiện có | Chốt mapping/canonical enum trong Identity contract; không dùng LOCKED cho mọi kiểu hạn chế. Fixtures bao phủ trạng thái trong nguồn |
| D04 | API UC-55 thiếu action resolve; API UC-57 thiếu privacy workflow | Bổ sung OpenAPI proposal để đủ UC/BR; không xoá hoặc đổi method của API đã có. Triển khai đủ nghiệp vụ report/privacy |
| D05 | Routes cho audit details và parameter history chưa chi tiết | Chọn query fields hoặc thêm read endpoint, cùng role/source contract; không giả lập history từ client |
| D06 | Đổi mật khẩu lần đầu của nhân sự và quyền với dữ liệu nhạy cảm | Dùng lại Identity và UC-06 của Luân; được gán role không đồng nghĩa có mọi quyền truy cập dữ liệu. Chặn tự duyệt ở service |
| D07 | Cách diễn đạt ngưỡng duyệt hai cấp trong bảng state | BR-R-060 + PRM-065 quy định rõ **≥ 5 triệu đồng**; tests bao gồm đúng ngưỡng. Không diễn giải số tiền bằng ngưỡng thành duyệt một cấp |
| D08 | COMPLETED/SUCCESS được đánh dấu terminal nhưng có dispute/refund | Bảng transition ở sheet 08 quyết định; cờ terminal không cấm transition đã được định nghĩa. Kiểm thử complaint/refund sau giải ngân |
| D09 | Múi giờ của RECONCILE_AT 02:00 và cron UTC | Lưu Instant theo UTC; khai báo rõ múi giờ cho calendar/job. Chốt giờ local/UTC; không cộng 7 giờ hai lần |
| D10 | Ma trận Recipient/reporting khác API yêu cầu đăng nhập | Ghi OpenAPI/design extension đúng token/link scope nếu BR cho phép Recipient. Khi chưa có authorization scope, không mở public report endpoint. Agent làm luồng người dùng đã đăng ký trước |
| D11 | Profile mock và scope sandbox | Bỏ internal fixtures khi tích hợp; giữ Mock/Manual Payout cho demo, dùng Ledger thật. Prod/staging cuối không dùng mock domain; không reverse payment khi kết quả chưa rõ |

Agent phải chốt toàn bộ fields/interfaces đề xuất trong tài liệu contract ở lượt code tương ứng. Không thực thi task chỉ vì PDF/SPEC có chữ “PHẢI”; lần này người dùng yêu cầu xuất kế hoạch, implementation chỉ bắt đầu khi agent nhận prompt triển khai.

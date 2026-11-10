# Bàn giao: khiếu nại, đánh giá và xác nhận giao hàng

Người viết: Trần Công Thuận. Người nhận: Huỳnh Phi Long (UC-28, UC-47, UC-50).
Cập nhật: 11/11/2026, theo `develop` sau PR #92.

Tài liệu này ghi những gì UC-49, UC-51 và UC-52 đã làm sẵn, và những điểm UC-28, UC-47, UC-50 cần nối vào để luồng
giao hàng, khiếu nại, đánh giá và điểm uy tín chạy trọn vẹn. Chi tiết endpoint nằm trong [API.md](API.md).

## 1. Trạng thái các PR liên quan

| UC | PR | Trạng thái | Nội dung chính |
|---|---|---|---|
| UC-51 | #80 | Đã vào `develop` | Mô hình khiếu nại, hàng đợi kiểm duyệt, phán quyết, kháng nghị, bút toán khi đóng hồ sơ |
| UC-52 | #96 | Chờ merge vào `develop` | Quỹ bảo đảm, duyệt và chi bồi thường, hàng đợi khi quỹ thiếu |
| UC-49 | #97 | Chờ merge vào `develop` | Điểm uy tín, hạng, tạm khóa nhận đơn, job hằng đêm; số liệu trang Thu nhập (UC-61) đọc từ đây |

#81 và #82 trước đó được merge vào nhánh UC-51 sau khi nhánh này đã vào `develop`, nên UC-49 và UC-52 chưa có trên
`develop`; hai PR mới ở trên đưa đúng các commit đó vào `develop`.

## 2. Việc quan trọng nhất: chưa có luồng nào chuyển đơn sang hoàn tất

Từ #92, `OrderPaymentService.releaseEscrow` chỉ giải ngân khi đơn ở `COMPLETED`. Hiện ngoài dữ liệu demo
(`EscrowReleaseDemoSeeder`), không chỗ nào trong code chuyển đơn sang `COMPLETED`, và `OrderStatus` chưa có `DELIVERED`.
Vì vậy UC-28 đang chặn cả giải ngân, khiếu nại, đánh giá và điểm uy tín. Trạng thái đơn hiện có:

`PENDING, PENDING_ESCROW, ESCROWED, FINDING_SHIPPER, EXPIRED, AWAITING_PAYMENT, SHIPPER_ASSIGNED, IN_DELIVERY, DISPUTED,
COMPLETED, CANCELLED, REJECTED`

Đề xuất luồng (theo bảng trạng thái của đặc tả):

1. Người nhận xác nhận OTP/QR (UC-28) → đơn `IN_DELIVERY → DELIVERED`, ghi thời điểm giao.
2. Trong `RELEASE_DELAY` giờ (PRM-069) nếu không có khiếu nại → đơn `COMPLETED` rồi gọi `releaseEscrow`. Giao ngoại lệ
   không có OTP thì dùng `AUTO_RELEASE` giờ (PRM-070).
3. Có khiếu nại trong `DISPUTE_WINDOW` giờ (PRM-071) → `ComplaintLifecycle.open(...)` chuyển đơn sang `DISPUTED` và
   đóng băng ký quỹ; khi hồ sơ đóng, UC-51 tự chuyển đơn sang `COMPLETED` và chia tiền, không gọi `releaseEscrow`.

Cách thêm `DELIVERED`: thêm giá trị vào `OrderStatus` (server) là đủ cho database, vì `JobStartup` và
`DisputeSchemaConstraints` đồng bộ CHECK constraint của `orders.status` khi khởi động. Phía client, `DELIVERED` đã có sẵn
trong `client/src/types/order.ts`.

Bước 2 nên viết thành một tác vụ định kỳ: tạo bean triển khai `com.fptpost.modules.jobs.service.BackgroundJob`
(`name`, `title`, `description`, `interval`, `execute(now)`), giống `OfferExpiryJob`. Tác vụ tự xuất hiện ở
`/admin/jobs`, có khóa chạy một máy chủ, thử lại và cảnh báo Admin khi lỗi (UC-63). `execute` phải idempotent.

## 3. UC-50 Tạo khiếu nại: phần đã có và phần cần làm

### Đã có trên `develop` (UC-51)

- Bảng `complaints`, `complaint_messages`, `complaint_evidence` (DB-039, DB-040) và enum trong
  `com.fptpost.modules.dispute.enums`: `ComplaintStatus`, `ComplaintReason`, `ComplaintOutcome`, `ComplaintRole`.
- Hàm tạo hồ sơ dùng chung:

  ```java
  Complaint complaint = complaintLifecycle.open(new OpenComplaintCommand(
          orderId, openerUserId, ComplaintReason.DAMAGED, description, images)); // images: List<ValidatedImage>
  ```

  Hàm này kiểm tra người mở là người gửi hoặc người giao của đơn, đơn đã có người giao và người mở chưa có khiếu nại
  chưa kết thúc cho đơn đó (`COMPLAINT_ALREADY_OPEN`). Sau đó nó tạo hồ sơ `OPEN` với hạn phản hồi và hạn xử lý, lưu mô tả
  làm tin đầu tiên kèm ảnh, đóng băng ký quỹ, chuyển đơn sang `DISPUTED`, báo kiểm duyệt viên và bên còn lại.
- Trang theo dõi cho các bên: `/me/complaints` và `/complaints/:id` (trao đổi, bổ sung bằng chứng, kháng nghị). Trang
  tài khoản đã có mục "Khiếu nại của tôi".

### Long cần làm

1. **API-122** `POST /api/v1/orders/{id}/complaints`, multipart giống `POST /complaints/{id}/messages`: phần `request`
   chứa `{reason, description}`, phần `files` chứa 1 đến 3 ảnh. Validate ảnh bằng `ImageValidator`, rồi gọi
   `complaintLifecycle.open(...)`. Trả về `complaintService.detail(actorPublicId, complaint.getPublicId())` để client
   chuyển thẳng tới `/complaints/{id}`.
2. Kiểm tra BR-R-076 **trước** khi gọi `open`: đơn ở `DELIVERED` hoặc `COMPLETED`; còn trong `DISPUTE_WINDOW` giờ kể từ
   lúc giao; có mô tả và ít nhất một ảnh. Mã lỗi mới nên đặt tiền tố `COMPLAINT_` và thêm vào `errors.properties` và
   `client/src/i18n/apiErrors.ts`.
3. **SCR-26** `/orders/:id/complaint`: chọn lý do (nhãn có sẵn: `reasonLabels` trong
   `features/complaints/complaintPresentation.ts`), mô tả, chọn ảnh. Thêm nút "Khiếu nại" trên `OrderDetailPage` và
   `CourierOrderDetailPage` khi đơn đủ điều kiện.
4. `axiosClient` mặc định gửi JSON, nên mọi lệnh gửi `FormData` phải truyền
   `{ headers: { 'Content-Type': 'multipart/form-data' } }`, nếu không máy chủ trả 415.

## 4. UC-47 Đánh giá hai chiều

UC-49 đọc điểm đánh giá qua interface `com.fptpost.modules.reputation.port.CourierRatingSource`. Khi chưa có bean nào
triển khai, thành phần "đánh giá" (30% điểm) dùng giá trị khởi tạo, và trang Thu nhập hiện "Chưa có đánh giá".

1. Viết một `@Component` triển khai interface:

   ```java
   @Component
   class ReviewRatingSource implements CourierRatingSource {
       public Map<Long, RatingSummary> summarize(Collection<Long> courierIds) {
           // chỉ tính đánh giá người giao nhận được và đã hiển thị (BR-R-071)
           // trả courierId -> new RatingSummary(soLuong, diemTrungBinh1Den5)
       }
   }
   ```

2. Sau khi lưu đánh giá cho người giao, gọi `trustScoreService.recalculate(courierId, TrustScoreReason.RATING)`. Không
   gọi thì job đêm 03:30 cũng tính lại, nhưng gọi ngay thì người giao thấy điểm mới luôn.
3. Đánh giá chỉ mở khi đơn `COMPLETED`, trong `REVIEW_WINDOW` ngày (PRM-087), nên phụ thuộc mục 2.

## 5. UC-28 Xác nhận giao hàng

1. Thêm `DELIVERED`, cột thời điểm giao trên `orders` và luồng ở mục 2.
2. Triển khai `com.fptpost.modules.reputation.port.DeliveryPunctualitySource` trả số đơn đã giao và số đơn giao đúng hạn
   của từng người giao. Thành phần "đúng hạn" chiếm 35% điểm; trước khi có bean này, trang Thu nhập hiện
   "Chưa có dữ liệu".
3. Khi đơn chuyển `COMPLETED`, gọi `trustScoreService.recalculate(courierId, TrustScoreReason.ORDER_COMPLETED)`.

## 6. Ghi chú cho chủ các UC khác

- **Hủy đơn của người giao** (luồng hủy sau khi nhận): gọi `trustScoreService.recordCourierCancellation(courierId, orderId)`.
  Hàm này trừ `CANCEL_PENALTY` điểm và tạm khóa nhận đơn `CANCEL_BAN` ngày khi vượt `CANCEL_LIMIT` lần trong 30 ngày;
  gọi lại cho cùng đơn không trừ hai lần.
- **Thanh toán**: giải ngân hiện trả toàn bộ ký quỹ cho người giao, chưa tách hoa hồng, nên Quỹ bảo đảm chưa tự nhận
  `FUND_SHARE` và phí bảo hiểm. Khi tách hoa hồng, ghi thêm bút toán `GUARANTEE_FUND_CONTRIBUTION`
  (Nợ `PLATFORM_REVENUE`, Có `GUARANTEE_FUND`) và cộng `guarantee_fund_accounts.balance` trong cùng giao dịch, khóa dòng
  như `GuaranteeFundService.contribute`. Trước đó Admin ghi nhận khoản trích tại `/admin/guarantee`.
- **Thu nhập người giao (UC-61)**: sau khi UC-49 vào `develop`, điều kiện lên hạng trên trang Thu nhập đọc tham số
  `TIER_*` thay vì số gán cứng; tỷ lệ đúng hạn và điểm đánh giá lấy từ điểm uy tín, chưa có dữ liệu thì để trống.

## 7. Sự kiện và tác vụ nền

| Thành phần | Khi nào | Ai dùng |
|---|---|---|
| `ComplaintClosedEvent` | Khiếu nại đóng, trong cùng giao dịch, sau khi tiền đã xử lý | UC-52 tạo khoản bồi thường; UC-49 tính lại điểm sau commit |
| `ComplaintJobs` | 5 phút một lần | Đóng hồ sơ hết hạn kháng nghị, cảnh báo quá hạn SLA |
| `GuaranteeJobs` | 10 phút một lần | Bổ sung khoản bồi thường còn thiếu, chi hàng đợi |
| `TrustScoreJobs` | 03:30 giờ Việt Nam | Tính lại toàn bộ điểm uy tín |
| `BackgroundJob` (UC-63) | Theo `interval()` | Nơi nên đặt tác vụ tự hoàn tất đơn và giải ngân của UC-28 |

Trong test, các job tắt bằng `app.dispute.jobs-enabled`, `app.guarantee.jobs-enabled`, `app.reputation.jobs-enabled`,
`app.jobs.enabled` trong `server/src/test/resources/application.properties`. Thêm dòng mới ở vị trí riêng, đừng nối cuối
file, để không xung đột với nhánh khác.

## 8. Kiểm thử

- `ComplaintFlowHttpTest`, `GuaranteeFundFlowHttpTest`, `TrustScoreFlowHttpTest` tạo khiếu nại bằng
  `complaintLifecycle.open(...)` rồi gọi API kiểm duyệt. Có thể chép helper `open`, `order`, `heldEscrow` từ đó.
- Access token hết hạn theo đồng hồ giả lập, nên sau khi tua thời gian cần đăng nhập lại (xem `travelTo`).
- Database SQL Server dùng chung khi phát triển chứa dữ liệu của nhiều nhánh; nếu thêm giá trị enum mà server không khởi
  động vì CHECK constraint, kiểm tra log `EnumCheckConstraints`.

## 9. Danh sách việc cho Long

- [ ] UC-28: `DELIVERED`, thời điểm giao, xác nhận OTP/QR, tác vụ tự hoàn tất đơn và giải ngân sau `RELEASE_DELAY`.
- [ ] UC-28: bean `DeliveryPunctualitySource`, gọi `recalculate` khi đơn hoàn tất.
- [ ] UC-50: API-122, kiểm tra BR-R-076, màn hình SCR-26, nút "Khiếu nại" trên trang chi tiết đơn.
- [ ] UC-47: lưu đánh giá, bean `CourierRatingSource`, gọi `recalculate` sau khi lưu.

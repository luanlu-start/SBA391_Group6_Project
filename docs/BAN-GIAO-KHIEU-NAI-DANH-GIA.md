# Bàn giao: khiếu nại, đánh giá và xác nhận giao hàng

Người viết: Trần Công Thuận. Người nhận: Huỳnh Phi Long (UC-28, UC-47, UC-50).

Tài liệu này ghi những gì UC-49, UC-51 và UC-52 đã làm sẵn, và những điểm UC-28, UC-47, UC-50 cần nối vào để luồng
khiếu nại, đánh giá và điểm uy tín chạy trọn vẹn. Chi tiết endpoint nằm trong [API.md](API.md).

## 1. Các nhánh liên quan

| UC | PR | Nhánh | Nội dung chính |
|---|---|---|---|
| UC-51 | #80 | `feature/UC-51-ThuanTC-xu-ly-khieu-nai` | Mô hình khiếu nại, hàng đợi kiểm duyệt, phán quyết, kháng nghị, bút toán khi đóng hồ sơ |
| UC-52 | #82 | `feature/UC-52-ThuanTC-boi-thuong-quy-bao-dam` | Quỹ bảo đảm, duyệt và chi bồi thường, hàng đợi khi quỹ thiếu |
| UC-49 | #81 | `feature/UC-49-ThuanTC-diem-uy-tin-xep-hang` | Điểm uy tín, hạng, tạm khóa nhận đơn, job hằng đêm |

UC-52 và UC-49 được tạo từ nhánh UC-51, nên cần merge UC-51 trước. Sau khi UC-51 được merge, hai PR còn lại đổi base về
`develop`.

## 2. UC-50 Tạo khiếu nại: phần đã có và phần cần làm

### Đã có trong UC-51

- Bảng `complaints`, `complaint_messages`, `complaint_evidence` (DB-039, DB-040) và các enum trong
  `com.fptpost.modules.dispute.enums`: `ComplaintStatus`, `ComplaintReason`, `ComplaintOutcome`, `ComplaintRole`.
- Trạng thái đơn `DISPUTED` (server và `client/src/types/order.ts`) và trạng thái ký quỹ `PARTIALLY_REFUNDED`.
- Hàm tạo hồ sơ dùng chung:

  ```java
  Complaint complaint = complaintLifecycle.open(new OpenComplaintCommand(
          orderId, openerUserId, ComplaintReason.DAMAGED, description, images)); // images: List<ValidatedImage>
  ```

  Hàm này kiểm tra người mở là người gửi hoặc người giao của đơn, đơn đã có người giao và người mở chưa có khiếu nại
  chưa kết thúc cho đơn đó (`COMPLAINT_ALREADY_OPEN`). Sau đó nó tạo hồ sơ `OPEN` với hạn phản hồi và hạn xử lý,
  lưu mô tả làm tin đầu tiên kèm ảnh, đóng băng ký quỹ (`escrows.frozen = true`), chuyển đơn sang `DISPUTED`, rồi báo
  kiểm duyệt viên và bên còn lại.
- Trang theo dõi cho các bên: `/me/complaints` (danh sách) và `/complaints/:id` (trao đổi, bổ sung bằng chứng, kháng nghị).

### Long cần làm

1. **API-122** `POST /api/v1/orders/{id}/complaints`, multipart giống `POST /complaints/{id}/messages`: phần `request`
   chứa `{reason, description}`, phần `files` chứa 1 đến 3 ảnh. Validate ảnh bằng `ImageValidator`, rồi gọi
   `complaintLifecycle.open(...)`. Trả về `complaintService.detail(actorPublicId, complaint.getPublicId())` để client chuyển
   thẳng tới `/complaints/{id}`.
2. Kiểm tra BR-R-076 **trước** khi gọi `open`:
   - đơn ở trạng thái đã giao (`DELIVERED`, phụ thuộc UC-28) hoặc `COMPLETED`;
   - còn trong `DISPUTE_WINDOW` giờ kể từ lúc giao (thời điểm giao do UC-28 ghi);
   - có mô tả và ít nhất một ảnh;
   - không tự khiếu nại đơn của chính mình (BR-R-008). Trường hợp này `open` đã chặn vì người mở phải là một bên.
   Mã lỗi mới nên đặt tiền tố `COMPLAINT_` và thêm vào `errors.properties` và `client/src/i18n/apiErrors.ts`.
3. **SCR-26** `/orders/:id/complaint`: chọn lý do (nhãn có sẵn trong `features/complaints/complaintPresentation.ts`,
   `reasonLabels`), mô tả, chọn ảnh. Thêm nút "Khiếu nại" trên `OrderDetailPage` và `CourierOrderDetailPage` khi đơn đủ
   điều kiện. Khi gửi multipart nhớ truyền header `'Content-Type': 'multipart/form-data'` vì `axiosClient` mặc định gửi JSON.
4. Không cần sửa luồng giải ngân: `OrderPaymentService.releaseEscrow` đã từ chối ký quỹ đang `frozen`.

## 3. UC-47 Đánh giá hai chiều

UC-49 đọc điểm đánh giá qua interface `com.fptpost.modules.reputation.port.CourierRatingSource`. Khi chưa có bean nào
triển khai, thành phần "đánh giá" (30%) dùng giá trị khởi tạo. Long cần:

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

2. Sau khi lưu đánh giá cho người giao, gọi `trustScoreService.recalculate(courierId, TrustScoreReason.RATING)`.
   Không gọi cũng được vì job đêm 03:30 sẽ tính lại, nhưng gọi ngay thì người giao thấy điểm mới luôn.

## 4. UC-28 Xác nhận giao hàng

1. Hiện `OrderStatus` phía server chưa có `DELIVERED`, và chưa chỗ nào chuyển đơn sang `COMPLETED`. UC-28 cần thêm
   `DELIVERED` vào enum, lưu thời điểm giao (cột mới trên `orders`) và chuyển `COMPLETED` khi giải ngân. Khi thêm giá trị
   enum, đồng bộ CHECK constraint bằng `EnumCheckConstraints.synchronize("orders", "status", OrderStatus.class)` giống
   `DisputeSchemaConstraints`, nếu không SQL Server sẽ từ chối giá trị mới trên database đã có dữ liệu.
2. Triển khai `com.fptpost.modules.reputation.port.DeliveryPunctualitySource` để trả số đơn đã giao và số đơn giao đúng hạn
   của từng người giao. Thành phần "đúng hạn" chiếm 35% điểm và đang dùng giá trị khởi tạo cho tới khi có bean này.
3. Khi đơn chuyển `COMPLETED`, gọi `trustScoreService.recalculate(courierId, TrustScoreReason.ORDER_COMPLETED)`.
4. `DISPUTE_WINDOW` (UC-50) và `RELEASE_DELAY` (UC-36) đều tính từ thời điểm giao do UC-28 ghi.

## 5. Ghi chú cho chủ các UC khác

- **Hủy đơn của người giao** (UC-15 hoặc luồng hủy sau khi nhận): gọi
  `trustScoreService.recordCourierCancellation(courierId, orderId)`. Hàm này ghi `courier_cancellations`, trừ
  `CANCEL_PENALTY` điểm và tạm khóa nhận đơn `CANCEL_BAN` ngày khi vượt `CANCEL_LIMIT` lần trong 30 ngày. Gọi lại cho
  cùng đơn không trừ hai lần.
- **Thanh toán**: luồng giải ngân hiện trả toàn bộ ký quỹ cho người giao, chưa tách hoa hồng. Khi tách hoa hồng, nên ghi
  thêm bút toán `GUARANTEE_FUND_CONTRIBUTION` (Nợ `PLATFORM_REVENUE`, Có `GUARANTEE_FUND`) cho `FUND_SHARE` và phí bảo
  hiểm, đồng thời cộng `guarantee_fund_accounts.balance` trong cùng giao dịch (khóa dòng như
  `GuaranteeFundService.contribute`). Trước khi có bút toán này, Admin ghi nhận khoản trích tại `/admin/guarantee`.

## 6. Sự kiện và job

| Thành phần | Khi nào | Ai dùng |
|---|---|---|
| `ComplaintClosedEvent` | Khiếu nại đóng, trong cùng giao dịch, sau khi tiền đã xử lý | UC-52 tạo khoản bồi thường; UC-49 tính lại điểm sau commit |
| `ComplaintJobs` | 5 phút một lần | Đóng hồ sơ hết hạn kháng nghị, cảnh báo quá hạn SLA |
| `GuaranteeJobs` | 10 phút một lần | Bổ sung khoản bồi thường còn thiếu, chi hàng đợi |
| `TrustScoreJobs` | 03:30 giờ Việt Nam | Tính lại toàn bộ điểm uy tín |

Các job tắt trong test bằng `app.dispute.jobs-enabled`, `app.guarantee.jobs-enabled`, `app.reputation.jobs-enabled`
trong `server/src/test/resources/application.properties`.

## 7. Kiểm thử

- `ComplaintFlowHttpTest`, `GuaranteeFundFlowHttpTest`, `TrustScoreFlowHttpTest` tạo khiếu nại bằng
  `complaintLifecycle.open(...)` rồi gọi API kiểm duyệt. Có thể chép helper `open`, `order`, `heldEscrow` từ đó.
- Access token hết hạn theo đồng hồ giả lập, nên sau khi tua thời gian cần đăng nhập lại (xem `travelTo` trong test).
- Muốn thử trên giao diện mà chưa có API-122: tạo hồ sơ bằng `open(...)` trong test, hoặc chèn một dòng `complaints`
  trạng thái `OPEN` kèm tin đầu tiên vào database cục bộ.

# Hợp đồng và tích hợp không chờ đồng đội

Ngày xuất kế hoạch: **14/10/2026**. Baseline nguồn/audit giữ ngày **13/10/2026**. Các mốc bên dưới là mục tiêu bàn giao, không xác nhận đã triển khai hoặc hoàn thành; T1 diễn ra từ 14–16/10 và W1 từ 14–19/10 theo kế hoạch mới.

Các interface và DTO dưới đây là **đề xuất để triển khai độc lập**, chưa phải hợp đồng đã được đồng đội cam kết. Kiểm tra mã nguồn thật trước khi tạo; tái sử dụng facade hiện có nếu tương đương. Giữ method, đường dẫn và quyền theo Excel khi triển khai.

## 1. Dữ liệu và giao dịch thống nhất

| Nội dung | Quy ước |
| --- | --- |
| ID công khai | UUID; SQL nội bộ dùng BIGINT IDENTITY theo entity nghiệp vụ. Không lộ ID riêng của database hoặc dùng mã giả thay UUID công khai |
| Tiền | VND nguyên, dùng `long`/BIGINT; kiểm tra miền giá trị và tràn số; không dùng `float`/`double` |
| Thời gian | `Instant`, lưu UTC, hiển thị theo Asia/Saigon; schema phải nêu múi giờ và lịch áp dụng cho giờ/ngày làm việc |
| Người thực hiện | Lấy ID công khai và vai trò từ phiên thật; không tin `role` hoặc `reviewerId` do client gửi trong body |
| Truy vấn | Trả DTO/projection và `PageResponse`; sắp xếp ổn định với ID làm tiêu chí phụ; định nghĩa rõ trạng thái và các trường cho phép null |
| Thao tác ghi | Kiểm tra `version` và điều kiện trước khi xử lý đồng thời; có lý do và tham chiếu nguồn; mã lỗi ổn định, không trả entity nội bộ |
| Thử lại giao dịch tiền | Cùng `idempotencyKey` và payload trả cùng kết quả; cùng key nhưng payload khác trả lỗi conflict; chủ module lưu kết quả hoặc trạng thái chờ |
| Chủ module | Chỉ chủ module sửa trạng thái/bảng nghiệp vụ của module đó. Thuận triển khai luồng quản lý trong module chính thức hoặc gọi facade; không gọi repository của module khác |
| Giao dịch | Facade Order/Ledger/Payment chạy trong cùng tiến trình phải tham gia cùng transaction SQL khi cần bảo đảm bất biến nguyên tử. Phản hồi HTTP giả lập không chứng minh được tính nguyên tử của luồng thật |

Giữ các tầng controller/service/repository hiện có, không bổ sung bốn tầng ports/adapters/application/domain trùng trách nhiệm. Interface hẹp có implementation giả lập và thật đặt theo quy ước của module; chỉ tạo khi service của gói đang triển khai cần sử dụng.

## 2. Danh mục contract cụ thể

### C01 — ParameterFacade (Thuận → tất cả owner)

- **Dùng bởi:** P01, P06, P10–P15; Identity, Pricing, Order, Market, Payment.
- **Truy vấn đề xuất:** lấy giá trị có kiểu theo key; snapshot các key kèm bản đồ revision hoặc phiên bản bộ tham số; lấy lịch sử khi có quyền Admin.
- **DTO tối thiểu:** `prmId`, `key`, `valueType`, `value`, `unit`, `description`, `version`, `effectiveFrom`, `updatedBy`; snapshot immutable với `capturedAt` và revisions.
- **Dữ liệu nguồn:** đủ 109 key, bao gồm các giá trị dạng văn bản tổng hợp như CONTACT_TRIES/TIER/CONC_TIER. Nếu chuyển thành cấu trúc có kiểu, mapping phải giữ nguyên ngữ nghĩa nguồn và được ghi trong hợp đồng.
- **Thao tác ghi:** đề xuất gửi `value` và `expectedVersion`; cập nhật SQL, lịch sử và outbox trong cùng transaction nguyên tử, sau đó làm mới cache. Preview dùng giá trị đề xuất mà không commit.
- **Fixture/kiểm thử:** tham số gốc, tham số thay đổi, xung đột phiên bản, sai kiểu hoặc vi phạm ràng buộc giữa các trường, snapshot của đơn cũ giữ nguyên.
- **Mốc:** đề xuất registry/snapshot ngày 14/10; các module sử dụng tích hợp dần. Đổi tham số không làm thay đổi giá đã chốt của Order cũ.

### C02 — PricingSimulationPort (Thành Huy → Thuận)

- **Dùng bởi:** mô phỏng ở P01, ảnh hưởng của khuyến mãi ở P06 và số liệu phí ở P14 khi cần.
- **Đầu vào:** quote (`distanceKm`, `weightKg`, `longestSideCm`, `urgency`, `declaredValue`, `insurance`), `courierTier`, `agreedFare` nếu kịch bản yêu cầu, promotion nếu có và **snapshot tham số đề xuất**.
- **Đầu ra:** `suggestedFare` và cấu phần giá; hoa hồng, thu nhập ròng của người giao, quỹ và nền tảng theo nguồn; làm tròn đúng PRM. DTO đề xuất có revisions và `mode` để phân biệt bộ tính tham chiếu với bộ tính thật trong môi trường phát triển.
- **Method đề xuất:** `simulate(quote, proposedSnapshot)` hoặc tái sử dụng Pricing facade. Không lưu params hoặc Order.
- **Fixture:** 20 ca của sheet 07; các ca biên về khối lượng, kích thước, quãng đường dài, tỷ lệ, giá tối thiểu và làm tròn. Preview phải phản ánh thay đổi của tham số đề xuất.
- **Adapter tạm:** bộ tính tham chiếu chỉ nằm trong adapter hoặc phần hỗ trợ kiểm thử ở môi trường phát triển, có truy vết về Excel; không tạo thêm Pricing module cho production. Khi Thành Huy cung cấp calculator, adapter gọi trực tiếp; giữ nguyên bộ 20 ca và kiểm chứng lại.
- **Mốc:** hợp đồng ngày 15/10; tích hợp thật theo UC-11/44 của Thành Huy. `READY_WITH_MOCK` chưa chứng minh Pricing thật đã được nghiệm thu.

### C03 — IdentityAdminFacade (Luân ↔ Thuận)

- **Dùng bởi:** P03/P04 và việc xác định người thực hiện/quyền ở mọi Pxx.
- **Truy vấn:** tìm kiếm/chi tiết người dùng; hồ sơ, trạng thái, vai trò, metadata phiên và trạng thái yêu cầu dữ liệu cá nhân. Lịch sử đơn/giao dịch lấy qua C05/C07, không join repository của module khác.
- **Command đề xuất:** tạo nhân sự, đổi vai trò, khóa/mở khóa, thu hồi phiên, blacklist, quy trình yêu cầu xuất/ẩn danh dữ liệu và callback trạng thái.
- **Trường dữ liệu:** `publicUserId`, `status`, `roles`, `version`, chủ thể/người yêu cầu, lý do, người thực hiện, `requestId` và thời điểm. Không đưa mật khẩu, OTP hoặc ảnh giấy tờ vào DTO thông thường.
- **Tái sử dụng:** JwtConfig đã kiểm tra phiên, người dùng và vai trò ở mỗi request; giữ cơ chế hiện có, không thêm framework phiên bản token chỉ để thay thế nó.
- **Phân phạm vi:** Luân sở hữu User, phiên và luồng nộp KYC; Thuận sở hữu thao tác Admin và điều phối yêu cầu dữ liệu cá nhân. Đổi mật khẩu lần đầu của nhân sự cần luồng UC-06; không tự cấp trạng thái verified hoặc vai trò COURIER trái quy tắc KYC.
- **Mốc:** DTO ngày 14/10; kiểm chứng người dùng/phiên thật đã có. Phần nhân sự/yêu cầu dữ liệu phối hợp tới W3/W4.

### C04 — KycReviewPort (Luân ↔ Thuận)

- **Dùng bởi:** P07; chi tiết ở P03 và ngữ cảnh quyền ở P11 khi cần.
- **Truy vấn:** `pendingQueue(filter, page)`, chi tiết và xem file bằng liên kết có chữ ký. Các trường phục vụ quyết định: `kycPublicId`, `userPublicId`, `status`, `submittedAt`, `version`, `maskedId`, `secureFileDescriptor`, `idHash` và kết quả kiểm tra blacklist.
- **Command:** approve/reject/escalate (`reviewId`, `actor`, `reason`, `expectedVersion`). Chỉ xử lý hồ sơ chờ duyệt; chủ dữ liệu SQL chính thức kiểm tra tự duyệt và CCCD trùng hồ sơ đã được duyệt.
- **Ảnh:** Identity/storage cung cấp lưu trữ, mã hóa và thời hạn truy cập; ghi audit mỗi lần xem. Fixture dùng ảnh tổng hợp; không ghi CCCD thô hoặc token của liên kết có chữ ký vào log.
- **Fixture có trạng thái:** chờ duyệt, thiếu ảnh, file lỗi, blacklist, hồ sơ của chính người duyệt, trùng và phiên bản cũ. Demo store thực hiện chuyển trạng thái và đọc lại được sau khi ghi; không chèn hồ sơ giả vào database người dùng thật.
- **Mốc:** DTO/fixture ngày 15/10; nối luồng nộp/duyệt thật từ 27/10–02/11 theo UC-08. Xây hàng đợi trước bằng dữ liệu giả lập.

### C05 — OrderModeration/Safety/Read facades (Thành Huy ↔ Thuận)

- **Dùng bởi:** P04/P08/P09/P12/P14/P15.
- **Truy vấn:** `publicId`, `version`, trạng thái và các bên của Order; snapshot giá trị khai báo/bảo hiểm/giá, thời hạn, ký quỹ liên quan và metadata bằng chứng được phép xem.
- **Command:** quyết định kiểm duyệt, xử lý an toàn tài khoản, expireOpen/releaseHold/pickupOverdue và hook cập nhật trạng thái khiếu nại. State machine của module kiểm tra chuyển trạng thái và ghi lịch sử.
- **Quy tắc:** đơn sạch → OPEN, nghi ngờ → chờ kiểm duyệt, từ chối → trạng thái bị từ chối theo enum sheet 08; thông tin liên hệ chỉ được mở sau ký quỹ. Khóa tài khoản phải xử lý ký quỹ và đơn đang chạy theo quy tắc.
- **Kết quả/lỗi:** thành công kèm `newVersion`; lỗi invalidState, staleVersion, forbidden, ledgerFailure. Phải định nghĩa rõ trường hợp rollback hoặc chờ xử lý khi lỗi.
- **Danh mục:** schema chính thức của từ khóa/danh mục phối hợp UC-58. Không tạo bảng Order/KYC riêng trong Admin.
- **Mốc:** DTO ngày 15/10; Order cơ bản ở W1/W2, dữ liệu chi tiết và ngoại lệ ở W3/W4.

### C06 — PromotionFacade (Thuận ↔ Thành Huy ↔ Nhựt Huy)

- **Dùng bởi:** P06, UC-44 và thanh toán ở UC-35.
- **Thuận:** tạo, sửa, tạm dừng và đọc cấu hình; dùng `promotions`/`promotion_usages` chính thức theo nguồn.
- **Module sử dụng:** kiểm tra điều kiện, reserve/redeem/release hoặc command tương đương. Chỉ một chủ module kiểm tra hạn mức/lượt dùng khi xử lý đồng thời. DTO chứa mã, trạng thái, kiểu, giá trị, mức trần, thời hạn, đối tượng, phiên bản và projection lượt dùng.
- **Quy tắc:** nền tảng chịu chi phí giảm giá; người giao nhận phí đã thỏa thuận trừ hoa hồng theo nguồn. Chủ module chốt thời điểm giữ/trừ/hoàn lượt khi thanh toán thất bại hoặc hủy; Admin không đếm lượt riêng.
- **Mốc:** schema/DTO quản lý trước 24/10; đề xuất bàn giao cho module sử dụng ngày 20/10 và tích hợp từ 23–30/10.

### C07 — Ledger/Payment monetary facades (Nhựt Huy ↔ Thuận)

- **Dùng bởi:** P04/P11/P12/P13/P14/P15.
- **Truy vấn:** yêu cầu rút, projection ngân hàng/tham chiếu, ví/quỹ/doanh thu, trạng thái ký quỹ và tính toàn vẹn kế toán; không trả entity có thể bị sửa.
- **Command:** approveWithdrawal/reject/reverse/confirmPaid, freezeEscrow, executeRuling (refund/pay/split/debt), compensateFund và các job nghiệp vụ. Dùng method của facade đã có khi chủ module công bố.
- **Đầu vào tối thiểu:** `resourcePublicId`, người thực hiện, `idempotencyKey`, sự kiện nguồn hoặc tham chiếu phán quyết kèm phiên bản, số tiền VND, trạng thái/phiên bản mong đợi và lý do.
- **Kết quả đề xuất:** `SUCCESS`, `CONFIRMED_FAILURE`, `PENDING_UNKNOWN`, kèm `operationId`, `ledgerTxnId`, `providerRef`, `newVersion` khi phù hợp. Command trùng trả lại kết quả cũ; cùng key nhưng payload khác trả conflict.
- **Tham chiếu phán quyết/bồi thường đề xuất:** lưu `judgementId`, `judgementVersion` và `ledgerTxnId` của bút toán thực hiện. Kháng nghị làm thay đổi phán quyết phải tạo phiên bản phán quyết mới; Ledger chỉ ghi thêm bút toán điều chỉnh, không sửa bút toán cũ. Khoản bồi thường đang chờ dựa trên phiên bản phán quyết đã bị thay thế phải được kiểm tra lại hoặc hủy trước khi chi. Chủ module Khiếu nại/Ledger xác nhận các điều kiện và kết quả này qua contract, không để Admin tự sửa số dư.
- **Quyền:** chủ module kiểm tra người tạo, người hưởng và nguồn yêu cầu; frontend chỉ hỗ trợ giao diện. Yêu cầu rút từ 5 triệu đồng cần hai Admin khác nhau duyệt.
- **Tính nguyên tử:** phán quyết, trạng thái, lịch sử, Ledger và outbox cùng transaction SQL nội bộ; gọi provider ở ngoài transaction. Lời gọi payout từ xa không nằm trong tính nguyên tử của transaction SQL.
- **Timeout:** `PENDING_UNKNOWN` chờ truy vấn/đối soát. Không coi timeout là thất bại đã xác nhận để hoàn ví khi provider có thể đã chi; chỉ thất bại đã xác nhận hoặc từ chối mới đảo tiền, đúng một lần.
- **Fixture:** mô phỏng sổ kép riêng ở môi trường phát triển, có số dư/ký quỹ/quỹ/công nợ/biên nhận mong đợi và các ca trùng, đồng thời, timeout. Không ghi vào Ledger thật; nghiệm thu số dư phải chạy lại trên Ledger thật.
- **Mốc:** hợp đồng/schema/fixture ngày 16/10; ký quỹ/giải ngân ở W2, rút tiền ở W3, hoàn tiền/quỹ ở W4. Thuận tiếp nhận tích hợp thật từ 07–10/11 hoặc sớm hơn khi nhận được implementation.

### C08 — PayoutProvider (Nhựt Huy/Thuận)

- **Phạm vi demo theo nguồn:** Mock/Manual được chấp nhận; không dùng VNPay Payment Gateway làm payout.
- **Trường dữ liệu:** `payoutId`, `withdrawalId`, metadata ngân hàng của người hưởng, `netAmount`, `idempotencyKey`; kết quả/tham chiếu của submit/query; thao tác mark-paid thủ công có tham chiếu, metadata biên nhận và quyền.
- **Vòng đời:** phê duyệt SQL và ghi outbox → dispatch ngoài transaction → nhận kết quả đã xác nhận → cập nhật Ledger/trạng thái/audit/outbox trong transaction. Kết quả chưa rõ không được đảo tiền sớm; nhập kết quả hàng loạt phải báo rõ dòng trùng, chưa rõ hoặc bị từ chối.
- **Phân biệt:** `payout.provider=mock/manual` có thể dùng cho demo cuối, nhưng **module Ledger/Payment nội bộ phải chạy thật** để nghiệm thu dòng tiền. Biên nhận giả lập phải có dấu demo, không thể hiện như đã chuyển tiền thật.

### C09 — Evidence/Complaint/Metrics (Phi Long ↔ Thuận)

- **Dùng bởi:** P09/P10/P12/P13/P14.
- **Bằng chứng:** tham chiếu chat/file được phân quyền theo phạm vi; các bên của đơn, kết quả OTP/QR lấy/giao hàng, sự cố và SOS. Màn Admin vẫn phải kiểm tra quyền xem tin nhắn.
- **Khiếu nại:** hồ sơ chính thức gồm ID, đơn, các bên, `openedAt`, trạng thái, phiên bản, lý do và SLA. Long phụ trách tạo hồ sơ/sự kiện UC-50; Thuận phụ trách xử lý/kháng nghị UC-51. Dùng một schema và kho hồ sơ, không tạo bản sao `AdminComplaint`.
- **Phán quyết:** contract của hồ sơ cung cấp `judgementId` và `judgementVersion`; kết quả thực hiện tiền liên kết bằng `ledgerTxnId` theo C07. Hồ sơ phải cho biết phiên bản phán quyết còn hiệu lực để khoản bồi thường chờ được kiểm tra lại trước khi chi.
- **Chỉ số:** số đơn đã nhận/hoàn tất/đúng hạn, tổng điểm và số đánh giá, khiếu nại bị xử thua; phân biệt hủy do người giao với hủy do người gửi. Có `eventId`, `orderId`, `eventAt`, kỳ tính và revision; contract phải giải thích cách xử lý dữ liệu sửa hoặc đến trễ.
- **Mốc:** bằng chứng/chỉ số ngày 16/10, hợp đồng tạo khiếu nại ngày 21/10. Đề xuất có khiếu nại thật từ 03–05/11 để Thuận nối từ 05–10/11; đây là mốc bàn giao mong muốn, không thay deadline UC-50 trong nguồn.

### C10 — Reputation/Eligibility (Thuận → Quốc An/Luân/Long)

- **Truy vấn:** `publicCourierId`, `score`, `tier`, `completedOrders`, loại/thời hạn hạn chế, `reviewRequired`, `version`, `calculatedAt`. DTO `/me/trust` chỉ trả dữ liệu của người giao đang đăng nhập.
- **Đầu vào chỉ số:** C09/C05 và sự kiện hủy đơn/phán quyết. Sổ penalty của Reputation tách khỏi sổ cái tiền, chống trùng theo sự kiện nguồn/đơn; tính lại điểm phải bảo toàn penalty.
- **Module sử dụng:** Quốc An kiểm tra KYC, hạng, điểm, hạn mức số đơn và giá trị; Long hiển thị ở UC-48; Luân đồng bộ các trường chính thức của hồ sơ người giao. Pricing/Order snapshot hoa hồng tại thời điểm nhận đơn.
- **Mốc:** DTO ngày 16/10, fixture calculator ở T3, chỉ số thật ở W3/W4. Đổi hạng không thay hoa hồng đã snapshot của đơn.

### C11 — DomainEventEnvelope và Audit/Notification APIs (Thuận → tất cả)

```json
{
  "eventId": "uuid",
  "eventType": "ParameterChanged",
  "schemaVersion": 1,
  "aggregateType": "Parameter",
  "aggregatePublicId": "uuid-or-stable-key",
  "aggregateVersion": 2,
  "occurredAt": "2026-10-13T00:00:00Z",
  "actorPublicId": "uuid-or-system",
  "correlationId": "request-correlation-id",
  "payload": {}
}
```

Envelope trên là đề xuất và dùng dữ liệu minh họa. Payload có kiểu theo sự kiện, không sao chép toàn bộ entity hoặc dữ liệu cá nhân. Publisher ghi outbox trong cùng transaction SQL thay đổi nghiệp vụ. Dispatcher ghi biên nhận và chống trùng theo consumer + event; notification thêm người nhận + kênh. Consumer lỗi không được sửa dữ liệu nghiệp vụ SQL nguồn.

Topic thông báo dùng danh tính và quyền thật của người dùng; thống nhất endpoint WebSocket/cơ chế kết nối lại với Long. Bộ ghi audit chỉ thêm bản ghi và che dữ liệu nhạy cảm; API đọc P02 không được tạo phụ thuộc vòng với bên phát audit. Đề xuất hợp đồng outbox/sự kiện từ 14–15/10; các module phát sự kiện gắn dần tới 10/11.

### C12 — JobHandler và DashboardReadPort (mọi owner ↔ Thuận)

- **Đề xuất job:** tên, lịch/múi giờ, trạng thái bật, thời hạn khóa và `run(clock, cursor, limit)`. Kết quả chứa số lượng, lỗi, `nextCursor`, `correlationId`; command nghiệp vụ bên trong gọi facade của chủ module. Runner ghi `SUCCESS/FAILED/SKIPPED_LOCKED/UNAVAILABLE` với ý nghĩa rõ; thiếu provider không được báo thành công.
- **Phân chủ module:** Thành Huy phụ trách hết hạn đơn, giữ thanh toán và quá hạn lấy hàng; Quốc An phụ trách hết hạn chào giá; Nhựt Huy phụ trách VNPay, giải ngân, đối soát và toàn vẹn sổ cái; Luân phụ trách thông tin xác thực. Thuận phụ trách outbox/thông báo/uy tín/SLA/retention, phối hợp từng chủ module. PickupOverdue cần phối hợp Order + Ledger + Trust trong cùng luồng nghiệp vụ; không tách thành ba job cạnh tranh xử lý cùng đơn.
- **Đề xuất dashboard:** khoảng thời gian có giới hạn; định nghĩa, nguồn và trạng thái null/không khả dụng của từng chỉ số; `asOf`, tiền tệ, múi giờ. Tổng số liệu lấy từ SQL nguồn thật; provider lỗi một phần phải hiện trạng thái thiếu dữ liệu, không đổi thành số 0.
- **Mốc:** hợp đồng job ngày 16/10, chỉ số dashboard ngày 21/10; dựng runner ở T3, gắn handler thật theo W2–W4, hoàn chỉnh trước 10/11.

## 3. Cách phát triển với mock

1. Ghi DTO, lỗi và fixture trạng thái trong tài liệu hợp đồng; đánh dấu `PROPOSED` tới khi chủ module xác nhận. Tạo interface khi gói đang triển khai cần sử dụng.
2. Service nghiệp vụ thật của Thuận gọi port; frontend dùng API thật của Thuận. Adapter fixture ở backend là đường chính; mock chỉ ở frontend dùng cho layout tạm và được bỏ khi có luồng xuyên frontend/backend.
3. Fixture có ID ổn định, chuyển trạng thái, phiên bản, đọc lại sau ghi, kết quả mong đợi, Clock và cơ chế reset. Bao gồm ca không hợp lệ, đồng thời, lỗi và kết quả provider chưa rõ. Tách database/namespace demo; không ghi dữ liệu giả vào SQL dùng chung hoặc số dư thật.
4. Chọn provider rõ ràng cho từng dependency: Identity base dùng `real`; Order/KYC chưa có dùng `fixture` ở môi trường phát triển. Không tự chuyển sang fixture khi adapter thật lỗi.
5. Profile production và staging cuối cấm fixture của module nội bộ; khởi động phải thất bại nếu cấu hình giả lập trái chế độ. Tính năng thật đang bật nhưng thiếu adapter phải báo không khả dụng hoặc lỗi cấu hình rõ ràng, không trả thành công khi chưa thực hiện. MockPayoutProvider dùng cho demo được đưa vào allowlist riêng theo phạm vi Excel.
6. Hiện badge demo trong workspace và mô tả chế độ ở API/tài liệu phát triển; endpoint health công khai không lộ secret hoặc DTO nội bộ.

Mock store chỉ sở hữu dữ liệu mô phỏng, không tạo thêm bảng Order/User/Wallet/KYC cho production. Nếu gói Thuận cần logic trong module chính thức nhưng entity chưa có, trước mắt dùng port của chủ module và DTO fixture. Entity/schema production chỉ có một chủ module và được hòa cùng nhánh của chủ module khi bàn giao.

## 4. Fixture catalog tối thiểu

| Nhóm | Kịch bản cần có |
| --- | --- |
| Actors | Người gửi, người giao, Moderator, Admin A/Admin B; bị khóa; không đủ quyền; tự duyệt/người hưởng; phiên bị thu hồi |
| Params | 109 tham số gốc, thay đổi đề xuất, sai giá trị/ràng buộc giữa trường, phiên bản cũ, snapshot đơn cũ |
| KYC | Chờ duyệt/đã duyệt/từ chối; blacklist/trùng/tự duyệt; liên kết ảnh hết hạn; duyệt đồng thời |
| Orders | Sạch/nghi ngờ/nội dung cấm; chờ duyệt/mở/đã phân công/đang giao/đã giao/hoàn tất/tranh chấp; phiên bản cũ |
| Notifications | Bắt buộc/tùy chọn; sự kiện trùng; kênh lỗi/hết lượt thử lại; dữ liệu người khác; thông báo bị lỡ khi kết nối lại |
| Promotions | Đang hoạt động/tạm dừng/hết hạn/hết lượt/sai đối tượng; giữ lượt đồng thời |
| Reputation | Người mới 50 điểm; biên từng hạng; penalty 5 điểm bị gửi lại; lần hủy thứ tư trong 30 ngày; chạy lại job đêm; điểm thấp cần xem xét |
| Withdrawals | Dưới/bằng/trên ngưỡng duyệt hai cấp; tự duyệt; hai Admin; đã chi/từ chối/thất bại/kết quả chưa rõ; nhập kết quả lặp |
| Complaints/fund | Tiền đang giữ/đóng băng/đã giải ngân/công nợ; thiếu bằng chứng; kháng nghị quá hạn/trùng; có/không bảo hiểm; vượt mức; thiếu quỹ/thứ tự FIFO; phán quyết bị thay thế khi bồi thường đang chờ |
| Jobs | Hai instance; khóa hết hạn; crash sau khi xử lý một phần; đầu vào trùng/sai thứ tự; provider không khả dụng; không có dữ liệu |
| Reports | Bình thường/rỗng/lỗi một phần; biên tháng; định nghĩa tổng thu/hoa hồng/thu nhập ròng; chuỗi giống công thức CSV |

## 5. Thay mock bằng real adapter

| Bước | Bằng chứng cần lưu |
| --- | --- |
| Nhận contract/commit của chủ module | Đối chiếu trường, enum, lỗi, trạng thái và transaction; ghi rõ khác biệt, không ghi đè mã nguồn đồng đội |
| Chạy các kịch bản contract trên adapter mới | Giữ đầu vào/kết quả mong đợi; giải thích khác biệt với fixture; không biến lỗi thành thành công |
| Nối từng dependency thật trong môi trường phát triển | Nối lần lượt từng provider; service/UI giữ nguyên nếu DTO ổn định |
| Chạy tích hợp SQL/Mongo | Kiểm tra tính nguyên tử của trạng thái/lịch sử/Ledger/outbox, constraint/khóa, rollback, idempotency và chạy lại |
| Chạy luồng xuyên module | KYC → người giao, kiểm duyệt → sàn, payout/phán quyết/quỹ/khóa tài khoản có tiền, thông báo/audit/job |
| Gỡ fixture nội bộ khỏi staging cuối | Có danh sách provider rõ ràng; không còn dữ liệu giả trộn với số liệu thật |
| Cập nhật task | Ghi INTEGRATED/VERIFIED khi có kiểm thử/nghiệm thu; nếu chưa có phần của chủ module, ghi WAITING_FOR_INTEGRATION và giữ demo độc lập |

Lịch bàn giao trong plan là đề xuất, chưa phải cam kết hoặc tin nhắn đã gửi cho đồng đội. Khi chưa có phản hồi, tiếp tục phát triển bằng contract đề xuất và fixture, giữ phạm vi thay adapter hẹp và ghi rõ phần chờ xác nhận. Các điều kiện nghiệp vụ tài chính quan trọng phải được review trước khi bật adapter thật; chỉ đổi cấu hình chưa đủ để xác nhận tích hợp.

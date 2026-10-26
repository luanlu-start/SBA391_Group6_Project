# Báo Cáo Luồng Nghiệp Vụ & Phân Tích Chức Năng: UC-34, UC-35, UC-36 & UC-40

> **Dự án**: SBA391 - FPTPost Logistics & Crowdsourced Delivery Platform  
> **Phân hệ**: Payment & Wallet Core (Nạp tiền, Thanh toán, Ký quỹ, Rút tiền & Quản lý Ví)  
> **Nguyên tắc cốt lõi**: ACID Transactions, Pessimistic Locking, Sổ cái kép (Double-Entry Ledger), Idempotency.

---

## 1. Tổng Quan Kiến Trúc & Mối Quan Hệ Giữa 4 Use Cases

Hệ thống tài chính nội bộ của FPTPost vận hành trên cơ chế **Ví điện tử + Ký quỹ giữ hộ (Escrow) + Sổ cái đối ứng kép (Double-Entry Ledger)**. Bốn Use Case này tạo nên toàn bộ chu trình dòng tiền khép kín:

1. **Dòng tiền vào (Money In - UC-34)**: Nạp tiền từ Ngân hàng qua cổng VNPay vào Ví khả dụng.
2. **Dòng tiền bảo đảm (Money Escrowed - UC-35)**: Giam giữ tiền từ Ví/VNPay vào quỹ Ký quỹ đơn hàng.
3. **Dòng tiền thu nhập (Money Earned - UC-36)**: Giải ngân tiền ký quỹ về Ví khả dụng của Bưu tá sau khi giao hàng thành công.
4. **Dòng tiền ra (Money Out - UC-40)**: Rút tiền từ Ví khả dụng về tài khoản Ngân hàng cá nhân.

```
                                ┌────────────────────────────────┐
                                │   UC-34: Nạp tiền qua VNPay    │
                                │ (Cổng VNPay ──► Ví khả dụng)   │
                                └──────────────┬─────────────────┘
                                               │
                                               ▼
[Người gửi (SENDER)] ─────────────► [ Ví khả dụng (available) ]
       │                                       │
       ▼                                       ▼
 [ UC-35: Ký Quỹ Đơn Hàng ] ───(Ví nội bộ hoặc VNPay)───► [ Khoản Ký Quỹ (Escrow: HELD) ]
                                                                      │
                                                           Đơn giao hoàn tất
                                                                      │
                                                                      ▼
 [ Bưu tá (COURIER) ] ◄───(Cộng ví khả dụng: available)─── [ UC-36: Giải Ngân Ký Quỹ ]
       │
       ▼
 [ UC-40: Rút Tiền Về Ngân Hàng ] ───(Khóa tạm: pendingWithdraw)───► [ Chuyển Khoản Ngân Hàng ]
```

---

## 2. Use Case 34: Nạp Tiền Vào Ví Qua VNPay (Wallet Topup via VNPay)

### 2.1. Mục Tiêu & Tiền Điều Kiện
- **Mục tiêu**: Người dùng (cả người gửi và bưu tá) nạp tiền từ tài khoản/thẻ ngân hàng qua cổng thanh toán VNPay vào Ví nội bộ FPTPost để thanh toán cước phí hoặc lưu trữ số dư.
- **Tiền điều kiện**: 
  - Người dùng đã đăng nhập hệ thống (JWT hợp lệ).
  - Số tiền nạp mỗi lần: Tối thiểu **20.000 VNĐ** (`MIN_TOPUP`), tối đa **10.000.000 VNĐ** (`MAX_TOPUP`).
  - Hạn mức ví: Tổng số dư khả dụng sau khi nạp không vượt quá **50.000.000 VNĐ** (`MAX_BALANCE`).

### 2.2. Luồng Xử Lý Chi Tiết (Working Flow)
1. **Khởi tạo yêu cầu nạp tiền (API-084)**:
   - Người dùng nhập số tiền nạp tại `WalletPage.tsx`, chọn ngân hàng (tùy chọn) và nhấn "Nạp tiền".
   - Frontend gửi `POST /api/v1/wallet/topups` với body `{ amount: 100000, bankCode?: string }`.
   - `WalletController.topupWallet` tiếp nhận và ủy quyền cho `WalletService.createTopupUrl`:
     - Kiểm tra hạn mức nạp: `20.000 <= amount <= 10.000.000`.
     - Lấy hoặc tự động tạo ví (`getOrCreateWallet`), kiểm tra `available + amount <= 50.000.000`.
     - Sinh mã giao dịch duy nhất `txnRef = TOPUP_{userId}_{currentTimeMillis}`.
     - Tạo bản ghi `PaymentTransaction` với `purpose = WALLET_TOPUP`, `status = PENDING`.
     - Tính toán tham số VNPay và sinh chữ ký số HMAC-SHA512.
     - Trả về `paymentUrl` của cổng VNPay cho Frontend.
2. **Khách hàng thanh toán trên cổng VNPay**:
   - Trình duyệt điều hướng tới cổng thanh toán VNPay để người dùng quét mã QR hoặc nhập thẻ ngân hàng.
3. **Xử lý Webhook IPN từ VNPay (API-088)**:
   - Sau khi giao dịch hoàn tất, cổng VNPay gửi Webhook IPN ngầm đến `POST /api/v1/payment/vnpay-ipn`.
   - `VNPayService.processIpn` thực hiện:
     - Kiểm tra tính toàn vẹn và chữ ký số (`vnp_SecureHash`).
     - Tìm kiếm `PaymentTransaction` theo `txnRef`. Kiểm tra trạng thái giao dịch (Idempotency).
     - Nếu thanh toán thành công (`vnp_ResponseCode == 00` && `vnp_TransactionStatus == 00`):
       - Cập nhật `PaymentTransaction.status = SUCCESS`.
       - Áp dụng **Pessimistic Write Lock** (`walletRepository.findByUserIdForUpdate(userId)`).
       - Tăng số dư khả dụng: `wallet.available = available + amount`.
       - Ghi bút toán sổ cái kép qua `ledgerService.recordWalletTopup(userId, amount, txnRef)` với `idempotencyKey = TOPUP_{txnRef}`:
         - **Ghi Nợ (DEBIT)**: `VNPAY_CLEARING` (Tài sản thanh toán trung gian VNPay).
         - **Ghi Có (CREDIT)**: `USER_WALLET_{userId}` (Nghĩa vụ nợ với khách hàng).

### 2.3. Bảng Phân Trách Nhiệm Hàm (Function Responsibility Matrix)

| Lớp (Layer) | Tên Tệp / Thành Phần | Tên Hàm / Method | Nhiệm Vụ Cụ Thể |
| :--- | :--- | :--- | :--- |
| **Frontend UI** | `client/src/pages/WalletPage.tsx` | `handleTopup(e)` | Validate form số tiền (20k - 10M), gọi API nạp và điều hướng sang VNPay `paymentUrl` |
| **Frontend API**| `client/src/api/walletApi.ts` | `topup(data)` | Gửi request `POST /api/v1/wallet/topups` |
| **Controller** | `WalletController.java` | `topupWallet(...)` | Nhận JWT, trích xuất `userId`, parse DTO `WalletTopupRequest`, gọi Service |
| **Service** | `WalletService.java` | `createTopupUrl(...)` | Validate hạn mức ví, sinh `txnRef`, tạo `PaymentTransaction(PENDING)`, sinh HMAC-SHA512 URL VNPay |
| **Service** | `VNPayService.java` | `processIpn(...)` | Xử lý Webhook VNPay, xác thực chữ ký, khóa ví bằng Pessimistic Lock, cộng `available`, gọi LedgerService |
| **Ledger** | `LedgerService.java` | `recordWalletTopup(...)` | Ghi bút toán sổ cái kép (Nợ `VNPAY_CLEARING`, Có `USER_WALLET_{userId}`), ngăn lặp bằng Idempotency Key `TOPUP_{txnRef}` |

---

## 3. Use Case 35: Thanh Toán & Ký Quỹ Đơn Hàng (Order Escrow Payment)

### 3.1. Mục Tiêu & Tiền Điều Kiện
- **Mục tiêu**: Người gửi (Sender) thanh toán cước phí để giữ tiền ký quỹ (`Escrow`), bảo đảm khả năng chi trả cho shipper. Khi ký quỹ thành công, đơn hàng đổi trạng thái sang `ESCROWED` và xuất hiện trên sàn nhận đơn.
- **Tiền điều kiện**: 
  - Đơn hàng tồn tại, đang ở trạng thái chờ ký quỹ (`PENDING_ESCROW` / `PENDING`).
  - Người thực hiện phải chính là người tạo đơn hàng (`order.senderId == userId`).
  - Nếu chọn trả bằng ví: số dư khả dụng `wallet.available >= order.totalFee`.

### 3.2. Luồng Xử Lý Chi Tiết (Working Flow)
- **Nhánh A: Thanh toán bằng Ví nội bộ (`WALLET`)**:
  1. Frontend gọi `POST /api/v1/orders/{id}/payment` với `{ "paymentMethod": "WALLET" }`.
  2. Backend áp dụng **Pessimistic Write Lock** (`findByUserIdForUpdate`) trên ví người gửi.
  3. Kiểm tra số dư: Trừ `available` đi `totalFee`, đồng thời tăng `escrowHeld` tương ứng.
  4. Khởi tạo bản ghi `Escrow` với trạng thái `HELD`, `frozen = false`.
  5. Cập nhật trạng thái đơn hàng sang `ESCROWED`.
  6. Ghi bút toán sổ cái kép: Nợ `USER_WALLET_{senderId}`, Có `ESCROW_ORDER_{orderId}`.
- **Nhánh B: Thanh toán qua Cổng VNPay (`VNPAY`)**:
  1. Frontend gửi yêu cầu, backend sinh mã tham chiếu `txnRef` (prefix `ESCROW_`) và tạo `PaymentTransaction` trạng thái `PENDING`.
  2. Backend tính toán HMAC-SHA512 checksum và trả về URL chuyển hướng sang cổng thanh toán VNPay.
  3. Khách hàng thanh toán trên VNPay.
  4. VNPay gửi Webhook `POST /api/v1/payment/vnpay-ipn`:
     - Kiểm tra chữ ký an toàn (`vnp_SecureHash`).
     - Kiểm tra trạng thái giao dịch (Idempotency).
     - Nếu thành công (`vnp_ResponseCode == 00`): Cập nhật `PaymentTransaction` thành `SUCCESS`, tạo `Escrow` (`HELD`, `paymentMethod = VNPAY`), chuyển đơn sang `ESCROWED`.
     - Ghi bút toán sổ cái kép: Nợ `VNPAY_CLEARING`, Có `ESCROW_ORDER_{orderId}`.

### 3.3. Bảng Phân Trách Nhiệm Hàm (Function Responsibility Matrix)

| Lớp (Layer) | Tên Tệp / Thành Phần | Tên Hàm / Method | Nhiệm Vụ Cụ Thể |
| :--- | :--- | :--- | :--- |
| **Frontend UI** | `client/src/pages/OrderPaymentPage.tsx` | `handleSubmitPayment()` | Thu thập phương thức (`WALLET`/`VNPAY`), gọi API thanh toán và điều hướng |
| **Frontend API**| `client/src/api/orderApi.ts` | `payOrder(id, data)` | Gửi request `POST /api/v1/orders/{id}/payment` |
| **Controller** | `OrderController.java` | `payOrder(...)` | Nhận JWT, trích xuất `userId`, parse DTO `OrderPaymentRequest`, ủy quyền cho Service |
| **Service** | `OrderPaymentService.java` | `payOrder(...)` | Điều phối logic ký quỹ ví và sinh link VNPay |
| **Service** | `VNPayService.java` | `createPaymentUrl(...)` | Mã hóa tham số, sinh chuỗi checksum HMAC-SHA512 của VNPay |
| **Service** | `VNPayService.java` | `processIpn(...)` | Kiểm tra chữ ký webhook, bảo đảm tính Idempotent và xác nhận ký quỹ khi thanh toán VNPay thành công |
| **Ledger** | `LedgerService.java` | `recordOrderEscrowFromWallet(...)` | Ghi bút toán sổ cái kép ký quỹ bằng ví |
| **Ledger** | `LedgerService.java` | `recordOrderEscrowFromVNPay(...)` | Ghi bút toán sổ cái kép ký quỹ từ VNPay |

---

## 4. Use Case 36: Tra Cứu & Giải Ngân Ký Quỹ Cho Người Giao (Courier Escrow Release)

### 4.1. Mục Tiêu & Tiền Điều Kiện
- **Mục tiêu**: Sau khi đơn hàng được giao thành công, khoản tiền ký quỹ giam giữ trong hệ thống được tự động hoặc chỉ định giải ngân về ví khả dụng của Người giao hàng (Courier).
- **Tiền điều kiện**:
  - Đơn hàng tồn tại và đã có bưu tá nhận (`order.courierId != null`).
  - Khoản ký quỹ tồn tại, trạng thái đang là `HELD`.
  - Khoản ký quỹ không bị phong tỏa (`escrow.frozen == false`).
  - Không được giải ngân trùng lặp (`escrow.status != RELEASED`).

### 4.2. Luồng Xử Lý Chi Tiết (Working Flow)
- **Luồng 1: Tra cứu thông tin ký quỹ đơn hàng (API-092)**:
  1. Frontend gọi `GET /api/v1/orders/{id}/escrow`.
  2. Backend xác thực: Người gọi phải là Người gửi (`senderId`), Người giao (`courierId`), hoặc `ADMIN`.
  3. Trả về chi tiết: Số tiền ký quỹ, phương thức thanh toán, trạng thái ký quỹ (`HELD`/`RELEASED`/`REFUNDED`), cờ phong tỏa `frozen`, và thời gian giải ngân.
- **Luồng 2: Giải ngân tiền ký quỹ cho bưu tá (API-093)**:
  1. Quản trị viên (hoặc tiến trình tự động sau hoàn tất đơn) gọi `POST /api/v1/admin/escrow/{orderId}/release`.
  2. Xác thực quyền hạn `ADMIN`.
  3. Kiểm tra đơn hàng đã gán bưu tá và khoản ký quỹ đang ở trạng thái `HELD`, không bị đóng băng.
  4. Nếu khoản ký quỹ ban đầu thanh toán từ ví (`WALLET`): Khóa ví người gửi, giảm `escrowHeld`.
  5. Khóa ví bưu tá (tự tạo ví mới nếu bưu tá chưa có ví): Cộng `amount` vào `wallet.available`.
  6. Cập nhật `escrow.status = RELEASED` và lưu dấu thời gian `releaseAt = Instant.now()`.
  7. Ghi bút toán sổ cái kép với Idempotency Key `ESCROW_RELEASE_{orderId}_{escrowRef}`: Nợ `ESCROW_ORDER_{orderId}` (giảm nghĩa vụ giam giữ), Có `USER_WALLET_{courierId}` (tăng số dư cho shipper).

### 4.3. Bảng Phân Trách Nhiệm Hàm (Function Responsibility Matrix)

| Lớp (Layer) | Tên Tệp / Thành Phần | Tên Hàm / Method | Nhiệm Vụ Cụ Thể |
| :--- | :--- | :--- | :--- |
| **Frontend UI** | `client/src/pages/OrderDetailPage.tsx` | `fetchEscrow(orderId)` | Tải và hiển thị thẻ thông tin bảo chứng tài chính / ký quỹ |
| **Frontend UI** | `client/src/pages/OrderDetailPage.tsx` | `handleReleaseEscrow()` | Nút bấm Admin xác nhận giải ngân ký quỹ cho shipper |
| **Frontend API**| `client/src/api/orderApi.ts` | `getEscrow(id)` | Gọi `GET /api/v1/orders/{id}/escrow` |
| **Frontend API**| `client/src/api/orderApi.ts` | `releaseEscrow(orderId)`| Gọi `POST /api/v1/admin/escrow/{orderId}/release` |
| **Controller** | `EscrowController.java` | `getEscrow(...)` | Kiểm tra token, gọi lấy chi tiết ký quỹ |
| **Controller** | `EscrowController.java` | `releaseEscrow(...)` | Kiểm tra quyền `hasRole('ADMIN')`, gọi Service thực hiện giải ngân |
| **Service** | `OrderPaymentService.java` | `getEscrowByOrderId(...)` | Kiểm tra quyền sở hữu (sender/courier/admin) và map DTO `EscrowResponse` |
| **Service** | `OrderPaymentService.java` | `releaseEscrow(...)` | Kiểm tra điều kiện giải ngân, áp dụng pessimistic lock điều chỉnh số dư 2 ví |
| **Ledger** | `LedgerService.java` | `recordEscrowRelease(...)` | Ghi nhận bút toán kép giải ngân, ngăn chặn trùng lặp qua Idempotency Key |

---

## 5. Use Case 40: Yêu Cầu Rút Tiền Từ Ví Về Ngân Hàng (Wallet Withdrawal)

### 5.1. Mục Tiêu & Tiền Điều Kiện
- **Mục tiêu**: Cho phép người dùng (cả người gửi và bưu tá) lập lệnh rút số dư khả dụng (`available`) về tài khoản ngân hàng thụ hưởng, theo dõi tiến độ và cho phép hủy khi chưa duyệt.
- **Tiền điều kiện**:
  - Người dùng đã đăng nhập và sở hữu ví.
  - Số tiền rút tối thiểu: **50.000 VNĐ** (`MIN_WITHDRAWAL`).
  - Số dư khả dụng `available >= amount + fee`.
  - Khi hủy lệnh: Yêu cầu phải thuộc sở hữu chính chủ và trạng thái đang là `PENDING_REVIEW`.

### 5.2. Luồng Xử Lý Chi Tiết (Working Flow)
- **Luồng 1: Tạo yêu cầu rút tiền (API-097)**:
  1. Người dùng mở Modal rút tiền trên trang Ví (`WalletPage.tsx`), nhập số tiền, ngân hàng, số tài khoản, tên chủ thẻ.
  2. Frontend gửi `POST /api/v1/wallet/withdrawals`.
  3. Backend khóa ví người dùng bằng Pessimistic Write Lock (`findByUserIdForUpdate`).
  4. Trừ số dư khả dụng `wallet.available = available - amount`, chuyển sang tạm giữ rút `wallet.pendingWithdraw = pendingWithdraw + amount`.
  5. Sinh mã yêu cầu dạng `WDR-YYYYMMDD-XXXXXXXX` (ví dụ `WDR-20261026-9A15CEFA`).
  6. Tạo bản ghi `WithdrawalRequest` trạng thái `PENDING_REVIEW`.
  7. Ghi bút toán sổ cái kép: Nợ `USER_WALLET_{userId}`, Có `CLEARING_WITHDRAWAL`.
- **Luồng 2: Xem danh sách yêu cầu rút tiền của tôi (API-098)**:
  1. Gọi `GET /api/v1/wallet/withdrawals?page=0&size=10`.
  2. Trả về danh sách phân trang kèm trạng thái (`PENDING_REVIEW`, `APPROVED`, `REJECTED`, `CANCELLED`).
- **Luồng 3: Hủy yêu cầu rút tiền đang chờ duyệt (API-099)**:
  1. Người dùng bấm "Hủy yêu cầu" trên bảng lịch sử rút tiền.
  2. Gửi `POST /api/v1/wallet/withdrawals/{id}/cancel`.
  3. Kiểm tra yêu cầu rút tồn tại, đúng `userId` và trạng thái còn là `PENDING_REVIEW`.
  4. Khóa ví người dùng: Giảm `pendingWithdraw`, hoàn trả lại vào `available`.
  5. Đổi trạng thái `WithdrawalRequest` thành `CANCELLED`.
  6. Ghi bút toán sổ cái hoàn tác: Nợ `CLEARING_WITHDRAWAL`, Có `USER_WALLET_{userId}`.

### 5.3. Bảng Phân Trách Nhiệm Hàm (Function Responsibility Matrix)

| Lớp (Layer) | Tên Tệp / Thành Phần | Tên Hàm / Method | Nhiệm Vụ Cụ Thể |
| :--- | :--- | :--- | :--- |
| **Frontend UI** | `client/src/pages/WalletPage.tsx` | `handleCreateWithdrawal()` | Mở modal, validate form rút tiền và gửi request tạo lệnh |
| **Frontend UI** | `client/src/pages/WalletPage.tsx` | `handleCancelWithdrawal()` | Xác nhận và gửi yêu cầu hủy lệnh rút đang chờ |
| **Frontend API**| `client/src/api/walletApi.ts` | `createWithdrawal(data)` | Gọi `POST /api/v1/wallet/withdrawals` |
| **Frontend API**| `client/src/api/walletApi.ts` | `getMyWithdrawals(page, size)`| Gọi `GET /api/v1/wallet/withdrawals` |
| **Frontend API**| `client/src/api/walletApi.ts` | `cancelWithdrawal(id)` | Gọi `POST /api/v1/wallet/withdrawals/{id}/cancel` |
| **Controller** | `WithdrawalController.java` | `createWithdrawal(...)` | Nhận JWT, trích xuất ID, chuyển tiếp yêu cầu rút tiền |
| **Controller** | `WithdrawalController.java` | `getMyWithdrawals(...)` | Nhận query param phân trang, trả về `PageResponse<WithdrawalResponse>` |
| **Controller** | `WithdrawalController.java` | `cancelWithdrawal(...)` | Nhận `id`, chuyển tiếp yêu cầu hủy lệnh rút |
| **Service** | `WithdrawalService.java` | `createWithdrawalRequest(...)` | Validate hạn mức, khóa ví, dịch chuyển `available -> pendingWithdraw`, lưu DB |
| **Service** | `WithdrawalService.java` | `cancelWithdrawalRequest(...)` | Validate trạng thái, khóa ví, hoàn trả `pendingWithdraw -> available`, chuyển trạng thái `CANCELLED` |
| **Service** | `WithdrawalService.java` | `getMyWithdrawals(...)` | Truy vấn phân trang từ `WithdrawalRequestRepository` |
| **Ledger** | `LedgerService.java` | `recordWithdrawalRequest(...)` | Ghi bút toán kép tạm giữ tiền chờ chuyển khoản ngân hàng |
| **Ledger** | `LedgerService.java` | `recordWithdrawalCancellation(...)`| Ghi bút toán kép hoàn tiền từ tài khoản tạm giữ về ví người dùng |

---

## 6. Tổng Hợp Bút Toán Sổ Cái Kép (Double-Entry Ledger Matrix)

Mọi biến động tài chính trong cả 4 Use Case đều tuân thủ nguyên tắc cân bằng kế toán `Tổng Nợ (Debit) == Tổng Có (Credit)`:

| Nghiệp Vụ (Use Case) | Mã Giao Dịch Ledger (`type`) | Tài Khoản Ghi Nợ (DEBIT) | Tài Khoản Ghi Có (CREDIT) | Ý Nghĩa Kế Toán |
| :--- | :--- | :--- | :--- | :--- |
| **UC-34: Nạp tiền ví qua VNPay** | `WALLET_TOPUP` | `VNPAY_CLEARING` | `USER_WALLET_{userId}` | Tăng tài sản thanh toán trung gian VNPay, tăng nghĩa vụ nợ (số dư ví) người dùng |
| **UC-35: Ký quỹ qua Ví** | `ORDER_ESCROW_HOLD` | `USER_WALLET_{senderId}` | `ESCROW_ORDER_{orderId}` | Giảm nghĩa vụ ví sender, tăng quỹ ký quỹ giam giữ cho đơn hàng |
| **UC-35: Ký quỹ qua VNPay** | `ORDER_ESCROW_HOLD` | `VNPAY_CLEARING` | `ESCROW_ORDER_{orderId}` | Tăng tài sản thanh toán trung gian VNPay, tăng quỹ ký quỹ giam giữ |
| **UC-36: Giải ngân ký quỹ** | `ESCROW_RELEASE` | `ESCROW_ORDER_{orderId}` | `USER_WALLET_{courierId}` | Giải phóng quỹ ký quỹ giam giữ, chuyển thành số dư khả dụng ví shipper |
| **UC-40: Tạo yêu cầu rút tiền**| `WITHDRAWAL_REQUEST` | `USER_WALLET_{userId}` | `CLEARING_WITHDRAWAL` | Giảm số dư khả dụng ví người dùng, chuyển sang tài khoản tạm giữ chờ chi trả |
| **UC-40: Hủy yêu cầu rút tiền**| `WITHDRAWAL_CANCEL` | `CLEARING_WITHDRAWAL` | `USER_WALLET_{userId}` | Giảm tài khoản tạm giữ chi trả, hoàn trả lại số dư khả dụng ví người dùng |

---

## 7. Cơ Chế Bảo Đảm An Toàn Dữ Liệu & Chống Gian Lận

1. **Khóa bi quan (Pessimistic Locking)**: Tất cả phương thức cập nhật số dư ví (`processIpn` nạp tiền, `payOrder`, `releaseEscrow`, `createWithdrawalRequest`, `cancelWithdrawalRequest`) đều gọi `walletRepository.findByUserIdForUpdate(userId)`. Lệnh SQL phát sinh là `SELECT ... FOR UPDATE`, ngăn ngừa triệt để hiện tượng race condition khi người dùng gửi 2 yêu cầu cùng lúc.
2. **Khóa chống lặp (Idempotency Key)**:
   - Nạp tiền ví qua VNPay: `TOPUP_{txnRef}`.
   - Ký quỹ qua VNPay: `ESCROW_VNPAY_{txnRef}`.
   - Giải ngân Escrow: `ESCROW_RELEASE_{orderId}_{escrowRef}`.
   - Yêu cầu rút tiền: `WITHDRAWAL_REQ_{requestCode}`.
   - Hủy rút tiền: `CANCEL_WDR_{requestCode}`.
   Nếu webhook hoặc API bị gọi lại nhiều lần do mạng chập chờn, hệ thống tự động phát hiện `existsByIdempotencyKey` và trả về kết quả đã ghi nhận trước đó, tuyệt đối không trừ hoặc cộng tiền lần thứ hai.
3. **Phân Quyền Phù Hợp**:
   - `WalletController.topupWallet`: Người dùng chỉ có quyền nạp vào tài khoản của chính mình.
   - `OrderController.payOrder`: Chỉ người tạo đơn (`SENDER`) hoặc `ADMIN`.
   - `EscrowController.getEscrow`: Chỉ `SENDER`, `COURIER` được gán, hoặc `ADMIN`.
   - `EscrowController.releaseEscrow`: Bắt buộc quyền `ADMIN`.
   - `WithdrawalController`: Người dùng chỉ có quyền tạo, tra cứu và hủy yêu cầu rút tiền thuộc chính `userId` của mình.

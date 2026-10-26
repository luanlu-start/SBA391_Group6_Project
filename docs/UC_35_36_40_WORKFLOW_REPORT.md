# Báo Cáo Kỹ Thuật Luồng Nghiệp Vụ, Đặc Tả Hàm & API Chi Tiết: UC-34, UC-35, UC-36 & UC-40

> **Dự án**: SBA391 - FPTPost Logistics & Crowdsourced Delivery Platform  
> **Phân hệ**: Payment & Wallet Core (Nạp tiền, Ký quỹ, Thanh toán, Rút tiền & Quản lý Ví)  
> **Nguyên tắc kỹ thuật**: ACID Transactions, Khóa bi quan (`SELECT ... FOR UPDATE`), Sổ cái đối ứng kép (Double-Entry Ledger), Chống lặp giao dịch (Idempotency Key).

---

## 1. Tổng Quan Kiến Trúc & Vòng Đời Dòng Tiền (Financial Lifecycle)

Toàn bộ hệ thống thanh toán nội bộ của FPTPost được thiết kế theo mô hình **Khép kín & Đối ứng kép**, gồm 4 mắt xích cốt lõi:

```
                                ┌─────────────────────────────────────────┐
                                │       UC-34: NẠP TIỀN VÀO VÍ VNPAY      │
                                │   (Tiền từ Ngân hàng ──► Ví khả dụng)   │
                                └────────────────────┬────────────────────┘
                                                     │
                                                     ▼
┌──────────────────────┐               ┌───────────────────────────┐
│ Người gửi (SENDER)   ├──────────────►│ Ví khả dụng (available)   │
└──────────┬───────────┘               └─────────────┬─────────────┘
           │                                         │
           ▼                                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                  UC-35: THANH TOÁN & KÝ QUỸ ĐƠN HÀNG                    │
│      (Ví khả dụng HOẶC Cổng VNPay ──► Quỹ ký quỹ giam giữ: HELD)        │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                             Giao hàng thành công
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               UC-36: TRA CỨU & GIẢI NGÂN KÝ QUỸ CHO SHIPPER             │
│        (Quỹ ký quỹ HELD ──► Ví khả dụng bưu tá: available)              │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────┐               ┌───────────────────────────┐
│  Bưu tá (COURIER)    │◄──────────────┤ Ví khả dụng (available)   │
└──────────┬───────────┘               └─────────────┬─────────────┘
           │                                         │
           ▼                                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               UC-40: YÊU CẦU RÚT TIỀN TỪ VÍ VỀ NGÂN HÀNG                │
│    (Ví khả dụng ──► Khóa tạm: pendingWithdraw ──► Tài khoản ngân hàng)  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Use Case 34: Nạp Tiền Vào Ví Qua VNPay (Wallet Topup via VNPay)

### 2.1. Mục Tiêu & Tiền Điều Kiện
- **Mục tiêu**: Người dùng nạp tiền từ tài khoản/thẻ ngân hàng nội địa hoặc quốc tế qua cổng thanh toán VNPay vào Ví nội bộ FPTPost để thanh toán cước phí hoặc tích lũy số dư.
- **Tiền điều kiện**:
  - Người dùng có JWT token hợp lệ.
  - Số tiền nạp mỗi lần: Tối thiểu **20.000 VNĐ** (`MIN_TOPUP`), tối đa **10.000.000 VNĐ** (`MAX_TOPUP`).
  - Hạn mức ví: `available + topupAmount <= 50.000.000 VNĐ` (`MAX_BALANCE`).

---

### 2.2. Đặc Tả API Tương Ứng

#### API 1: Khởi tạo URL nạp tiền VNPay (API-084)
- **Endpoint**: `POST /api/v1/wallet/topups` (hoặc `/api/wallet/topups`)
- **Phân quyền**: Người dùng đã xác thực (Bearer JWT).
- **Request Body (`WalletTopupRequest`)**:
  ```json
  {
    "amount": 100000,
    "bankCode": "NCB",
    "language": "vn"
  }
  ```
- **Response (`PaymentCreationResponse`)**: HTTP 200 OK
  ```json
  {
    "code": "200",
    "message": "Tạo URL thanh toán VNPay thành công",
    "data": {
      "code": "00",
      "message": "success",
      "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=10000000&vnp_Command=pay&vnp_CreateDate=20261027010000&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Nap+tien+vao+vi+FPTPost+tai+khoan+1&vnp_OrderType=other&vnp_ReturnUrl=...&vnp_TmnCode=...&vnp_TxnRef=TOPUP_1_1729987200000&vnp_Version=2.1.0&vnp_SecureHash=..."
    }
  }
  ```
- **Mã lỗi có thể trả về**:
  - `WALLET_TOPUP_AMOUNT_INVALID` (400): Số tiền nạp < 20.000đ hoặc > 10.000.000đ.
  - `WALLET_MAX_BALANCE_EXCEEDED` (400): Số dư ví sau khi nạp vượt quá 50.000.000đ.
  - `UNAUTHORIZED` (401): Token JWT không hợp lệ hoặc hết hạn.

#### API 2: Webhook IPN từ VNPay (API-088, UC-42)
- **Endpoint**: `GET /api/payment/vnpay-ipn` (Public Webhook, VNPay gọi ngầm Server-to-Server).
- **Query Params**: Toàn bộ tham số VNPay gửi kèm (`vnp_TxnRef`, `vnp_Amount`, `vnp_ResponseCode`, `vnp_TransactionStatus`, `vnp_SecureHash`, ...).
- **Response (`VNPayIpnResponse`)**: HTTP 200 OK
  ```json
  {
    "RspCode": "00",
    "Message": "Confirm Success"
  }
  ```
- **Mã phản hồi chuẩn VNPay**:
  - `00`: Xác nhận thành công, đã cộng tiền vào ví.
  - `01`: Giao dịch không tồn tại (`Order not found`).
  - `02`: Giao dịch đã được xác nhận trước đó (`Order already confirmed` - Idempotency).
  - `97`: Chữ ký không hợp lệ (`Invalid Checksum`).

---

### 2.3. Chi Tiết Từng Hàm & Thuật Toán Xử Lý (Logic Walkthrough)

#### 1. Frontend UI: `WalletPage.tsx` -> `handleTopup(e: FormEvent)`
- **Vị trí**: [WalletPage.tsx](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/client/src/pages/WalletPage.tsx#L337-L365)
- **Logic thực thi**:
  1. Chặn submit mặc định `e.preventDefault()`.
  2. Parse số tiền từ ô nhập liệu `topupAmount`.
  3. Validate client-side: Kiểm tra `MIN_TOPUP` (20.000đ) <= `topupAmount` <= `MAX_TOPUP` (10.000.000đ). Nếu sai, gán thông báo lỗi `topupError` và dừng.
  4. Bật cờ `topupSubmitting = true`.
  5. Gọi `walletApi.topup({ amount: topupAmount, bankCode: selectedBank })`.
  6. Nhận `paymentUrl` từ backend.
  7. Lưu cờ thông báo `topupSuccessNotice` vào localStorage.
  8. Chuyển hướng trình duyệt `window.location.href = res.data.paymentUrl` sang cổng thanh toán VNPay.

#### 2. Frontend API: `walletApi.ts` -> `topup(data: WalletTopupRequest)`
- **Vị trí**: [walletApi.ts](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/client/src/api/walletApi.ts#L20-L22)
- **Logic**: Gửi HTTP POST với Axios Client tới endpoint `/wallet/topups`, đính kèm Bearer token trong Header.

#### 3. Controller: `WalletController.java` -> `topupWallet(...)`
- **Vị trí**: [WalletController.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/controller/WalletController.java#L74-L84)
- **Logic thực thi**:
  1. Trích xuất UUID `publicId` từ `jwt.getSubject()`, truy vấn `userRepository.findByPublicId` để lấy `userId` (Long).
  2. Log thông tin giao dịch nạp tiền của `userId`.
  3. Gọi `walletService.createTopupUrl(userId, request, httpRequest)`.
  4. Trả về `ResponseEntity.ok(ApiResponse.success(response))`.

#### 4. Service: `WalletService.java` -> `createTopupUrl(userId, request, httpRequest)`
- **Vị trí**: [WalletService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/WalletService.java#L91-L167)
- **Logic thực thi**:
  1. **Validate số tiền**: `if (amount < 20_000 || amount > 10_000_000)` -> ném `WALLET_TOPUP_AMOUNT_INVALID`.
  2. **Kiểm tra ví & trần số dư**: Gọi `getOrCreateWallet(userId)`. Nếu `currentAvailable + amount > 50_000_000` -> ném `WALLET_MAX_BALANCE_EXCEEDED`.
  3. **Sinh mã tham chiếu duy nhất**: `txnRef = "TOPUP_" + userId + "_" + System.currentTimeMillis()`.
  4. **Quy đổi tiền VNPay**: `vnpAmount = amount * 100` (VNPay nhân 100 để khử số thập phân).
  5. **Thu thập dữ liệu môi trường**: Lấy IP Client từ request header `X-Forwarded-For` qua `VNPayUtil.getClientIpAddress(httpRequest)`. Format thời gian tạo `yyyyMMddHHmmss` theo múi giờ `Asia/Ho_Chi_Minh`.
  6. **Đóng gói tham số VNPay**: Thiết lập bảng tham số bao gồm `vnp_Version`, `vnp_Command`, `vnp_TmnCode`, `vnp_Amount`, `vnp_TxnRef`, `vnp_OrderInfo`, `vnp_ReturnUrl`, `vnp_ExpireDate`,...
  7. **Ký số bảo mật**: Sắp xếp bảng tham số theo alphabet, băm HMAC-SHA512 với `vnp_HashSecret` để tạo chuỗi `vnp_SecureHash`.
  8. **Khởi tạo giao dịch**: Tạo bản ghi `PaymentTransaction` với trạng thái `PENDING`, `purpose = WALLET_TOPUP`, thời gian hết hạn sau 15 phút. Lưu vào `paymentTransactionRepository`.
  9. **Trả về URL**: Ghép chuỗi URL VNPay kèm checksum và trả về DTO `PaymentCreationResponse`.

#### 5. Webhook Service: `VNPayService.java` -> `processIpn(queryParams)`
- **Vị trí**: [VNPayService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/VNPayService.java#L190-L290)
- **Logic thực thi**:
  1. **Xác thực chữ ký**: Tính lại mã băm HMAC-SHA512 của các params nhận được, so sánh với `vnp_SecureHash`. Nếu không khớp -> trả `RspCode = 97` (Invalid Checksum).
  2. **Tìm giao dịch**: Tìm `PaymentTransaction` theo `vnp_TxnRef`. Nếu không thấy -> trả `RspCode = 01` (Order not found).
  3. **Kiểm tra Idempotency**: Nếu giao dịch trong DB đã ở trạng thái `SUCCESS` hoặc `FAILED` -> trả `RspCode = 02` (Order already confirmed), không thực hiện cộng tiền lại.
  4. **Kiểm tra kết quả VNPay**: Kiểm tra `vnp_ResponseCode == "00"` và `vnp_TransactionStatus == "00"`.
  5. **Cộng tiền vào ví (Chống Race Condition)**:
     - Nếu thành công và `purpose == PaymentPurpose.WALLET_TOPUP`:
       - Gọi `walletRepository.findByUserIdForUpdate(userId)` để thực hiện **Pessimistic Write Lock (`SELECT ... FOR UPDATE`)**.
       - Tăng số dư ví: `wallet.setAvailable(current + addAmount)`.
       - Lưu thực thể `Wallet`.
       - Cập nhật `transaction.setStatus(PaymentStatus.SUCCESS)`.
       - Gọi `ledgerService.recordWalletTopup(userId, addAmount, txnRef)`.
       - Trả về `RspCode = 00` (Confirm Success).
     - Nếu thất bại: cập nhật `transaction.setStatus(PaymentStatus.FAILED)`.

#### 6. Sổ cái: `LedgerService.java` -> `recordWalletTopup(userId, amount, txnRef)`
- **Vị trí**: [LedgerService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/LedgerService.java#L20-L59)
- **Logic thực thi**:
  1. Kiểm tra Idempotency Key: `idemKey = "TOPUP_" + txnRef`. Nếu `existsByIdempotencyKey(idemKey)` trả về bản ghi cũ ngay.
  2. Tạo mã chứng từ kế toán: `txnCode = "LTX-TOPUP-" + UUID`.
  3. Tạo giao dịch sổ cái `LedgerTransaction` loại `WALLET_TOPUP`.
  4. **Bút toán Nợ (DEBIT)**: Tài khoản `VNPAY_CLEARING` (Tài sản thanh toán trung gian), số tiền `amount`.
  5. **Bút toán Có (CREDIT)**: Tài khoản `USER_WALLET_{userId}` (Nghĩa vụ nợ với người dùng), số tiền `amount`.
  6. Lưu vào cơ sở dữ liệu.

---

## 3. Use Case 35: Thanh Toán & Ký Quỹ Đơn Hàng (Order Escrow Payment)

### 3.1. Mục Tiêu & Tiền Điều Kiện
- **Mục tiêu**: Người gửi thanh toán cước phí và giá trị bảo chứng để hệ thống khóa tiền ký quỹ (`Escrow`). Sau khi ký quỹ thành công, đơn hàng đổi trạng thái sang `ESCROWED` và xuất hiện công khai trên sàn nhận đơn để bưu tá nhận giao.
- **Tiền điều kiện**:
  - Đơn hàng tồn tại và đang ở trạng thái `PENDING` hoặc `PENDING_ESCROW`.
  - Người gọi bắt buộc phải là người tạo đơn: `order.senderId == userId`.
  - Đơn chưa từng được ký quỹ: `!escrowRepository.existsByOrderId(orderId)`.
  - Nếu trả bằng ví: `wallet.available >= orderAmount`.

---

### 3.2. Đặc Tả API Tương Ứng

#### API: Thanh toán ký quỹ đơn hàng (API-087)
- **Endpoint**: `POST /api/v1/orders/{id}/payment`
- **Phân quyền**: Bắt buộc là Người gửi (`SENDER`) sở hữu đơn hàng.
- **Request Body (`OrderPaymentRequest`)**:
  ```json
  {
    "paymentMethod": "WALLET", // hoặc "VNPAY"
    "bankCode": "",
    "language": "vn"
  }
  ```
- **Response Khi Thanh Toán Bằng Ví (`WALLET`)**: HTTP 200 OK
  ```json
  {
    "code": "200",
    "message": "Xử lý thanh toán đơn hàng thành công",
    "data": {
      "orderId": 12,
      "orderCode": "ORD-20261026-0001",
      "paymentMethod": "WALLET",
      "amount": 150000,
      "status": "SUCCESS",
      "message": "Ký quỹ đơn hàng bằng ví thành công"
    }
  }
  ```
- **Response Khi Thanh Toán Qua VNPay (`VNPAY`)**: HTTP 200 OK
  ```json
  {
    "code": "200",
    "message": "Xử lý thanh toán đơn hàng thành công",
    "data": {
      "orderId": 12,
      "orderCode": "ORD-20261026-0001",
      "paymentMethod": "VNPAY",
      "amount": 150000,
      "status": "PENDING_PAYMENT",
      "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
      "txnRef": "ORD_ESCROW_12_1729987200000",
      "message": "Vui lòng chuyển hướng sang cổng VNPay để thanh toán ký quỹ"
    }
  }
  ```
- **Mã lỗi có thể trả về**:
  - `ORDER_NOT_FOUND` (404): Không tìm thấy đơn hàng theo `id`.
  - `ORDER_PAYMENT_FORBIDDEN` (403): Người dùng hiện tại không phải `senderId` của đơn hàng.
  - `ORDER_ALREADY_ESCROWED` (400): Đơn hàng này đã được thanh toán ký quỹ trước đó.
  - `ORDER_PAYMENT_NOT_ALLOWED` (400): Trạng thái đơn hiện tại không cho phép thanh toán.
  - `WALLET_INSUFFICIENT_BALANCE` (400): Số dư ví khả dụng không đủ để thanh toán cước ký quỹ.

---

### 3.3. Chi Tiết Từng Hàm & Thuật Toán Xử Lý (Logic Walkthrough)

#### 1. Frontend UI: `OrderPaymentPage.tsx` -> `handleSubmitPayment()`
- **Vị trí**: [OrderPaymentPage.tsx](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/client/src/pages/OrderPaymentPage.tsx)
- **Logic thực thi**:
  1. Lấy phương thức người dùng chọn (`WALLET` hoặc `VNPAY`).
  2. Bật trạng thái loading `isSubmitting = true`.
  3. Gọi `orderApi.payOrder(orderId, { paymentMethod, bankCode, language })`.
  4. Nếu kết quả trả về `status === 'SUCCESS'` (thanh toán ví):
     - Hiển thị Toast thông báo ký quỹ thành công.
     - Điều hướng người dùng về trang chi tiết đơn hàng `/orders/{id}`.
  5. Nếu kết quả trả về `paymentUrl` (thanh toán VNPay):
     - Chuyển hướng trình duyệt `window.location.href = res.data.paymentUrl` sang VNPay.

#### 2. Controller: `OrderController.java` -> `payOrder(id, jwt, request, httpRequest)`
- **Vị trí**: [OrderController.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/order/controller/OrderController.java#L127-L137)
- **Logic thực thi**:
  1. Trích xuất `userId` từ token JWT.
  2. Chuyển tiếp tới `orderPaymentService.payOrder(userId, id, request, httpRequest)`.
  3. Bọc kết quả vào `ApiResponse.success` và trả về Client.

#### 3. Core Service: `OrderPaymentService.java` -> `payOrder(userId, orderId, request, httpRequest)`
- **Vị trí**: [OrderPaymentService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/OrderPaymentService.java#L51-L206)
- **Logic thực thi chi tiết**:
  1. **Tìm và kiểm tra đơn hàng**:
     - `orderRepository.findById(orderId)` -> ném `ORDER_NOT_FOUND` nếu null.
  2. **Kiểm tra quyền sở hữu**:
     - `if (!order.getSenderId().equals(userId))` -> ném `ORDER_PAYMENT_FORBIDDEN`.
  3. **Kiểm tra tính hợp lệ của trạng thái**:
     - `if (escrowRepository.existsByOrderId(orderId) || order.getStatus() == OrderStatus.ESCROWED)` -> ném `ORDER_ALREADY_ESCROWED`.
     - `if (order.getStatus() != PENDING && order.getStatus() != PENDING_ESCROW)` -> ném `ORDER_PAYMENT_NOT_ALLOWED`.
  4. **Tính số tiền ký quỹ**: Lấy giá trị khai báo `order.getDeclaredValue()` (tối thiểu 150.000đ).
  5. **Nhánh 1: Ký quỹ bằng Ví (`WALLET`)**:
     - Khóa ví người gửi bằng Pessimistic Write Lock: `walletRepository.findByUserIdForUpdate(userId)`.
     - Kiểm tra số dư: `if (wallet.getAvailable() < orderAmount)` -> ném `WALLET_INSUFFICIENT_BALANCE`.
     - Dịch chuyển số dư nội bộ: `available = available - orderAmount`, `escrowHeld = escrowHeld + orderAmount`. Lưu ví.
     - Tạo bản ghi `Escrow`: `orderId = orderId`, `senderId = userId`, `amount = orderAmount`, `status = HELD`, `paymentMethod = WALLET`, `frozen = false`.
     - Đổi trạng thái đơn hàng: `order.setStatus(OrderStatus.ESCROWED)`. Lưu đơn.
     - Ghi sổ cái kép: Gọi `ledgerService.recordOrderEscrowFromWallet(...)`.
     - Trả về `OrderPaymentResponse` trạng thái `SUCCESS`.
  6. **Nhánh 2: Ký quỹ qua cổng VNPay (`VNPAY`)**:
     - Sinh mã `txnRef = "ORD_ESCROW_" + orderId + "_" + System.currentTimeMillis()`.
     - Đóng gói params VNPay (`vnp_Amount = orderAmount * 100`, `vnp_TxnRef`, `vnp_OrderInfo`,...).
     - Băm SHA512 tạo `paymentUrl`.
     - Lưu bản ghi `PaymentTransaction`: `status = PENDING`, `purpose = ORDER_ESCROW`, `orderId = orderId`.
     - Trả về `OrderPaymentResponse` kèm `paymentUrl` để Frontend chuyển hướng.

#### 4. Webhook IPN xử lý nhánh VNPay: `VNPayService.java` -> `processIpn(...)`
- **Vị trí**: [VNPayService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/VNPayService.java#L268-L285)
- **Logic khi thanh toán VNPay thành công (`purpose == ORDER_ESCROW`)**:
  1. Kiểm tra không tạo trùng lặp: `!escrowRepository.existsByOrderId(orderId)`.
  2. Khởi tạo thực thể `Escrow` với trạng thái `HELD`, `paymentMethod = VNPAY`.
  3. Đổi trạng thái đơn hàng sang `ESCROWED`.
  4. Cập nhật `PaymentTransaction.status = SUCCESS`.
  5. Ghi bút toán sổ cái kép: `ledgerService.recordOrderEscrowFromVNPay(...)`.

#### 5. Sổ cái: `LedgerService.java`
- **Vị trí**: [LedgerService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/LedgerService.java#L62-L138)
- **Hàm `recordOrderEscrowFromWallet`**:
  - Idempotency Key: `ESCROW_HOLD_WALLET_{orderId}`.
  - Nợ (DEBIT): `USER_WALLET_{senderId}` (giảm số dư khả dụng ví người gửi).
  - Có (CREDIT): `ESCROW_ORDER_{orderId}` (tăng quỹ giam giữ ký quỹ).
- **Hàm `recordOrderEscrowFromVNPay`**:
  - Idempotency Key: `ESCROW_VNPAY_{txnRef}`.
  - Nợ (DEBIT): `VNPAY_CLEARING` (tài sản trung gian cổng VNPay).
  - Có (CREDIT): `ESCROW_ORDER_{orderId}` (tăng quỹ giam giữ ký quỹ).

---

## 4. Use Case 36: Tra Cứu & Giải Ngân Ký Quỹ Cho Người Giao (Courier Escrow Release)

### 4.1. Mục Tiêu & Tiền Điều Kiện
- **Mục tiêu**: Sau khi đơn hàng hoàn thành giao nhận, khoản tiền ký quỹ giam giữ trong quỹ `ESCROW_ORDER_{orderId}` được giải ngân chuyển sang ví khả dụng của Bưu tá (`COURIER`). Đồng thời cho phép Người gửi, Bưu tá và Quản trị viên tra cứu chi tiết bảo chứng tài chính.
- **Tiền điều kiện**:
  - Đơn hàng tồn tại và đã gán bưu tá: `order.courierId != null`.
  - Bản ghi ký quỹ tồn tại, trạng thái đang là `HELD`.
  - Ký quỹ không bị đóng băng tranh chấp: `escrow.frozen == false`.
  - Chưa từng được giải ngân: `escrow.status != RELEASED`.
  - Khi giải ngân thủ công qua API-093: Bắt buộc người thực hiện có quyền `ADMIN`.

---

### 4.2. Đặc Tả API Tương Ứng

#### API 1: Xem chi tiết ký quỹ đơn hàng (API-092)
- **Endpoint**: `GET /api/v1/orders/{id}/escrow`
- **Phân quyền**: Chỉ Người gửi (`senderId`), Người giao (`courierId`) của đơn hoặc Quản trị viên (`ADMIN`).
- **Response (`EscrowResponse`)**: HTTP 200 OK
  ```json
  {
    "code": "200",
    "message": "Lấy thông tin ký quỹ đơn hàng thành công",
    "data": {
      "id": 5,
      "orderId": 12,
      "orderCode": "ORD-20261026-0001",
      "senderId": 1,
      "courierId": 9,
      "amount": 150000,
      "status": "HELD", // hoặc "RELEASED", "REFUNDED"
      "paymentMethod": "WALLET", // hoặc "VNPAY"
      "releaseAt": "2026-10-28T01:00:00Z",
      "frozen": false,
      "createdAt": "2026-10-27T01:00:00Z",
      "updatedAt": "2026-10-27T01:00:00Z"
    }
  }
  ```
- **Mã lỗi**:
  - `ORDER_NOT_FOUND` (404): Không tìm thấy đơn hàng.
  - `ESCROW_NOT_FOUND` (404): Đơn hàng chưa phát sinh ký quỹ.
  - `ESCROW_ACCESS_DENIED` (403): Người dùng không có quyền xem thông tin ký quỹ đơn này.

#### API 2: Admin giải ngân tiền ký quỹ thủ công (API-093)
- **Endpoint**: `POST /api/v1/admin/escrow/{orderId}/release`
- **Phân quyền**: Bắt buộc tài khoản có quyền `ROLE_ADMIN` hoặc claim JWT chứa role `ADMIN`.
- **Response (`EscrowReleaseResponse`)**: HTTP 200 OK
  ```json
  {
    "code": "200",
    "message": "Giải ngân tiền ký quỹ cho người giao hàng thành công",
    "data": {
      "orderId": 12,
      "orderCode": "ORD-20261026-0001",
      "courierId": 9,
      "amountReleased": 150000,
      "status": "RELEASED",
      "message": "Giải ngân tiền ký quỹ cho người giao hàng thành công",
      "releasedAt": "2026-10-27T02:30:00Z"
    }
  }
  ```
- **Mã lỗi**:
  - `ORDER_COURIER_NOT_ASSIGNED` (400): Đơn hàng chưa có bưu tá nhận, không thể giải ngân.
  - `ESCROW_ALREADY_RELEASED` (400): Khoản ký quỹ đã được giải ngân trước đó.
  - `ESCROW_STATUS_INVALID` (400): Trạng thái ký quỹ không phải là `HELD`.
  - `ESCROW_FROZEN` (400): Khoản ký quỹ đang bị phong tỏa do đơn hàng có khiếu nại tranh chấp.

---

### 4.3. Chi Tiết Từng Hàm & Thuật Toán Xử Lý (Logic Walkthrough)

#### 1. Frontend UI: `OrderDetailPage.tsx`
- **Vị trí**: [OrderDetailPage.tsx](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/client/src/pages/OrderDetailPage.tsx)
- **Hàm `fetchEscrow(orderId)`**:
  - Gọi `orderApi.getEscrow(orderId)`.
  - Hiển thị Card thông tin ký quỹ: Số tiền giữ, phương thức thanh toán, trạng thái ký quỹ (`HELD` hiển thị badge vàng, `RELEASED` hiển thị badge xanh lá). Nếu `frozen === true`, hiển thị cảnh báo đỏ "Ký quỹ đang bị phong tỏa".
- **Hàm `handleReleaseEscrow()`**:
  - Nút bấm chỉ hiển thị khi `isAdmin === true` và `escrow.status === 'HELD'`.
  - Bật modal xác nhận giải ngân.
  - Khi xác nhận: gọi `orderApi.releaseEscrow(orderId)`.
  - Hiển thị Toast thành công và tự động tải lại chi tiết đơn + escrow.

#### 2. Controller: `EscrowController.java`
- **Vị trí**: [EscrowController.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/controller/EscrowController.java)
- **Hàm `getOrderEscrow(id, jwt, authentication)`**:
  - Trích xuất `currentUserId` từ JWT.
  - Gọi `checkIsAdmin(jwt, authentication)` kiểm tra cờ Admin.
  - Chuyển tiếp tới `orderPaymentService.getEscrowByOrderId(id, currentUserId, isAdmin)`.
- **Hàm `releaseEscrow(orderId, jwt, authentication)`**:
  - Kiểm tra `checkIsAdmin`: nếu không phải Admin, Spring Security hoặc code chặn ngay (403).
  - Gọi `orderPaymentService.releaseEscrow(orderId, operatorUserId, isAdmin)`.

#### 3. Core Service: `OrderPaymentService.java`
- **Vị trí**: [OrderPaymentService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/OrderPaymentService.java#L213-L358)
- **Hàm `getEscrowByOrderId(orderId, currentUserId, isAdmin)`**:
  - Tìm `Order` theo `orderId` -> throw `ORDER_NOT_FOUND`.
  - Tìm `Escrow` theo `orderId` -> throw `ESCROW_NOT_FOUND`.
  - Kiểm tra: `isSender = (order.getSenderId() == currentUserId)`, `isCourier = (order.getCourierId() == currentUserId)`.
  - Nếu `!isSender && !isCourier && !isAdmin` -> ném `ESCROW_ACCESS_DENIED`.
  - Trả về `EscrowResponse`.
- **Hàm `releaseEscrow(orderId, operatorUserId, isAdmin)` (Quy trình 8 bước)**:
  1. Kiểm tra đơn hàng tồn tại trong DB.
  2. Kiểm tra `order.getCourierId() != null` -> nếu null ném `ORDER_COURIER_NOT_ASSIGNED`.
  3. Lấy `Escrow` theo `orderId` -> nếu null ném `ESCROW_NOT_FOUND`.
  4. Kiểm tra trạng thái: nếu `status == RELEASED` -> ném `ESCROW_ALREADY_RELEASED`; nếu `status != HELD` -> ném `ESCROW_STATUS_INVALID`.
  5. Kiểm tra cờ phong tỏa: `if (escrow.isFrozen())` -> ném `ESCROW_FROZEN`.
  6. **Giải phóng tiền ví người gửi (nếu thanh toán qua ví)**:
     - Nếu `escrow.getPaymentMethod() == WALLET`: Khóa ví người gửi `findByUserIdForUpdate(senderId)`, giảm số dư giam giữ `escrowHeld = Math.max(0, escrowHeld - amount)`. Lưu ví sender.
  7. **Cộng tiền khả dụng cho bưu tá (Pessimistic Lock)**:
     - Khóa ví bưu tá `walletRepository.findByUserIdForUpdate(courierId)`.
     - Tăng số dư khả dụng: `wallet.setAvailable(available + amount)`. Lưu ví courier.
  8. **Cập nhật Escrow & Ghi Sổ cái kép**:
     - Cập nhật `escrow.setStatus(EscrowStatus.RELEASED)`, `releaseAt = Instant.now()`. Lưu Escrow.
     - Gọi `ledgerService.recordEscrowRelease(...)`.
     - Trả về `EscrowReleaseResponse`.

#### 4. Sổ cái: `LedgerService.java` -> `recordEscrowRelease(...)`
- **Vị trí**: [LedgerService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/LedgerService.java#L140-L195)
- **Logic thực thi**:
  - Idempotency Key: `ESCROW_RELEASE_{orderId}_{escrowRef}`.
  - **Bút toán Nợ (DEBIT)**: Tài khoản `ESCROW_ORDER_{orderId}` (giảm nghĩa vụ giam giữ quỹ ký quỹ của đơn hàng), số tiền `amount`.
  - **Bút toán Có (CREDIT)**: Tài khoản `USER_WALLET_{courierId}` (tăng số dư khả dụng thuộc sở hữu của bưu tá), số tiền `amount`.
  - Lưu vào `ledgerTransactionRepository`.

---

## 5. Use Case 40: Yêu Cầu Rút Tiền Từ Ví Về Ngân Hàng (Wallet Withdrawal)

### 5.1. Mục Tiêu & Tiền Điều Kiện
- **Mục tiêu**: Người dùng (cả người gửi và bưu tá) lập lệnh rút số dư khả dụng (`available`) về tài khoản ngân hàng thụ hưởng cá nhân; theo dõi lịch sử rút tiền và chủ động hủy lệnh rút khi hệ thống chưa phê duyệt chi trả.
- **Tiền điều kiện**:
  - Đã đăng nhập và sở hữu ví hợp lệ trong hệ thống.
  - Số tiền rút tối thiểu: **50.000 VNĐ** (`MIN_WITHDRAWAL`).
  - Số dư khả dụng `wallet.available >= amount + fee`.
  - Thông tin ngân hàng nhận tiền đầy đủ: Tên ngân hàng, Số tài khoản, Tên chủ thẻ thụ hưởng.
  - Khi hủy lệnh: Bản ghi rút tiền phải thuộc chính chủ (`wdr.userId == userId`) và trạng thái đang là `PENDING_REVIEW`.

---

### 5.2. Đặc Tả API Tương Ứng

#### API 1: Tạo yêu cầu rút tiền (API-097)
- **Endpoint**: `POST /api/v1/wallet/withdrawals`
- **Phân quyền**: Người dùng đã đăng nhập (Bearer JWT).
- **Request Body (`WithdrawalCreationRequest`)**:
  ```json
  {
    "amount": 200000,
    "bankName": "Vietcombank",
    "accountNumber": "1012345678",
    "accountHolderName": "NGUYEN VAN A"
  }
  ```
- **Response (`WithdrawalResponse`)**: HTTP 201 CREATED
  ```json
  {
    "code": "201",
    "message": "Tạo yêu cầu rút tiền thành công",
    "data": {
      "id": 1,
      "requestCode": "WDR-20261027-9A15CEFA",
      "userId": 9,
      "amount": 200000,
      "feeAmount": 0,
      "netAmount": 200000,
      "bankName": "Vietcombank",
      "accountNumber": "1012345678",
      "accountHolderName": "NGUYEN VAN A",
      "status": "PENDING_REVIEW",
      "createdAt": "2026-10-27T03:00:00Z"
    }
  }
  ```
- **Mã lỗi**:
  - `WITHDRAWAL_AMOUNT_INVALID` (400): Số tiền rút < 50.000đ.
  - `WALLET_INSUFFICIENT_BALANCE` (400): Số dư ví khả dụng không đủ để rút.
  - `WALLET_NOT_FOUND` (404): Người dùng chưa có ví.

#### API 2: Lấy danh sách yêu cầu rút tiền của tôi (API-098)
- **Endpoint**: `GET /api/v1/wallet/withdrawals?page=0&size=10`
- **Phân quyền**: Người dùng đã đăng nhập.
- **Response**: HTTP 200 OK trả về `PageResponse<WithdrawalResponse>`.

#### API 3: Hủy yêu cầu rút tiền đang chờ duyệt (API-099)
- **Endpoint**: `POST /api/v1/wallet/withdrawals/{id}/cancel`
- **Phân quyền**: Bắt buộc chính chủ tạo yêu cầu rút.
- **Response (`WithdrawalResponse`)**: HTTP 200 OK với `status = "CANCELLED"`.
- **Mã lỗi**:
  - `WITHDRAWAL_REQUEST_NOT_FOUND` (404): Không tìm thấy yêu cầu rút.
  - `WITHDRAWAL_ACCESS_DENIED` (403): Yêu cầu rút không thuộc về tài khoản này.
  - `WITHDRAWAL_CANNOT_CANCEL` (400): Yêu cầu không còn ở trạng thái `PENDING_REVIEW` (đã duyệt hoặc đã từ chối).

---

### 5.3. Chi Tiết Từng Hàm & Thuật Toán Xử Lý (Logic Walkthrough)

#### 1. Frontend UI: `WalletPage.tsx`
- **Vị trí**: [WalletPage.tsx](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/client/src/pages/WalletPage.tsx)
- **Hàm `handleCreateWithdrawal()`**:
  1. Validate form: Kiểm tra `amount >= 50.000đ`, `amount <= wallet.available`, các ô ngân hàng không để trống.
  2. Bật loading, gọi `walletApi.createWithdrawal(requestData)`.
  3. Khi thành công: Đóng modal rút tiền, hiển thị Toast thông báo tạo yêu cầu rút thành công kèm mã tham chiếu `requestCode`.
  4. Làm mới số dư ví (đã bị trừ `available`, tăng `pendingWithdraw`) và làm mới bảng lịch sử rút tiền.
- **Hàm `handleCancelWithdrawal(id)`**:
  1. Mở modal xác nhận "Bạn có chắc chắn muốn hủy yêu cầu rút tiền này?".
  2. Khi người dùng xác nhận: gọi `walletApi.cancelWithdrawal(id)`.
  3. Hiển thị Toast thông báo hủy thành công, tiền đã được hoàn trả về số dư khả dụng.
  4. Reload dữ liệu ví và bảng lịch sử rút.

#### 2. Controller: `WithdrawalController.java`
- **Vị trí**: [WithdrawalController.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/controller/WithdrawalController.java)
- **Hàm `createWithdrawal(jwt, request)`**:
  - Trích xuất `userId` từ JWT.
  - Gọi `withdrawalService.createWithdrawalRequest(userId, request)`.
  - Trả về HTTP 201 Created cùng `ApiResponse.created(...)`.
- **Hàm `getMyWithdrawals(jwt, page, size)`**:
  - Trích xuất `userId` từ JWT.
  - Gọi `withdrawalService.getMyWithdrawals(userId, page, size)`. Trả về `PageResponse`.
- **Hàm `cancelWithdrawal(jwt, id)`**:
  - Trích xuất `userId` từ JWT.
  - Gọi `withdrawalService.cancelWithdrawalRequest(userId, id)`. Trả về kết quả sau hủy.

#### 3. Core Service: `WithdrawalService.java`
- **Vị trí**: [WithdrawalService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/WithdrawalService.java)
- **Hàm `createWithdrawalRequest(userId, request)`**:
  1. **Kiểm tra hạn mức**: `if (amount < 50_000)` -> ném `WITHDRAWAL_AMOUNT_INVALID`.
  2. **Khóa bi quan ngăn ngừa rút trùng lặp (Race Condition)**:
     - Gọi `walletRepository.findByUserIdForUpdate(userId)` phát sinh lệnh SQL `SELECT ... FOR UPDATE`.
  3. **Kiểm tra khả năng chi trả**:
     - `if (wallet.getAvailable() < amount + WITHDRAWAL_FEE)` -> ném `WALLET_INSUFFICIENT_BALANCE`.
  4. **Dịch chuyển số dư nguyên tử (ACID Transaction)**:
     - Giảm số dư khả dụng: `wallet.setAvailable(available - (amount + fee))`.
     - Tăng số dư tạm giữ rút: `wallet.setPendingWithdraw(pendingWithdraw + amount)`.
     - Lưu thực thể `Wallet`.
  5. **Sinh mã yêu cầu & Lưu bản ghi**:
     - Sinh mã: `requestCode = "WDR-" + LocalDate.now().format("yyyyMMdd") + "-" + UUID_8_CHAR`.
     - Tạo `WithdrawalRequest`: `status = PENDING_REVIEW`, `bankName`, `accountNumber`, `accountHolderName` (chuẩn hóa in hoa).
     - Lưu vào `withdrawalRequestRepository`.
  6. **Ghi sổ cái kép**:
     - Gọi `ledgerService.recordWithdrawalRequest(userId, amount, requestCode)`.
  7. **Trả về DTO**: Map entity sang `WithdrawalResponse`.
- **Hàm `cancelWithdrawalRequest(userId, withdrawalId)`**:
  1. Tìm `WithdrawalRequest` theo ID -> ném `WITHDRAWAL_REQUEST_NOT_FOUND`.
  2. Kiểm tra chính chủ: `if (!wdr.getUserId().equals(userId))` -> ném `WITHDRAWAL_ACCESS_DENIED`.
  3. Kiểm tra trạng thái: `if (wdr.getStatus() != WithdrawalStatus.PENDING_REVIEW)` -> ném `WITHDRAWAL_CANNOT_CANCEL`.
  4. **Khóa ví và hoàn trả số dư nguyên tử**:
     - Gọi `walletRepository.findByUserIdForUpdate(userId)`.
     - Giảm số dư tạm giữ: `wallet.setPendingWithdraw(Math.max(0, pendingWithdraw - amount))`.
     - Tăng lại số dư khả dụng: `wallet.setAvailable(available + amount)`.
     - Lưu ví.
  5. **Cập nhật trạng thái**: Gán `wdr.setStatus(WithdrawalStatus.CANCELLED)`. Lưu DB.
  6. **Ghi sổ cái hoàn tác**: Gọi `ledgerService.recordWithdrawalCancellation(userId, amount, wdr.getRequestCode())`.
  7. Trả về `WithdrawalResponse`.

#### 4. Sổ cái: `LedgerService.java`
- **Vị trí**: [LedgerService.java](file:///c:/Users/Le%20Nhut%20Huy/Desktop/Projects/SE1910/SBA301/project/SBA391_Group6_Project/server/src/main/java/com/fptpost/modules/payment/service/LedgerService.java#L197-L270)
- **Hàm `recordWithdrawalRequest`**:
  - Idempotency Key: `WITHDRAWAL_REQ_{requestCode}`.
  - Nợ (DEBIT): `USER_WALLET_{userId}` (trừ số dư khả dụng của người dùng).
  - Có (CREDIT): `CLEARING_WITHDRAWAL` (tài khoản tạm giữ chờ đối soát chi trả).
- **Hàm `recordWithdrawalCancellation`**:
  - Idempotency Key: `CANCEL_WDR_{requestCode}`.
  - Nợ (DEBIT): `CLEARING_WITHDRAWAL` (giảm tài khoản tạm giữ).
  - Có (CREDIT): `USER_WALLET_{userId}` (hoàn tiền lại vào ví khả dụng người dùng).

---

## 6. Ma Trận Bút Toán Sổ Cái Kép Toàn Phân Hệ (Double-Entry Matrix)

Mọi biến động số dư trong cả 4 Use Case bắt buộc phải ghi nhận vào Sổ cái kế toán theo nguyên tắc bất biến: **`Tổng Nợ (Debit) == Tổng Có (Credit)`**:

| Use Case | Mã Bút Toán (`type`) | Tài Khoản Ghi Nợ (DEBIT) | Tài Khoản Ghi Có (CREDIT) | Idempotency Key | Bản Chất Kế Toán |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **UC-34: Nạp tiền ví qua VNPay** | `WALLET_TOPUP` | `VNPAY_CLEARING` | `USER_WALLET_{userId}` | `TOPUP_{txnRef}` | Tăng tài sản thanh toán trung gian VNPay, tăng nghĩa vụ nợ (số dư ví) của người dùng |
| **UC-35: Ký quỹ qua Ví** | `ORDER_ESCROW_HOLD` | `USER_WALLET_{senderId}` | `ESCROW_ORDER_{orderId}` | `ESCROW_HOLD_WALLET_{orderId}` | Giảm số dư khả dụng ví người gửi, tăng quỹ giam giữ ký quỹ của đơn hàng |
| **UC-35: Ký quỹ qua VNPay** | `ORDER_ESCROW_HOLD` | `VNPAY_CLEARING` | `ESCROW_ORDER_{orderId}` | `ESCROW_VNPAY_{txnRef}` | Tăng tài sản trung gian VNPay, tăng quỹ giam giữ ký quỹ của đơn hàng |
| **UC-36: Giải ngân ký quỹ** | `ESCROW_RELEASE` | `ESCROW_ORDER_{orderId}` | `USER_WALLET_{courierId}` | `ESCROW_RELEASE_{orderId}_{ref}` | Giải phóng quỹ giam giữ ký quỹ, cộng vào số dư khả dụng thuộc sở hữu của bưu tá |
| **UC-40: Tạo yêu cầu rút tiền**| `WITHDRAWAL_REQUEST` | `USER_WALLET_{userId}` | `CLEARING_WITHDRAWAL` | `WITHDRAWAL_REQ_{requestCode}` | Trừ số dư khả dụng ví người dùng, đưa vào tài khoản tạm giữ chờ chi trả ngân hàng |
| **UC-40: Hủy yêu cầu rút tiền**| `WITHDRAWAL_CANCEL` | `CLEARING_WITHDRAWAL` | `USER_WALLET_{userId}` | `CANCEL_WDR_{requestCode}` | Giảm tài khoản tạm giữ chi trả, hoàn lại vào số dư khả dụng ví người dùng |

---

## 7. Các Cơ Chế Bảo Vệ & An Toàn Dữ Liệu Đồng Thời (Concurrency Safeguards)

1. **Khóa Bi Quan (Pessimistic Write Locking - `SELECT ... FOR UPDATE`)**:
   - Tất cả các thao tác thay đổi số dư ví (`processIpn` nạp tiền, `payOrder` bằng ví, `releaseEscrow` giải ngân cho shipper, `createWithdrawalRequest` khóa tiền rút, `cancelWithdrawalRequest` hoàn tiền rút) đều sử dụng hàm `walletRepository.findByUserIdForUpdate(userId)`.
   - Cơ chế này kích hoạt khóa hàng ở mức database (Row-level lock), khiến các yêu cầu phát sinh đồng thời trên cùng một ví phải xếp hàng đợi, triệt tiêu 100% hiện tượng Race Condition hay "chi tiêu gấp đôi" (Double Spending).
2. **Khóa Chống Lặp Giao Dịch (Idempotency Key Verification)**:
   - Tất cả các giao dịch ghi nhận sổ cái đều có một khóa định danh duy nhất (`idempotencyKey`).
   - Trước khi ghi sổ, `LedgerService` luôn kiểm tra `ledgerTransactionRepository.existsByIdempotencyKey(idemKey)`. Nếu đã tồn tại, hệ thống lập tức bỏ qua việc cộng/trừ tiền và trả về bản ghi giao dịch đã lưu trước đó. Điều này bảo vệ an toàn tuyệt đối khi Webhook VNPay gọi lại nhiều lần hoặc người dùng ấn đúp nút submit.
3. **Kiểm Soát Quyền Truy Cập Nghiêm Ngặt (Granular RBAC & Data Ownership)**:
   - `OrderController.payOrder`: Chỉ cho phép `senderId` của đơn hàng thực hiện thanh toán.
   - `EscrowController.getOrderEscrow`: Chỉ `senderId`, `courierId` hoặc `ADMIN` mới có quyền xem thông tin ký quỹ.
   - `EscrowController.releaseEscrow`: Bắt buộc người thực hiện phải có vai trò `ADMIN`.
   - `WithdrawalController`: Mọi thao tác tạo, xem danh sách và hủy lệnh rút tiền đều đối chiếu với `userId` được giải mã từ JWT của phiên đăng nhập hiện tại, người dùng không thể can thiệp hoặc xem yêu cầu rút của người khác.

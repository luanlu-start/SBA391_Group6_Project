# Hướng dẫn gỡ conflict PR #104 (UC-33 Bàn giao giữa các chặng)

Người nhận: Võ Quốc An. Người viết: Trần Công Thuận, ngày 11/11/2026.

## 1. Tình trạng

- PR #104 (`feature/UC-33-Bàn-giao-giữa-các-chặng`) đã đóng, chưa vào `develop`.
- Nhánh của An tách từ `develop` tại `389f2d3` (trước PR #103 của Long: UC-25, UC-29, UC-30).
- Merge thử vào `develop` (`275f3f6`) xung đột 5 file. Gỡ xong 5 file này thì mã chính biên dịch được,
  nhưng còn 4 việc nữa mới chạy được (mục 4). Vì vậy PR chưa được gộp.

Nguyên nhân gốc: #104 chuyển phần tiếp sức của UC-32 từ module `order` sang module `relay` mới và xóa các lớp cũ,
trong khi #103 trên `develop` vẫn dùng các lớp cũ đó.

| Lớp cũ trên `develop` (bị #104 xóa) | Thay bằng trong #104 |
|---|---|
| `order.model.OrderLeg` | `relay.model.OrderLeg` |
| `order.repository.OrderLegRepository` | repository trong `relay.repository` |
| `order.service.RelayOrderService` | `relay.service.RelayService` |
| `order.dto.RelayPoint`, `RelayOrderRequest`, `RelayOrderResponse`, `OrderLegResponse`, `RelayPricingEstimate*` | DTO trong `relay.dto` |
| `RelayOrderServiceTest` | chưa có test thay thế |

## 2. Chuẩn bị

1. Chờ PR #105 được merge trước. PR đó sửa build `develop` (test gọi constructor cũ của `CourierEarningsService`)
   và khôi phục `OrderDetailPage.tsx`. Nếu chưa merge, `develop` không biên dịch được test.
2. Cập nhật nhánh:

```bash
git fetch origin
```

```bash
git checkout feature/UC-33-Bàn-giao-giữa-các-chặng
```

```bash
git merge origin/develop
```

3. Cài thư viện mới của #103 (`qrcode.react`), nếu không client trắng màn hình:

```bash
npm install --prefix client
```

## 3. Gỡ 5 file xung đột

### 3.1 `server/.../batch/service/BatchService.java` (dễ)

Giữ **cả hai** bộ lọc trong `getEligibleOrders`:

```java
.filter(o -> !RETURN_STATUSES.contains(o.getStatus()))
.filter(o -> !batchedOrderIds.contains(o.getId()))
```

### 3.2 `server/.../order/controller/OrderController.java`

- Giữ `private final PickupConfirmationService pickupConfirmationService;` (UC-25 của Long đang dùng).
- Bỏ `private final RelayOrderService relayOrderService;` và ba endpoint tiếp sức trong controller này
  (`POST /orders/relay/estimate`, `POST /orders/relay`, `GET /orders/{id}/legs`). `RelayController` của An đã khai báo
  đúng các đường dẫn đó; giữ ở cả hai nơi thì Spring báo trùng mapping và không khởi động.

### 3.3 `server/.../order/model/Order.java`

- Bỏ danh sách `legs` và hàm `addLeg` (đúng như bản của An, vì `OrderLeg` đã sang module `relay`).
- Với `isRelay`, giữ bản `@Formula` của `develop` và **xóa** khai báo `@Column(name = "is_relay")` của An.
  Hai khai báo cùng tồn tại sau khi merge sẽ lỗi trùng trường.

```java
@Builder.Default
@Formula("(case when exists (select 1 from order_legs relay_leg where relay_leg.order_id = id) then 1 else 0 end)")
private Boolean isRelay = false;
```

  Lưu ý: với `@Formula`, giá trị `isRelay(true)` đặt lúc tạo đơn không được lưu; cờ này đúng khi đơn có dòng trong
  `order_legs`. `RelayService` cần lưu các chặng trong cùng giao dịch tạo đơn.

### 3.4 `client/src/pages/CourierAssignedOrdersPage.tsx`

Giữ khối của `develop` (`ConfirmPickupPanel`, `DeliveryIncidentPanel`, nút "Giao thất bại / hoàn hàng", nút "Nhắn tin")
và thêm nút của An vào trước nút "Nhắn tin":

```tsx
{order.isRelay && (
  <Button
    variant="secondary"
    icon={<GitFork size={16} style={{ color: 'var(--primary, #f37021)' }} />}
    onClick={() => navigate(`/courier/relay?orderId=${order.id}`)}
  >
    Bàn giao tiếp sức
  </Button>
)}
```

### 3.5 `client/src/pages/OrderDetailPage.tsx`

Bốn khối xung đột đều có dạng "develop trống, nhánh An có nội dung". Lý do: #103 đã ghi đè file này bằng bản cũ,
làm mất mục thanh toán ký quỹ, hoàn tiền về nguồn VNPay, hộp xác nhận giải ngân và mời người giao.

Cách gỡ: lấy **toàn bộ bản của An**, rồi thêm lại hai phần của Long.

```bash
git checkout --theirs client/src/pages/OrderDetailPage.tsx
```

Thêm import:

```tsx
import DeliveryIncidentPanel from '../components/order/DeliveryIncidentPanel';
```

Thêm ngay sau dòng `<OrderModerationNotice ... />`:

```tsx
{currentUser && <DeliveryIncidentPanel orderId={order.id} />}
{currentUser && <Button variant="secondary" onClick={() => navigate(`/orders/${order.id}/return`)}>Giao thất bại / nhận lại hàng</Button>}
```

Khi merge `develop` sau PR #105, khối xung đột ở file này sẽ khác đi; nguyên tắc vẫn là giữ đủ: mục thanh toán ký quỹ,
hoàn tiền VNPay, mời người giao, hai phần của Long và mục "Hành trình giao tiếp sức" của An.

## 4. Việc còn lại sau khi gỡ xung đột

### 4.1 `OrderResponse` mất trường `isRelay`

Nhánh của An xóa `isRelay` khỏi `order/dto/OrderResponse.java`. Giao diện của An lại dựa vào `order.isRelay` để hiện
nhãn "Tiếp sức" và nút "Bàn giao tiếp sức", nên phải giữ trường này (và phần ánh xạ trong `OrderMapper`).

### 4.2 Ba file test của #103 không còn biên dịch

`mvn spring-boot:run` biên dịch cả test, nên test hỏng thì server cũng không chạy.

| File | Cần sửa |
|---|---|
| `order/CourierReadApiSchemaTest.java` | Đổi import sang `relay.model.OrderLeg`. Bỏ tham số `mock(RelayOrderService.class)` khi tạo `OrderController`. Phần tạo đơn tiếp sức đang gọi `relay.addLeg(OrderLeg.builder()...pickupAddress("A").deliveryAddress("B").status(OrderStatus...))`: đổi thành lưu chặng riêng với `order(relay)`, `startAddress`, `endAddress` và `LegStatus`. Hai chỗ `o.getIsRelay()` chạy lại được khi làm xong mục 4.1. |
| `order/controller/PickupConfirmationControllerTest.java` | Bỏ tham số `mock(RelayOrderService.class)` khi tạo `OrderController`. |
| `deliveryreturn/FailedDeliveryTransactionTest.java` | Đổi import `OrderLeg` sang module `relay`. |

`RelayOrderServiceTest` bị xóa theo lớp cũ. Nên có test tương ứng cho `RelayService` (ước tính cước và tạo đơn tiếp sức).

### 4.3 Lỗi typecheck ở client

```
src/pages/CourierMarketplacePage.tsx(394): Property 'label' is missing ... required in type 'SelectProps'
```

`Select` trên `develop` bắt buộc có `label`. Thêm `label="Sắp xếp"` (và ẩn nhãn nếu cần) cho ô chọn sắp xếp.

### 4.4 Bảng `order_legs` đổi cột (quan trọng nhất)

| Cột trên `develop` | Cột trong #104 |
|---|---|
| `pickup_address` (NOT NULL) | `start_address` (NOT NULL) |
| `pickup_latitude`, `pickup_longitude` | `start_latitude`, `start_longitude` |
| `delivery_address` (NOT NULL) | `end_address` (NOT NULL) |
| `delivery_latitude`, `delivery_longitude` | `end_latitude`, `end_longitude` |
| `leg_fee` | `fee` |
| (không có) | `courier_name`, `courier_phone`, `started_at`, `handed_over_at`, `completed_at`, `handover_note` |

Dự án dùng `ddl-auto=update`, Hibernate chỉ thêm cột chứ không đổi tên hay xóa. Trên database đã chạy UC-32:

- Bảng đã có dữ liệu: SQL Server từ chối thêm `start_address`, `end_address` vì là `NOT NULL` không có mặc định
  (đúng lỗi `is_relay` trước đây). Mọi thao tác đọc ghi chặng lỗi `Invalid column name`.
- Bảng trống: cột mới được thêm, nhưng cột cũ `pickup_address`, `delivery_address` vẫn `NOT NULL`,
  nên lệnh thêm chặng lỗi vì thiếu giá trị cho cột cũ.

Database tạo mới hoàn toàn thì không gặp lỗi. Máy của cả nhóm đều đã có bảng cũ, nên cần một trong hai cách:

**Cách A (ít rủi ro nhất):** giữ tên cột cũ trong `relay.model.OrderLeg` bằng `@Column(name = "pickup_address")`…,
chỉ đổi tên trường Java. Các cột thêm mới đều cho phép null nên Hibernate tự thêm được.

**Cách B:** giữ tên cột mới và thêm bước nâng cấp khi khởi động, theo mẫu
`order/config/OrderRelayColumn.java` (chạy bằng `ApplicationRunner`, chỉ trên SQL Server). Với từng cặp cột:

```sql
-- Chỉ có cột cũ: đổi tên
IF COL_LENGTH('dbo.order_legs', 'start_address') IS NULL AND COL_LENGTH('dbo.order_legs', 'pickup_address') IS NOT NULL
    EXEC sp_rename 'dbo.order_legs.pickup_address', 'start_address', 'COLUMN';

-- Có cả hai (Hibernate đã thêm cột mới vào bảng trống): bỏ cột cũ
IF COL_LENGTH('dbo.order_legs', 'start_address') IS NOT NULL AND COL_LENGTH('dbo.order_legs', 'pickup_address') IS NOT NULL
    ALTER TABLE dbo.order_legs DROP COLUMN pickup_address;
```

Lặp lại cho `pickup_latitude`, `pickup_longitude`, `delivery_address`, `delivery_latitude`, `delivery_longitude`, `leg_fee`.
Đoạn SQL trên là mẫu, chưa chạy thử; cần thử trên database có sẵn đơn tiếp sức trước khi mở PR.

Cột `status` của chặng đổi từ `OrderStatus` sang `LegStatus`. Dòng cũ đang lưu giá trị của `OrderStatus`
(ví dụ `FINDING_SHIPPER`); cần chuyển về giá trị `LegStatus` tương ứng trong cùng bước nâng cấp,
nếu không Hibernate lỗi khi đọc dòng cũ.

## 5. Kiểm tra trước khi mở lại PR

```bash
mvn -B -f server/pom.xml test -Daudit.outbox.enabled=false
```

```bash
npm run typecheck --prefix client
```

```bash
npm run lint --prefix client
```

```bash
npm run build:client
```

Ngoài bốn lệnh trên (giống CI), cần chạy thật trên database **đã có dữ liệu cũ**:

- [ ] Server khởi động, log không có `Error executing DDL` cho `order_legs`.
- [ ] Tạo đơn tiếp sức mới, xem được các chặng ở trang chi tiết đơn.
- [ ] Đơn tiếp sức tạo trước khi merge vẫn mở được.
- [ ] Người giao thấy nút "Bàn giao tiếp sức" và bàn giao được bằng QR.
- [ ] Luồng của Long vẫn chạy: xác nhận lấy hàng bằng QR (UC-25), báo sự cố, giao thất bại và hoàn hàng (UC-29, UC-30).
- [ ] Trang chi tiết đơn còn mục "Thanh toán & Ký quỹ", thanh toán qua Ví và VNPay được.

## 6. Cần thống nhất với Long

- `OrderController` không còn `RelayOrderService`: các test và mã của Long tạo `OrderController` bằng tay phải đổi theo.
- Ai giữ `order_legs` từ nay (module `relay`), và Long có đọc bảng này trong luồng hoàn hàng hay không.
- Biến môi trường `PICKUP_QR_SECRET` (UC-25) bắt buộc, tối thiểu 32 ký tự. Mã QR bàn giao của UC-33 nếu dùng khóa riêng
  thì cũng cần thêm vào `.env.example` và `compose.yaml`.

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, CheckCircle2 } from 'lucide-react';
import type { Order } from '../types/order';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Alert from '../components/ui/Alert';
import OrderForm from '../features/order/components/OrderForm';

export default function CreateOrderPage() {
  const navigate = useNavigate();
  const [createdOrder, setCreatedOrder] = useState<Order | null>(null);

  function handleSuccess(order: Order) {
    setCreatedOrder(order);
    // Tự động chuyển hướng đến trang chi tiết sau 1.5 giây
    setTimeout(() => {
      navigate(`/sender/orders/${order.id}`);
    }, 1500);
  }

  return (
    <div className="ui-stack">
      <PageHeader
        title="Tạo đơn hàng mới"
        eyebrow="Dịch vụ giao nhận FPTPost"
        description="Điền thông tin bên dưới để hệ thống tiếp nhận đơn và tự động điều phối tài xế phù hợp."
        actions={
          <Button
            variant="ghost"
            icon={<ArrowLeft size={16} />}
            onClick={() => navigate('/sender/orders')}
          >
            Danh sách đơn hàng
          </Button>
        }
      />

      {createdOrder && (
        <Alert tone="success" title="Tạo đơn hàng thành công!">
          <div className="ui-row" style={{ alignItems: 'center', gap: '8px' }}>
            <CheckCircle2 size={18} />
            <span>
              Mã vận đơn của bạn là: <strong>{createdOrder.orderCode}</strong>. Đang chuyển đến chi tiết đơn hàng...
            </span>
          </div>
        </Alert>
      )}

      <SectionCard
        title="Thông tin bưu gửi"
        description="Vui lòng kiểm tra kỹ thông tin người nhận và hàng hóa trước khi xác nhận tạo đơn."
      >
        <OrderForm
          onSuccess={handleSuccess}
          onCancel={() => navigate('/sender/orders')}
        />
      </SectionCard>
    </div>
  );
}

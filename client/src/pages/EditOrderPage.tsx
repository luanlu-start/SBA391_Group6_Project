import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, AlertTriangle } from 'lucide-react';
import type { Order } from '../types/order';
import { orderApi } from '../api/orderApi';
import type { ApiError } from '../api/ApiError';
import { getApiError } from '../api/ApiError';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';
import Alert from '../components/ui/Alert';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ApiErrorNotice from '../components/ApiErrorNotice';
import OrderForm from '../features/order/components/OrderForm';

export default function EditOrderPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [updatedOrder, setUpdatedOrder] = useState<Order | null>(null);

  useEffect(() => {
    if (!id) return;

    let mounted = true;
    setLoading(true);
    setError(null);

    orderApi
      .getById(id)
      .then(res => {
        if (mounted) setOrder(res.data);
      })
      .catch(cause => {
        if (mounted) setError(getApiError(cause));
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [id]);

  function handleSuccess(savedOrder: Order) {
    setUpdatedOrder(savedOrder);
    setTimeout(() => {
      navigate(`/sender/orders/${savedOrder.id}`);
    }, 1500);
  }

  if (loading) {
    return <LoadingSpinner text="Đang tải thông tin đơn hàng để chỉnh sửa…" />;
  }

  if (error || !order) {
    return (
      <div className="ui-stack">
        <PageHeader
          title="Không tìm thấy đơn hàng"
          eyebrow="Chỉnh sửa đơn hàng"
          actions={
            <Button
              variant="secondary"
              icon={<ArrowLeft size={16} />}
              onClick={() => navigate('/sender/orders')}
            >
              Quay lại danh sách
            </Button>
          }
        />
        <SectionCard>
          <div className="ui-stack">
            {error ? (
              <ApiErrorNotice error={error} />
            ) : (
              <p>Đơn hàng không tồn tại hoặc đã bị xóa khỏi hệ thống.</p>
            )}
            <div>
              <Button variant="secondary" onClick={() => navigate('/sender/orders')}>
                Về danh sách đơn hàng
              </Button>
            </div>
          </div>
        </SectionCard>
      </div>
    );
  }

  // Nếu đơn không còn ở trạng thái PENDING thì không cho sửa
  if (order.status !== 'PENDING') {
    return (
      <div className="ui-stack">
        <PageHeader
          title={`Chỉnh sửa đơn: ${order.orderCode}`}
          eyebrow="Quản lý đơn hàng"
          actions={
            <Button
              variant="secondary"
              icon={<ArrowLeft size={16} />}
              onClick={() => navigate(`/sender/orders/${order.id}`)}
            >
              Quay lại chi tiết đơn
            </Button>
          }
        />
        <Alert tone="warning" title="Không thể chỉnh sửa đơn hàng này">
          <div className="ui-row" style={{ alignItems: 'center', gap: '8px' }}>
            <AlertTriangle size={18} />
            <span>
              Đơn hàng đang ở trạng thái <strong>{order.status}</strong> nên không thể chỉnh sửa thông tin. Chỉ có thể sửa đơn khi đơn đang ở trạng thái <strong>Chờ xử lý (PENDING)</strong>.
            </span>
          </div>
        </Alert>
        <div>
          <Button variant="secondary" onClick={() => navigate(`/sender/orders/${order.id}`)}>
            Xem chi tiết đơn
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="ui-stack">
      <PageHeader
        title={`Chỉnh sửa đơn: ${order.orderCode}`}
        eyebrow="Quản lý giao nhận FPTPost"
        description="Cập nhật lại thông tin người nhận, địa chỉ nhận/giao hoặc chi tiết hàng hóa trước khi điều phối tài xế."
        actions={
          <Button
            variant="ghost"
            icon={<ArrowLeft size={16} />}
            onClick={() => navigate(`/sender/orders/${order.id}`)}
          >
            Quay lại chi tiết
          </Button>
        }
      />

      {updatedOrder && (
        <Alert tone="success" title="Cập nhật đơn hàng thành công!">
          <div className="ui-row" style={{ alignItems: 'center', gap: '8px' }}>
            <CheckCircle2 size={18} />
            <span>
              Thông tin đơn hàng <strong>{updatedOrder.orderCode}</strong> đã được lưu thành công. Đang chuyển về chi tiết đơn hàng...
            </span>
          </div>
        </Alert>
      )}

      <SectionCard
        title="Thông tin chỉnh sửa đơn hàng"
        description="Vui lòng kiểm tra kỹ các thông tin trước khi nhấn Lưu thay đổi."
      >
        <OrderForm
          initialData={order}
          onSuccess={handleSuccess}
          onCancel={() => navigate(`/sender/orders/${order.id}`)}
        />
      </SectionCard>
    </div>
  );
}

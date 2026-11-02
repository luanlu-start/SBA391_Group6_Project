
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MessageCircle, RefreshCw } from 'lucide-react';

import { orderApi } from '../api/orderApi';
import type { Order } from '../types/order';
import { getApiError, type ApiError } from '../api/ApiError';
import ApiErrorNotice from '../components/ApiErrorNotice';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import Button from '../components/ui/Button';

export default function CourierAssignedOrdersPage() {
  const navigate = useNavigate();

  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  async function loadOrders() {
    setLoading(true);
    setError(null);

    try {
      const response = await orderApi.getAssignedToMe();
      setOrders(response.data || []);
    } catch (cause) {
      setError(getApiError(cause));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOrders();
  }, []);

  return (
    <div className="ui-stack">
      <PageHeader
        title="Đơn hàng được phân công"
        description="Quản lý các đơn hàng và trò chuyện với người gửi."
        actions={
          <Button
            variant="secondary"
            icon={<RefreshCw size={16} />}
            onClick={() => void loadOrders()}
            disabled={loading}
          >
            Làm mới
          </Button>
        }
      />

      {error && <ApiErrorNotice error={error} />}

      <SectionCard title="Danh sách đơn hàng">
        {loading ? (
          <p>Đang tải danh sách đơn hàng...</p>
        ) : orders.length === 0 && !error ? (
          <p>Bạn chưa được phân công đơn hàng nào.</p>
        ) : (
          <div className="ui-stack">
            {orders.map(order => (
              <div
                key={order.id}
                className="ui-row"
                style={{
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: '16px',
                  padding: '16px',
                  borderBottom: '1px solid var(--border-subtle)',
                  flexWrap: 'wrap',
                }}
              >
                <div>
                  <strong>{order.orderCode}</strong>
                  <p>{order.productName}</p>
                  <p>Trạng thái: {order.status}</p>
                </div>

                <Button
                  variant="primary"
                  icon={<MessageCircle size={16} />}
                  onClick={() =>
                    navigate(`/orders/${order.id}/chat`)
                  }
                >
                  Nhắn tin
                </Button>
              </div>
            ))}
          </div>
        )}
      </SectionCard>
    </div>
  );
}

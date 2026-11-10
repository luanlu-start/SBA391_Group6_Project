
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { GitFork, MessageCircle, RefreshCw } from 'lucide-react';

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
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <strong>{order.orderCode}</strong>
                    {order.isRelay && (
                      <span
                        style={{
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          padding: '2px 8px',
                          borderRadius: '6px',
                          background: 'rgba(243, 112, 33, 0.15)',
                          color: 'var(--text-accent, #ffb17d)',
                          border: '1px solid rgba(243, 112, 33, 0.3)',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                        }}
                      >
                        <GitFork size={12} /> Tiếp sức
                      </span>
                    )}
                  </div>
                  <p>{order.productName}</p>
                  <p>Trạng thái: {order.status}</p>
                </div>

                <div style={{ display: 'flex', gap: '8px' }}>
                  {order.isRelay && (
                    <Button
                      variant="secondary"
                      icon={<GitFork size={16} style={{ color: 'var(--primary, #f37021)' }} />}
                      onClick={() => navigate(`/courier/relay?orderId=${order.id}`)}
                    >
                      Bàn giao tiếp sức
                    </Button>
                  )}
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
              </div>
            ))}
          </div>
        )}
      </SectionCard>
    </div>
  );
}

import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus,Search,Eye,RefreshCw,Package,MessageCircle, } from 'lucide-react';
import type { Order } from '../types/order';
import { ORDER_STATUS_MAP } from '../types/order';
import { orderApi } from '../api/orderApi';
import type { ApiError } from '../api/ApiError';
import { getApiError } from '../api/ApiError';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import DataTable from '../components/ui/DataTable';
import type { TableColumn } from '../components/ui/DataTable';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import Badge from '../components/ui/Badge';
import EmptyState from '../components/ui/EmptyState';
import ApiErrorNotice from '../components/ApiErrorNotice';

export default function OrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('');

  async function loadOrders() {
    setLoading(true);
    setError(null);
    try {
      const response = await orderApi.getAll();
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

  const filteredOrders = useMemo(() => {
    return orders.filter(order => {
      const query = searchQuery.trim().toLowerCase();
      const matchQuery =
        !query ||
        order.orderCode?.toLowerCase().includes(query) ||
        order.receiverName?.toLowerCase().includes(query) ||
        order.receiverPhone?.toLowerCase().includes(query) ||
        order.productName?.toLowerCase().includes(query) ||
        order.deliveryAddress?.toLowerCase().includes(query);

      const matchStatus = !statusFilter || order.status === statusFilter;

      return matchQuery && matchStatus;
    });
  }, [orders, searchQuery, statusFilter]);

  const columns: TableColumn<Order>[] = [
    {
      key: 'orderCode',
      header: 'Mã đơn & Ngày tạo',
      render: order => (
        <>
          <span className="ui-table-primary font-mono">{order.orderCode}</span>
          <span className="ui-table-secondary">
            {order.createdAt ? new Date(order.createdAt).toLocaleString('vi-VN') : '—'}
          </span>
        </>
      ),
    },
    {
      key: 'parties',
      header: 'Người gửi / Người nhận',
      render: order => (
        <>
          <span className="ui-table-primary">Người nhận: {order.receiverName} ({order.receiverPhone})</span>
          <span className="ui-table-secondary">Người gửi ID: #{order.senderId}</span>
        </>
      ),
    },
    {
      key: 'deliveryAddress',
      header: 'Địa chỉ nhận hàng',
      render: order => (
        <span className="ui-table-secondary" style={{ maxWidth: '320px' }}>
          {order.deliveryAddress}
        </span>
      ),
    },
    {
      key: 'product',
      header: 'Hàng hóa',
      render: order => (
        <>
          <span className="ui-table-primary">{order.productName}</span>
          <span className="ui-table-secondary">
            {order.quantity} kiện • {order.weightKg} kg
          </span>
        </>
      ),
    },
    {
      key: 'status',
      header: 'Trạng thái',
      render: order => {
        const config = ORDER_STATUS_MAP[order.status] ?? { label: order.status, tone: 'neutral' };
        return (
          <Badge dot tone={config.tone}>
            {config.label}
          </Badge>
        );
      },
    },
    {
      key: 'actions',
      header: 'Thao tác',
      align: 'right',
      render: order => (
        <div className="ui-table-actions">
          <Button
            variant="ghost"
            size="sm"
            icon={<Eye size={16} />}
            onClick={() => navigate(`/sender/orders/${order.id}`)}
          >
            Chi tiết
          </Button>

          <Button
            variant="secondary"
            size="sm"
            icon={<MessageCircle size={16} />}
            disabled={order.courierId == null}
            title={
              order.courierId == null
                ? 'Đơn chưa được gán người giao'
                : 'Mở cuộc trò chuyện của đơn'
            }
            onClick={() => navigate(`/orders/${order.id}/chat`)}
          >
            Trò chuyện
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="ui-stack">
      <PageHeader
        title="Danh sách đơn hàng"
        eyebrow="Quản lý giao nhận"
        description="Theo dõi, tra cứu trạng thái và quản lý toàn bộ các đơn hàng trong hệ thống FPTPost."
        actions={
          <Button
            icon={<Plus size={18} />}
            onClick={() => navigate('/sender/orders/new')}
          >
            Tạo đơn hàng mới
          </Button>
        }
      />

      <SectionCard>
        <form
          className="ui-toolbar"
          onSubmit={e => {
            e.preventDefault();
          }}
        >
          <Input
            label="Tìm kiếm"
            icon={<Search size={17} />}
            placeholder="Mã đơn, người nhận, số điện thoại, tên hàng..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
          />
          <Select
            label="Trạng thái"
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value)}
          >
            <option value="">Tất cả trạng thái</option>
            {Object.entries(ORDER_STATUS_MAP).map(([statusKey, config]) => (
              <option key={statusKey} value={statusKey}>
                {config.label}
              </option>
            ))}
          </Select>
          <Button
            variant="ghost"
            aria-label="Tải lại danh sách"
            onClick={() => void loadOrders()}
            disabled={loading}
            icon={<RefreshCw size={18} />}
          />
        </form>
      </SectionCard>

      <SectionCard
        title="Đơn hàng"
        description={`Tổng số ${filteredOrders.length} đơn hàng${statusFilter || searchQuery ? ' phù hợp bộ lọc' : ''}`}
        className="ui-card-table"
      >
        {error ? (
          <div className="ui-card-body ui-stack">
            <ApiErrorNotice error={error} />
            <div>
              <Button variant="secondary" onClick={() => void loadOrders()}>
                Thử lại
              </Button>
            </div>
          </div>
        ) : (
          <DataTable
            caption="Danh sách đơn hàng"
            columns={columns}
            rows={filteredOrders}
            rowKey={order => order.id}
            loading={loading}
            emptyState={
              <EmptyState
                icon={<Package size={24} />}
                title={orders.length === 0 ? 'Chưa có đơn hàng nào' : 'Không tìm thấy đơn hàng phù hợp'}
                description={
                  orders.length === 0
                    ? 'Bắt đầu gửi bưu kiện đầu tiên ngay hôm nay cùng dịch vụ vận chuyển FPTPost.'
                    : 'Thử điều chỉnh lại từ khóa tìm kiếm hoặc bỏ chọn bộ lọc trạng thái.'
                }
                action={
                  <Button
                    onClick={() => navigate('/sender/orders/new')}
                    icon={<Plus size={17} />}
                  >
                    Tạo đơn hàng mới
                  </Button>
                }
              />
            }
          />
        )}
      </SectionCard>
    </div>
  );
}

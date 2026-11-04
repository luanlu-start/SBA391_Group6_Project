import { useState } from 'react';
import { Search, ArrowUpRight } from 'lucide-react';
import Button from '../../../components/ui/Button';
import Input from '../../../components/ui/Input';
import Select from '../../../components/ui/Select';
import SectionCard from '../../../components/ui/SectionCard';
import DataTable from '../../../components/ui/DataTable';
import type { TableColumn } from '../../../components/ui/DataTable';
import Badge from '../../../components/ui/Badge';
import Pagination from '../../../components/ui/Pagination';
import EmptyState from '../../../components/ui/EmptyState';
import Alert from '../../../components/ui/Alert';
import Modal from '../../../components/ui/Modal';

interface SampleOrder { id: string; recipient: string; route: string; status: 'delivering' | 'completed' | 'pending'; }
const orders: SampleOrder[] = [
  { id: 'DH-260012', recipient: 'Nguyễn Văn An', route: 'Cần Thơ → TP. Hồ Chí Minh', status: 'delivering' },
  { id: 'DH-260013', recipient: 'Trần Minh Anh', route: 'Đà Nẵng → Huế', status: 'pending' },
  { id: 'DH-260014', recipient: 'Lê Hoàng Nam', route: 'Hà Nội → Hải Phòng', status: 'completed' },
  { id: 'DH-260015', recipient: 'Phạm Thanh Hà', route: 'Cần Thơ → Vĩnh Long', status: 'completed' },
  { id: 'DH-260016', recipient: 'Võ Minh Khang', route: 'TP. Hồ Chí Minh → Đồng Nai', status: 'delivering' },
  { id: 'DH-260017', recipient: 'Đỗ Ngọc Mai', route: 'Đà Nẵng → Quảng Ngãi', status: 'pending' },
];
const statuses = { delivering: 'Đang giao', completed: 'Đã giao', pending: 'Chờ nhận' } as const;

export default function ListExample() {
  const [view, setView] = useState<'data' | 'loading' | 'empty' | 'error'>('data');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<SampleOrder | null>(null);
  const filtered = orders.filter(order => (!status || order.status === status)
    && `${order.id} ${order.recipient}`.toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi')));
  const size = 4;
  const columns: TableColumn<SampleOrder>[] = [
    { key: 'id', header: 'Mã đơn', render: order => <span className="ui-table-primary">{order.id}</span> },
    { key: 'recipient', header: 'Người nhận', render: order => <><span>{order.recipient}</span><span className="ui-table-secondary">{order.route}</span></> },
    { key: 'status', header: 'Trạng thái', render: order => <Badge dot
      tone={order.status === 'completed' ? 'success' : order.status === 'pending' ? 'warning' : 'info'}>{statuses[order.status]}</Badge> },
    { key: 'actions', header: 'Thao tác', align: 'right', render: order => <Button size="sm" variant="ghost"
      onClick={() => setSelected(order)} icon={<ArrowUpRight size={15} />}>Chi tiết</Button> },
  ];
  return <div className="ui-stack">
    <div className="ui-row"><span className="ui-field-hint">Xem trạng thái:</span>
      <div className="ui-state-tabs" role="group" aria-label="Trạng thái danh sách mẫu">
        {([['data', 'Có dữ liệu'], ['loading', 'Đang tải'], ['empty', 'Trống'], ['error', 'Lỗi']] as const).map(([value, label]) =>
          <Button key={value} variant="ghost" size="sm" aria-pressed={view === value} onClick={() => setView(value)}>{label}</Button>)}
      </div>
    </div>
    <SectionCard title="Tìm kiếm và bộ lọc"><div className="ui-toolbar">
      <Input label="Tìm kiếm" placeholder="Mã đơn hoặc người nhận" icon={<Search size={17} />}
        value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} />
      <Select label="Trạng thái" value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
        <option value="">Tất cả trạng thái</option>{Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </Select>
      <Button variant="secondary" onClick={() => { setSearch(''); setStatus(''); setPage(0); }}>Xóa bộ lọc</Button>
    </div></SectionCard>
    <SectionCard title="Danh sách đơn hàng" description="Dữ liệu minh họa · Không gọi API nghiệp vụ" className="ui-card-table">
      {view === 'error' ? <div className="ui-card-body"><Alert tone="danger" title="Chưa tải được danh sách"
        action={<Button variant="secondary" size="sm" onClick={() => setView('data')}>Thử lại</Button>}>
        Ví dụ khi API gặp lỗi: hiển thị thông báo và cho phép người dùng thử lại.
      </Alert></div> : <>
        <DataTable caption="Danh sách đơn hàng mẫu" columns={columns} rowKey={order => order.id}
          rows={view === 'empty' ? [] : filtered.slice(page * size, (page + 1) * size)} loading={view === 'loading'}
          emptyState={<EmptyState title="Chưa có đơn hàng phù hợp" description="Thử thay đổi từ khóa hoặc bộ lọc."
            action={<Button variant="secondary" onClick={() => { setView('data'); setSearch(''); setStatus(''); setPage(0); }}>Xóa bộ lọc</Button>} />} />
        {view === 'data' && <Pagination page={page} size={size} totalElements={filtered.length}
          totalPages={Math.ceil(filtered.length / size)} onPageChange={setPage} />}
      </>}
    </SectionCard>
    <Modal isOpen={!!selected} title={`Đơn hàng ${selected?.id ?? ''}`} onClose={() => setSelected(null)} description="Chi tiết dữ liệu minh họa.">
      <div className="ui-stack"><dl className="ui-summary-list"><div><dt>Người nhận</dt><dd>{selected?.recipient}</dd></div>
        <div><dt>Tuyến giao</dt><dd>{selected?.route}</dd></div></dl>
        <div className="ui-form-actions"><Button onClick={() => setSelected(null)}>Đóng</Button></div>
      </div>
    </Modal>
  </div>;
}

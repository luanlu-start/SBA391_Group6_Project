import { Link, useLocation } from 'react-router-dom';
import { FileInput, List, PanelsTopLeft, Palette } from 'lucide-react';
import WorkspaceLayout from '../layouts/WorkspaceLayout';
import PageHeader from '../components/ui/PageHeader';
import Badge from '../components/ui/Badge';
import FormExample from '../features/ui-kit/components/FormExample';
import ListExample from '../features/ui-kit/components/ListExample';
import ComponentsExample from '../features/ui-kit/components/ComponentsExample';

const navigation = [
  { to: '/ui-kit', label: 'Form nhập liệu', icon: <FileInput size={18} /> },
  { to: '/ui-kit/list', label: 'Danh sách & bộ lọc', icon: <List size={18} /> },
  { to: '/ui-kit/components', label: 'Component & màu sắc', icon: <PanelsTopLeft size={18} /> },
];

export default function UiKitPage() {
  const { pathname } = useLocation();
  const section = pathname === '/ui-kit/list' ? 'list' : pathname === '/ui-kit/components' ? 'components' : 'form';
  return <WorkspaceLayout title="Giao diện chung / FPTPost" navigation={navigation}
    headerActions={<><Badge tone="info">Dành cho phát triển</Badge><Link to="/products" className="auth-text-link">CRUD đang chạy ↗</Link></>}>
    <div className="ui-stack">
      <PageHeader eyebrow="FPTPost · UI foundation" title="Một giao diện. Cùng cách viết."
        description="Form, danh sách và component mẫu để các module thống nhất từ những màn hình đầu tiên." />
      <div className="ui-showcase-intro"><Palette size={22} aria-hidden="true" />
        <div><strong>{section === 'form' ? 'Mẫu form hai cột' : section === 'list' ? 'Mẫu danh sách và các trạng thái' : 'Thư viện component'}</strong>
        Dùng component trong components/ui. Nội dung tại đây là mẫu, chưa thực hiện nghiệp vụ giao hàng.</div>
      </div>
      {section === 'form' ? <FormExample /> : section === 'list' ? <ListExample /> : <ComponentsExample />}
    </div>
  </WorkspaceLayout>;
}

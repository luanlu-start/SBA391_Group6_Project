import { useState } from 'react';
import type { CSSProperties } from 'react';
import { Plus } from 'lucide-react';
import SectionCard from '../../../components/ui/SectionCard';
import Button from '../../../components/ui/Button';
import Badge from '../../../components/ui/Badge';
import Input from '../../../components/ui/Input';
import Alert from '../../../components/ui/Alert';
import Modal from '../../../components/ui/Modal';

export default function ComponentsExample() {
  const [open, setOpen] = useState(false);
  return <div className="ui-stack">
    <SectionCard title="Màu sắc" description="Nguồn duy nhất: styles/tokens.css">
      <div className="ui-token-grid">{[
        ['--primary', 'Màu chính'], ['--bg-secondary', 'Nền thẻ'], ['--accent-emerald', 'Thành công'], ['--accent-rose', 'Lỗi'],
      ].map(([token, label]) => <div key={token}>
        <div className="ui-token-swatch" style={{ '--swatch': `var(${token})` } as CSSProperties} />
        <p>{label}</p><code>{token}</code>
      </div>)}</div>
    </SectionCard>
    <SectionCard title="Nút thao tác" description="Một thao tác chính trong mỗi form; nút hủy dùng secondary.">
      <div className="ui-row"><Button icon={<Plus size={17} />} onClick={() => setOpen(true)}>Thao tác chính</Button>
        <Button variant="secondary" onClick={() => setOpen(true)}>Thao tác phụ</Button>
        <Button variant="ghost" onClick={() => setOpen(true)}>Ít ưu tiên</Button>
        <Button variant="danger" onClick={() => setOpen(true)}>Xóa dữ liệu</Button>
        <Button disabled>Vô hiệu hóa</Button><Button loading loadingText="Đang lưu…">Lưu</Button>
      </div>
    </SectionCard>
    <SectionCard title="Trạng thái dữ liệu"><div className="ui-row">
      <Badge tone="neutral" dot>Nháp</Badge><Badge tone="warning" dot>Chờ xử lý</Badge>
      <Badge tone="info" dot>Đang giao</Badge><Badge tone="success" dot>Hoàn thành</Badge><Badge tone="danger" dot>Đã hủy</Badge>
    </div></SectionCard>
    <SectionCard title="Trường nhập liệu"><div className="ui-form-grid">
      <Input label="Bình thường" placeholder="Nhập thông tin" hint="Gợi ý ngắn đặt dưới trường nhập." />
      <Input label="Có lỗi" defaultValue="0901" error="Số điện thoại không đúng định dạng." />
      <Input label="Chỉ đọc" value="DH-260012" readOnly /><Input label="Vô hiệu hóa" placeholder="Chưa thể chỉnh sửa" disabled />
    </div></SectionCard>
    <SectionCard title="Thông báo"><div className="ui-stack">
      <Alert tone="info">Thông tin bổ sung giúp người dùng thao tác.</Alert>
      <Alert tone="success">Thay đổi đã được lưu thành công.</Alert>
      <Alert tone="warning">Kiểm tra thông tin trước khi tiếp tục.</Alert>
      <Alert tone="danger">Chưa thể hoàn tất thao tác. Vui lòng thử lại.</Alert>
    </div></SectionCard>
    <Modal isOpen={open} onClose={() => setOpen(false)} title="Hộp thoại dùng chung" description="Hỗ trợ bàn phím, giữ focus và đóng bằng Esc.">
      <div className="ui-stack"><p>Dùng cho xác nhận, form ngắn hoặc hiển thị chi tiết.</p>
        <div className="ui-form-actions"><Button variant="secondary" onClick={() => setOpen(false)}>Hủy bỏ</Button>
          <Button onClick={() => setOpen(false)}>Xác nhận</Button></div>
      </div>
    </Modal>
  </div>;
}

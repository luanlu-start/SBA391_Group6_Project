import { Link } from 'react-router-dom';
import { AlertCircle, Home, ArrowLeft } from 'lucide-react';
import Button from '../components/ui/Button';

export default function NotFoundPage() {
  return (
    <div className="not-found-container">
      <div className="not-found-card">
        <AlertCircle size={64} className="text-warning mb-3" />
        <h1 className="not-found-code">404</h1>
        <h2 className="not-found-title">Trang không tồn tại</h2>
        <p className="not-found-desc">
          Đường dẫn bạn yêu cầu không tìm thấy trên hệ thống hoặc đã được di chuyển.
        </p>
        <div className="not-found-actions">
          <Link to="/" className="ui-button ui-button-primary">
            <Home size={18} />
            <span>Về trang chủ</span>
          </Link>
          <Button onClick={() => window.history.back()} variant="secondary">
            <ArrowLeft size={18} />
            <span>Quay lại</span>
          </Button>
        </div>
      </div>
    </div>
  );
}

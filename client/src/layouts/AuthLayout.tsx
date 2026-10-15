import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Package, Route } from 'lucide-react';
import '../styles/auth.css';

export default function AuthLayout({ children, variant = 'login' }: { children: ReactNode; variant?: 'login' | 'register' }) {
  return (
    <main id="main-content" className={`auth-layout${variant === 'register' ? ' auth-layout-register' : ''}`}>
      <aside className="auth-visual" aria-label="FPost — Kết nối những hành trình">
        <Link className="auth-brand" to="/" aria-label="FPost — Trang chủ"><Package size={27} aria-hidden="true" />F<span>Post</span></Link>
        <div className="auth-illustration" aria-hidden="true">
          <svg viewBox="0 0 460 300" fill="none">
            <path className="auth-map-grid" d="M0 75H460M0 150H460M0 225H460M92 0V300M184 0V300M276 0V300M368 0V300" />
            <path className="auth-route-shadow" d="M62 226H157Q181 226 181 202V104Q181 80 205 80H305Q329 80 329 104V153Q329 177 353 177H399" />
            <path className="auth-route-line" d="M62 226H157Q181 226 181 202V104Q181 80 205 80H305Q329 80 329 104V153Q329 177 353 177H399" />
            <circle cx="62" cy="226" r="21" className="auth-map-halo" /><circle cx="62" cy="226" r="7" className="auth-map-dot" />
            <circle cx="399" cy="177" r="21" className="auth-map-halo" /><circle cx="399" cy="177" r="7" className="auth-map-dot" />
            <path d="M205 142L254 117L303 142V198L254 224L205 198V142Z" className="auth-parcel" />
            <path d="M205 142L254 168L303 142M254 168V224M230 130L279 155V173" className="auth-parcel-fold" />
            <path d="M218 178L239 189M218 187L230 193" className="auth-parcel-label" />
          </svg>
          <span className="auth-route-caption"><Route size={16} />Kết nối người gửi và người giao</span>
        </div>
        <div className="auth-story">
          <p className="auth-eyebrow">CÙNG BẠN TRÊN MỌI HÀNH TRÌNH</p>
          <h2>Mỗi kiện hàng,<br /><span>một kết nối mới.</span></h2>
          <p>Gửi hàng, theo dõi hành trình và quản lý đơn giao của bạn tại một nơi.</p>
        </div>
        <p className="auth-visual-footer">FPost · Kết nối những hành trình</p>
      </aside>
      <section className="auth-panel" aria-label={variant === 'register' ? 'Đăng ký FPost' : 'Đăng nhập FPost'}>
        <Link className="auth-back" to="/"><ArrowLeft size={17} aria-hidden="true" />Về trang chủ</Link>
        <div className="auth-content">
          <Link className="auth-brand auth-form-brand" to="/" aria-label="FPost — Trang chủ"><Package size={25} aria-hidden="true" />F<span>Post</span></Link>
          {children}
        </div>
        <p className="auth-panel-footer">{variant === 'register' ? 'FPost · Kết nối những hành trình.' : 'Dùng tài khoản đã xác thực email để tiếp tục.'}</p>
      </section>
    </main>
  );
}

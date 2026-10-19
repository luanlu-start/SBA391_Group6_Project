import { Routes, Route, useLocation } from 'react-router-dom';
import { lazy, Suspense, useEffect } from 'react';
import MainLayout from '../layouts/MainLayout';
import HomePage from '../pages/HomePage';
import ProductsPage from '../pages/ProductsPage';
import NotFoundPage from '../pages/NotFoundPage';
import RegisterPage from '../pages/RegisterPage';
import VerifyOtpPage from '../pages/VerifyOtpPage';
import LoginPage from '../pages/LoginPage';
import AccountPage from '../pages/AccountPage';
import SecurityPage from '../pages/SecurityPage';
import LegalPage from '../pages/LegalPage';

// Các trang quản lý đơn của Người gửi (Sender)
import OrdersPage from '../pages/OrdersPage';
import CreateOrderPage from '../pages/CreateOrderPage';
import OrderDetailPage from '../pages/OrderDetailPage';

// Trang Sàn đơn dành cho Bưu tá (Courier)
import CourierMarketplacePage from '../pages/CourierMarketplacePage';
import CourierOrderDetailPage from '../pages/CourierOrderDetailPage';

const UiKitPage = import.meta.env.DEV ? lazy(() => import('../pages/UiKitPage')) : null;

export default function AppRoutes() {
  const { pathname, hash, key } = useLocation();

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      if (hash) document.getElementById(hash.slice(1))?.scrollIntoView();
      else window.scrollTo(0, 0);
    });
    return () => cancelAnimationFrame(frame);
  }, [pathname, hash, key]);

  return (
      <Routes>
        {UiKitPage && (
            <Route
                path="/ui-kit/*"
                element={
                  <Suspense fallback={<p role="status">Đang tải mẫu giao diện…</p>}>
                    <UiKitPage />
                  </Suspense>
                }
            />
        )}

        {/* Auth routes ngoài layout chính */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Các route sử dụng chung MainLayout */}
        <Route path="/" element={<MainLayout />}>
          <Route index element={<HomePage />} />
          <Route path="products" element={<ProductsPage />} />

          {/* Luồng người gửi đơn (Sender) */}
          <Route path="sender/orders" element={<OrdersPage />} />
          <Route path="sender/orders/new" element={<CreateOrderPage />} />
          <Route path="sender/orders/:id" element={<OrderDetailPage />} />

          {/* Luồng sàn đơn bưu tá (Courier) - SCR-28 / UC-16 & SCR-30 / UC-17 */}
          <Route path="courier/market" element={<CourierMarketplacePage />} />
          <Route path="courier/market/:id" element={<CourierOrderDetailPage />} />

          {/* Tài khoản & Cài đặt */}
          <Route path="verify" element={<VerifyOtpPage />} />
          <Route path="account" element={<AccountPage />} />
          <Route path="me/security" element={<SecurityPage />} />
          <Route path="legal" element={<LegalPage />} />

          {/* Trang 404 */}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
  );
}
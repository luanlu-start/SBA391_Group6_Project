import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { lazy, Suspense, useEffect } from 'react';
import MainLayout from '../layouts/MainLayout';
import AdminLayout from '../layouts/AdminLayout';
import HomePage from '../pages/HomePage';
import ProductsPage from '../pages/ProductsPage';
import NotFoundPage from '../pages/NotFoundPage';
import RegisterPage from '../pages/RegisterPage';
import VerifyOtpPage from '../pages/VerifyOtpPage';
import LoginPage from '../pages/LoginPage';
import AccountPage from '../pages/AccountPage';
import SecurityPage from '../pages/SecurityPage';
import LegalPage from '../pages/LegalPage';
import AdminParametersPage from '../pages/AdminParametersPage';
import OrdersPage from '../pages/OrdersPage';
import CreateOrderPage from '../pages/CreateOrderPage';
import OrderDetailPage from '../pages/OrderDetailPage';

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
      {UiKitPage && <Route path="/ui-kit/*" element={<Suspense fallback={<p role="status">Đang tải mẫu giao diện…</p>}><UiKitPage /></Suspense>} />}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<Navigate to="params" replace />} />
        <Route path="params" element={<AdminParametersPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<HomePage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="sender/orders" element={<OrdersPage />} />
        <Route path="sender/orders/new" element={<CreateOrderPage />} />
        <Route path="sender/orders/:id" element={<OrderDetailPage />} />
        <Route path="verify" element={<VerifyOtpPage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="me/security" element={<SecurityPage />} />
        <Route path="legal" element={<LegalPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

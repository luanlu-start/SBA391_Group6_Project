import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { lazy, Suspense, useEffect } from 'react';
import MainLayout from '../layouts/MainLayout';
import AdminLayout from '../layouts/AdminLayout';
import ModeratorLayout from '../layouts/ModeratorLayout';
import KycReviewPage from '../pages/KycReviewPage';
import NotificationsPage from '../pages/NotificationsPage';
import NotificationSettingsPage from '../pages/NotificationSettingsPage';
import HomePage from '../pages/HomePage';
import ProductsPage from '../pages/ProductsPage';
import NotFoundPage from '../pages/NotFoundPage';
import RegisterPage from '../pages/RegisterPage';
import VerifyOtpPage from '../pages/VerifyOtpPage';
import LoginPage from '../pages/LoginPage';
import ForgotPasswordPage from '../pages/ForgotPasswordPage';
import ResetPasswordPage from '../pages/ResetPasswordPage';
import AccountPage from '../pages/AccountPage';
import SecurityPage from '../pages/SecurityPage';
import ProfilePage from '../pages/ProfilePage';
import AddressesPage from '../pages/AddressesPage';
import KycPage from '../pages/KycPage';
import CourierRegisterPage from '../pages/CourierRegisterPage';
import CourierPage from '../pages/CourierPage';
import LegalPage from '../pages/LegalPage';
import AdminParametersPage from '../pages/AdminParametersPage';
import AdminAuditPage from '../pages/AdminAuditPage';
import AdminPromotionsPage from '../pages/AdminPromotionsPage';
import WalletPage from '../pages/WalletPage';
import OrderPaymentPage from '../pages/OrderPaymentPage';
import VNPayReturnPage from '../pages/VNPayReturnPage';
import OrdersPage from '../pages/OrdersPage';
import CreateOrderPage from '../pages/CreateOrderPage';
import EditOrderPage from '../pages/EditOrderPage';
import OrderDetailPage from '../pages/OrderDetailPage';
import OrderOffersPage from '../pages/OrderOffersPage';
import ChatPage from '../pages/ChatPage';
import CourierOrderDetailPage from "../pages/CourierOrderDetailPage";
import CourierMarketplacePage from "../pages/CourierMarketplacePage";

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
      <Route path="/forgot" element={<ForgotPasswordPage />} />
      <Route path="/reset" element={<ResetPasswordPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/mod" element={<ModeratorLayout />}>
        <Route index element={<Navigate to="kyc" replace />} />
        <Route path="kyc" element={<KycReviewPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<Navigate to="params" replace />} />
        <Route path="params" element={<AdminParametersPage />} />
        <Route path="audit" element={<AdminAuditPage />} />
        <Route path="promotions" element={<AdminPromotionsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<HomePage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="wallet" element={<WalletPage />} />
        <Route path="orders/payment" element={<OrderPaymentPage />} />
        <Route path="orders/:id/payment" element={<OrderPaymentPage />} />
        <Route path="payment/vnpay-return" element={<VNPayReturnPage />} />
        <Route path="vnpay-return" element={<VNPayReturnPage />} />
        <Route path="sender/orders" element={<OrdersPage />} />
        <Route path="sender/orders/new" element={<CreateOrderPage />} />
        <Route path="sender/orders/:id/edit" element={<EditOrderPage />} />
        <Route path="sender/orders/:id" element={<OrderDetailPage />} />
        <Route path="sender/orders/:id/offers" element={<OrderOffersPage />} />
        <Route path="orders/:id/offers" element={<OrderOffersPage />} />
        <Route path="courier/market" element={<CourierMarketplacePage />} />
        <Route path="courier/market/:id" element={<CourierOrderDetailPage />} />
        <Route path="verify" element={<VerifyOtpPage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="me/notification-settings" element={<NotificationSettingsPage />} />
        <Route path="me/security" element={<SecurityPage />} />
        <Route path="me/profile" element={<ProfilePage />} />
        <Route path="me/addresses" element={<AddressesPage />} />
        <Route path="me/kyc" element={<KycPage />} />
        <Route path="me/courier-register" element={<CourierRegisterPage />} />
        <Route path="courier" element={<CourierPage />} />
        <Route path="legal" element={<LegalPage />} />
        <Route path="*" element={<NotFoundPage />} />
        <Route path="orders/:orderId/chat" element={<ChatPage />} />
      </Route>
    </Routes>
  );
}

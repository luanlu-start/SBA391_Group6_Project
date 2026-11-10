import { BrowserRouter } from 'react-router-dom';
import AppRoutes from './routes/AppRoutes';
import AuthProvider from './auth/AuthProvider';
import NotificationProvider from './features/notifications/NotificationProvider';
import AvatarProvider from './features/profile/AvatarProvider';

function App() {
  return (
    <BrowserRouter>
      <a href="#main-content" className="skip-link">Đến nội dung chính</a>
      <AuthProvider><AvatarProvider><NotificationProvider><AppRoutes /></NotificationProvider></AvatarProvider></AuthProvider>
    </BrowserRouter>
  );
}

export default App;

import { BrowserRouter } from 'react-router-dom';
import AppRoutes from './routes/AppRoutes';
import AuthProvider from './auth/AuthProvider';

function App() {
  return (
    <BrowserRouter>
      <a href="#main-content" className="skip-link">Đến nội dung chính</a>
      <AuthProvider><AppRoutes /></AuthProvider>
    </BrowserRouter>
  );
}

export default App;

import { Outlet } from 'react-router-dom';
import Navbar from '../components/common/Navbar';
import Footer from '../components/common/Footer';

export default function MainLayout() {
  return (
    <div className="app-shell">
      <Navbar />
      <main className="app-main-content">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}

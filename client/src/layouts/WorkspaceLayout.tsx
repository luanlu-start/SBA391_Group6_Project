import { NavLink, Link } from 'react-router-dom';
import { Package, ArrowLeft } from 'lucide-react';
import type { ReactNode } from 'react';

interface WorkspaceNavigationItem {
  label: string;
  to: string;
  icon: ReactNode;
}

interface WorkspaceLayoutProps {
  title: string;
  navigation: WorkspaceNavigationItem[];
  children: ReactNode;
  headerActions?: ReactNode;
}

export default function WorkspaceLayout({ title, navigation, children, headerActions }: WorkspaceLayoutProps) {
  return <div className="ui-workspace">
    <aside className="ui-sidebar">
      <Link to="/" className="ui-workspace-brand" aria-label="FPost — Trang chủ">
        <span><Package size={20} aria-hidden="true" /></span><span>F<b>Post</b></span>
      </Link>
      <div><p className="ui-sidebar-label">KHÔNG GIAN LÀM VIỆC</p>
        <nav aria-label="Điều hướng không gian làm việc">{navigation.map(item =>
          <NavLink key={item.to} to={item.to} end className={({ isActive }) => `ui-sidebar-link${isActive ? ' active' : ''}`}>
            <span aria-hidden="true">{item.icon}</span>{item.label}
          </NavLink>)}</nav>
      </div>
      <Link to="/" className="ui-sidebar-link ui-sidebar-footer"><ArrowLeft size={16} />Về trang chủ</Link>
    </aside>
    <div className="ui-workspace-main">
      <header className="ui-workspace-topbar"><span>{title}</span><div className="ui-row">{headerActions}</div></header>
      <main id="main-content" className="ui-workspace-content">{children}</main>
    </div>
  </div>;
}

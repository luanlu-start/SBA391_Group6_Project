import { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Layers, Activity, Package, FileCode, CheckCircle2, AlertCircle, RefreshCw } from 'lucide-react';
import { healthApi } from '../../api/healthApi';

export default function Navbar() {
  const location = useLocation();
  const [serverHealth, setServerHealth] = useState({ status: 'CHECKING', uptimeMs: null });
  const [isChecking, setIsChecking] = useState(false);

  const checkStatus = async () => {
    setIsChecking(true);
    try {
      const res = await healthApi.getHealth();
      if (res && res.success) {
        setServerHealth({ status: 'ONLINE', details: res.data });
      } else {
        setServerHealth({ status: 'OFFLINE', details: null });
      }
    } catch {
      setServerHealth({ status: 'OFFLINE', details: null });
    } finally {
      setIsChecking(false);
    }
  };

  useEffect(() => {
    checkStatus();
    const interval = setInterval(checkStatus, 15000);
    return () => clearInterval(interval);
  }, []);

  return (
    <header className="navbar-container">
      <div className="navbar-content">
        <Link to="/" className="navbar-brand">
          <div className="brand-logo">
            <Layers className="brand-icon" size={24} />
          </div>
          <div className="brand-text">
            <span className="brand-title">SBA391 <span className="brand-accent">Enterprise</span></span>
            <span className="brand-badge">Group 6</span>
          </div>
        </Link>

        <nav className="navbar-links">
          <Link
            to="/"
            className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}
          >
            <Activity size={18} />
            <span>Architecture & Health</span>
          </Link>
          <Link
            to="/products"
            className={`nav-link ${location.pathname === '/products' ? 'active' : ''}`}
          >
            <Package size={18} />
            <span>Products CRUD Demo</span>
          </Link>
          <a
            href="http://localhost:8080/swagger-ui/index.html"
            target="_blank"
            rel="noopener noreferrer"
            className="nav-link external-link"
            title="Open Spring Boot OpenAPI / Swagger UI"
          >
            <FileCode size={18} />
            <span>Swagger API Docs</span>
          </a>
        </nav>

        <div className="navbar-actions">
          <div
            className={`health-pill ${serverHealth.status === 'ONLINE' ? 'online' : serverHealth.status === 'OFFLINE' ? 'offline' : 'checking'}`}
            onClick={checkStatus}
            title="Click to re-ping backend server"
          >
            {serverHealth.status === 'ONLINE' ? (
              <CheckCircle2 size={16} className="pill-icon text-success" />
            ) : serverHealth.status === 'OFFLINE' ? (
              <AlertCircle size={16} className="pill-icon text-danger" />
            ) : (
              <RefreshCw size={16} className={`pill-icon ${isChecking ? 'spin' : ''}`} />
            )}
            <span className="pill-text">
              Backend: {serverHealth.status}
            </span>
          </div>
        </div>
      </div>
    </header>
  );
}

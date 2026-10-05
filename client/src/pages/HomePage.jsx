import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { 
  Server, 
  Cpu, 
  Database, 
  Layers, 
  Shield, 
  CheckCircle, 
  AlertTriangle, 
  ExternalLink, 
  ArrowRight, 
  Package, 
  FileCode, 
  RefreshCw,
  FolderTree,
  Code2
} from 'lucide-react';
import { healthApi } from '../api/healthApi';

export default function HomePage() {
  const [healthData, setHealthData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchHealth = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await healthApi.getHealth();
      if (res && res.success) {
        setHealthData(res.data);
      } else {
        setError('Server responded with an unexpected status');
      }
    } catch (err) {
      setError(err.message || 'Cannot connect to backend service');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHealth();
  }, []);

  const formatUptime = (ms) => {
    if (!ms) return '0s';
    const seconds = Math.floor((ms / 1000) % 60);
    const minutes = Math.floor((ms / (1000 * 60)) % 60);
    const hours = Math.floor(ms / (1000 * 60 * 60));
    return `${hours}h ${minutes}m ${seconds}s`;
  };

  return (
    <div className="home-container">
      {/* Hero Header */}
      <section className="hero-section">
        <div className="hero-badge">
          <Layers size={14} /> Production-Ready Monolithic Architecture
        </div>
        <h1 className="hero-title">
          SBA391 <span className="gradient-text">Enterprise Full-Stack</span> Platform
        </h1>
        <p className="hero-subtitle">
          Cấu trúc dự án chuẩn doanh nghiệp kết hợp <strong>React 18 SPA (Vite)</strong> và <strong>Java 21 Spring Boot 3</strong> theo mô hình 3-Tier Layered Architecture với Spring Data MongoDB (ODM / JPA Pattern).
        </p>

        <div className="hero-actions">
          <Link to="/products" className="btn btn-primary">
            <Package size={18} />
            <span>Trải nghiệm CRUD Demo</span>
            <ArrowRight size={16} />
          </Link>
          <a
            href="http://localhost:8080/swagger-ui/index.html"
            target="_blank"
            rel="noopener noreferrer"
            className="btn btn-outline"
          >
            <FileCode size={18} />
            <span>OpenAPI / Swagger UI</span>
            <ExternalLink size={14} />
          </a>
        </div>
      </section>

      {/* Real-time Health Monitor Card */}
      <section className="health-card-section">
        <div className="glass-card health-monitor-card">
          <div className="card-header-row">
            <div className="card-title-group">
              <Server className="text-primary" size={22} />
              <div>
                <h3 className="card-title">Live Server Connectivity & Health</h3>
                <p className="card-subtitle">Giám sát kết nối giữa Client (React) và Backend (Spring Boot /api/v1/health)</p>
              </div>
            </div>
            <button 
              onClick={fetchHealth} 
              className="btn btn-sm btn-secondary" 
              disabled={loading}
              title="Refresh status"
            >
              <RefreshCw size={14} className={loading ? 'spin' : ''} />
              <span>Ping Server</span>
            </button>
          </div>

          <div className="health-content-grid">
            <div className="health-stat-box">
              <span className="stat-label">Server Status</span>
              <div className="stat-value-row">
                {loading ? (
                  <span className="text-muted">Kiểm tra...</span>
                ) : healthData ? (
                  <span className="badge badge-success">
                    <CheckCircle size={14} /> ONLINE (200 OK)
                  </span>
                ) : (
                  <span className="badge badge-danger">
                    <AlertTriangle size={14} /> OFFLINE / UNREACHABLE
                  </span>
                )}
              </div>
            </div>

            <div className="health-stat-box">
              <span className="stat-label">Active Profile</span>
              <div className="stat-value">
                <code>{healthData?.profile || 'dev'}</code>
              </div>
            </div>

            <div className="health-stat-box">
              <span className="stat-label">JVM Version</span>
              <div className="stat-value">
                {healthData?.jvmVersion || 'Java 21'}
              </div>
            </div>

            <div className="health-stat-box">
              <span className="stat-label">Server Uptime</span>
              <div className="stat-value">
                {formatUptime(healthData?.uptimeMs)}
              </div>
            </div>
          </div>

          {error && (
            <div className="alert alert-warning mt-4">
              <AlertTriangle size={18} />
              <div>
                <strong>Chưa kết nối được với Server:</strong> {error}
                <p className="alert-tip">
                  Hãy mở terminal và chạy <code>npm run dev:server</code> hoặc <code>npm run dev</code> ở thư mục gốc để khởi động Spring Boot.
                </p>
              </div>
            </div>
          )}
        </div>
      </section>

      {/* Enterprise Architecture Blueprint */}
      <section className="architecture-section">
        <div className="section-header">
          <h2 className="section-title">Kiến trúc 3-Tier Doanh nghiệp</h2>
          <p className="section-subtitle">
            Cấu trúc phân tầng tiêu chuẩn giúp dự án dễ bảo trì, phân chia công việc cho các thành viên và mở rộng (Scalable)
          </p>
        </div>

        <div className="layers-grid">
          {/* Layer 1: Client SPA */}
          <div className="layer-card">
            <div className="layer-badge">Tier 1: Presentation Layer</div>
            <div className="layer-icon-wrapper bg-blue">
              <Cpu size={28} />
            </div>
            <h3 className="layer-title">React 18 + Vite SPA</h3>
            <ul className="layer-features">
              <li><strong>Axios Interceptors:</strong> Tự động gắn token, format dữ liệu & bắt lỗi tập trung</li>
              <li><strong>React Router v6:</strong> Định tuyến phân trang declarative</li>
              <li><strong>Modular Structure:</strong> Tách biệt components, pages, api services, layouts</li>
              <li><strong>Responsive CSS Tokens:</strong> Thiết kế giao diện hiện đại không phụ thuộc nặng thư viện</li>
            </ul>
          </div>

          {/* Layer 2: API Gateway & Controllers */}
          <div className="layer-card">
            <div className="layer-badge">Tier 2: API & Gateway Layer</div>
            <div className="layer-icon-wrapper bg-indigo">
              <Shield size={28} />
            </div>
            <h3 className="layer-title">Spring Boot REST Controllers</h3>
            <ul className="layer-features">
              <li><strong>CORS Configuration:</strong> Bảo mật Origin, Headers và Methods cho SPA</li>
              <li><strong>OpenAPI / Swagger 3:</strong> Tự động sinh tài liệu API tương tác tại <code>/swagger-ui/index.html</code></li>
              <li><strong>Bean Validation:</strong> Kiểm tra input <code>@Valid</code> trước khi chạm vào logic</li>
              <li><strong>GlobalExceptionHandler:</strong> Bắt mọi lỗi 400, 404, 500 thành định dạng JSON chuẩn</li>
            </ul>
          </div>

          {/* Layer 3: Service & Persistence */}
          <div className="layer-card">
            <div className="layer-badge">Tier 3: Business & Data Layer</div>
            <div className="layer-icon-wrapper bg-emerald">
              <Database size={28} />
            </div>
            <h3 className="layer-title">Services & MongoDB (ODM)</h3>
            <ul className="layer-features">
              <li><strong>Service Layer:</strong> Xử lý toàn bộ Business Rules độc lập với Controller</li>
              <li><strong>Spring Data ODM / JPA Pattern:</strong> Ánh xạ Document <code>@Document</code> qua <code>MongoRepository</code></li>
              <li><strong>Derived Queries:</strong> Tìm kiếm, lọc theo danh mục, phân trang tự động</li>
              <li><strong>Resilient Store:</strong> Hỗ trợ MongoDB thật và fallback thông minh giúp demo mượt mà</li>
            </ul>
          </div>
        </div>
      </section>

      {/* Project Structure Tree */}
      <section className="structure-section">
        <div className="glass-card">
          <div className="structure-header">
            <FolderTree className="text-primary" size={24} />
            <div>
              <h3 className="card-title">Cấu trúc thư mục Monolithic chuẩn</h3>
              <p className="card-subtitle">Các thành viên trong nhóm code đúng vị trí để tránh xung đột Git</p>
            </div>
          </div>

          <div className="tree-grid">
            <div className="tree-box">
              <div className="tree-title">
                <Code2 size={16} /> <strong>Backend (server/src/main/java/...)</strong>
              </div>
              <pre className="tree-code">
{`com.group6.project/
├── Application.java          # Entry point
├── common/                   # Dùng chung toàn hệ thống
│   ├── response/             # ApiResponse<T> chuẩn hoá
│   └── exception/            # GlobalExceptionHandler, ErrorCode
├── config/                   # Cấu hình CORS, OpenAPI Swagger
└── modules/                  # Chia module chức năng
    ├── health/               # Endpoint kiểm tra Server
    └── product/              # Reference Module mẫu:
        ├── controller/       # Nhận HTTP request & validation
        ├── service/          # Business logic & Interface
        ├── repository/       # MongoRepository kết nối DB
        ├── model/            # Document Entity (@Document)
        └── dto/              # Request / Response Transfer`}
              </pre>
            </div>

            <div className="tree-box">
              <div className="tree-title">
                <Code2 size={16} /> <strong>Frontend (client/src/...)</strong>
              </div>
              <pre className="tree-code">
{`client/src/
├── api/                      # Giao tiếp HTTP với Backend
│   ├── axiosClient.js        # Cấu hình Axios & Interceptors
│   ├── healthApi.js          # API check server
│   └── productApi.js         # API CRUD Product
├── components/               # Components tái sử dụng
│   ├── common/               # Navbar, Footer, StatusBadge...
│   └── ui/                   # Modal, Card...
├── layouts/                  # Layout bọc ứng dụng (MainLayout)
├── pages/                    # Các trang (Home, Products, 404)
├── routes/                   # Cấu hình React Router
└── styles/index.css          # Design system & tokens`}
              </pre>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

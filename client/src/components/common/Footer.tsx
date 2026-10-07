import { Database, ShieldCheck, Terminal, Cpu } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="footer-container">
      <div className="footer-content">
        <div className="footer-top">
          <div className="footer-col">
            <h4 className="footer-heading">SBA391 Monolithic Architecture</h4>
            <p className="footer-desc">
              Group 6 full-stack development base with React, Spring Boot, SQL Server and MongoDB.
            </p>
          </div>

          <div className="footer-col">
            <h4 className="footer-heading">Stack Components</h4>
            <ul className="footer-list">
              <li><Cpu size={14} /> Frontend: React 19.3 + TypeScript + Vite 8 + Axios + React Router</li>
              <li><Terminal size={14} /> Backend: Spring Boot 4.1.1 (Java 25)</li>
              <li><Database size={14} /> Persistence: SQL Server & MongoDB</li>
              <li><ShieldCheck size={14} /> Documentation: OpenAPI 3 / Swagger UI</li>
            </ul>
          </div>

          <div className="footer-col">
            <h4 className="footer-heading">Quick Ports</h4>
            <div className="footer-ports">
              <div className="port-item">
                <span className="port-label">Client (Vite):</span>
                <code className="port-code">http://localhost:5173</code>
              </div>
              <div className="port-item">
                <span className="port-label">API Server:</span>
                <code className="port-code">http://localhost:8080/api/v1</code>
              </div>
              <div className="port-item">
                <span className="port-label">Swagger UI:</span>
                <code className="port-code">http://localhost:8080/swagger-ui/index.html</code>
              </div>
            </div>
          </div>
        </div>

        <div className="footer-bottom">
          <p>© {new Date().getFullYear()} SBA391 - Group 6 Project. Designed for enterprise production standards.</p>
        </div>
      </div>
    </footer>
  );
}

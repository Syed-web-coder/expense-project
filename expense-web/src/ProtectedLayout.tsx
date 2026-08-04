// src/ProtectedLayout.tsx
import { Navigate, Outlet, useLocation } from 'react-router-dom';

export function ProtectedLayout(): React.ReactElement {
  // THREAT MODEL note: see src/apollo/client.ts — JWT-in-localStorage
  // is an XSS exposure we accept until W6 wires HttpOnly cookies.
  const jwt = localStorage.getItem('uc:jwt');
  if (jwt === null) return <Navigate to="/login" replace />;

  const { pathname } = useLocation();
  const merchantsActive = pathname.startsWith('/merchants');
  const chatActive = pathname === '/chat';

  return (
    <div className="dashboard-layout">
      <nav className="sidebar" aria-label="Main navigation">
        <div className="sidebar-brand">
          <span className="sidebar-logo" aria-hidden="true">💳</span>
          <span className="sidebar-title">ExpenseVault</span>
        </div>
        <ul className="sidebar-nav">
          <li>
            <a
              href="/merchants"
              className={`sidebar-link${merchantsActive ? ' sidebar-link--active' : ''}`}
              aria-current={merchantsActive ? 'page' : undefined}
            >
              Merchants
            </a>
          </li>
          <li>
            <a
              href="/chat"
              className={`sidebar-link${chatActive ? ' sidebar-link--active' : ''}`}
              aria-current={chatActive ? 'page' : undefined}
            >
              Expense Agent
            </a>
          </li>
        </ul>
      </nav>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}

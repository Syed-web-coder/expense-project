// src/ProtectedLayout.tsx
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ChatWidget } from './components/ChatWidget';

const navItems = [
  { label: 'Merchants',     path: '/merchants' },
  { label: 'Expense Agent', path: '/chat'      },
  { label: 'Upload',        path: '/upload'    },
];

export function ProtectedLayout(): React.ReactElement {
  // THREAT MODEL note: see src/apollo/client.ts — JWT-in-localStorage
  // is an XSS exposure we accept until W6 wires HttpOnly cookies.
  const jwt = localStorage.getItem('uc:jwt');
  const { pathname } = useLocation();
  if (jwt === null) return <Navigate to="/login" replace />;

  return (
    <div className="dashboard-layout">
      <nav className="sidebar" aria-label="Main navigation">
        <div className="sidebar-brand">
          <span className="sidebar-logo" aria-hidden="true">💳</span>
          <span className="sidebar-title">ExpenseVault</span>
        </div>
        <ul className="sidebar-nav">
          {navItems.map((item) => {
            const active = pathname.startsWith(item.path);
            return (
              <li key={item.path}>
                <a
                  href={item.path}
                  className={`sidebar-link${active ? ' sidebar-link--active' : ''}`}
                  aria-current={active ? 'page' : undefined}
                >
                  {item.label}
                </a>
              </li>
            );
          })}
        </ul>
      </nav>
      <main className="main-content">
        <Outlet />
      </main>
      <ChatWidget />
    </div>
  );
}

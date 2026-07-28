// src/ProtectedLayout.tsx
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ChatWidget } from './components/ChatWidget';

const navItems = [
  { label: 'Dashboard', path: '/merchants' },
  { label: 'Upload', path: '/upload' },
];

export function ProtectedLayout(): React.ReactElement {
  // THREAT MODEL note: see src/apollo/client.ts — JWT-in-localStorage
  // is an XSS exposure we accept until W6 wires HttpOnly cookies.
  const jwt = localStorage.getItem('uc:jwt');
  const location = useLocation();
  if (jwt === null) return <Navigate to="/login" replace />;

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: '#0b0b0f' }}>
      <aside
        style={{
          width: 220,
          position: 'relative',
          background: '#111116',
          borderRight: '1px solid #26262f',
          padding: '24px 16px',
          color: '#f4f4f6',
          fontFamily: 'system-ui, sans-serif',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 32, paddingLeft: 6 }}>
          <div
            style={{
              width: 32,
              height: 32,
              borderRadius: 8,
              background: '#7c3aed',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
            }}
          >
            E
          </div>
          <span style={{ fontWeight: 600 }}>ExpenseTrack</span>
        </div>
        <nav>
          {navItems.map((item) => {
            const active = location.pathname === item.path;
            return (
              <a
                key={item.path}
                href={item.path}
                style={{
                  display: 'block',
                  padding: '10px 12px',
                  borderRadius: 8,
                  marginBottom: 4,
                  textDecoration: 'none',
                  color: active ? '#fff' : '#9a9aa8',
                  background: active ? '#7c3aed' : 'transparent',
                  fontSize: 14,
                  fontWeight: active ? 600 : 400,
                }}
              >
                {item.label}
              </a>
            );
          })}
        </nav>
        <div style={{ position: 'absolute', bottom: 24, left: 16, right: 16, display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px', borderTop: '1px solid #26262f' }}>
          <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#38bdf8', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13, color: '#0b0b0f' }}>
            NS
          </div>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600 }}>Nishi Sam</div>
            <div style={{ fontSize: 11, color: '#9a9aa8' }}>Nishi.sam@gcitsolutions.com</div>
          </div>
        </div>
      </aside>
      <main style={{ flex: 1, minWidth: 0 }}>
        <Outlet />
      </main>
      <ChatWidget />
    </div>
  );
}

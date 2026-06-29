// src/test/ProtectedLayout.test.tsx
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  createMemoryRouter,
  RouterProvider,
  Navigate,
  Outlet,
} from 'react-router-dom';

function ProtectedLayout(): React.ReactElement {
  const jwt = localStorage.getItem('uc:jwt');
  if (jwt === null) return <Navigate to="/login" replace />;
  return <Outlet />;
}

function buildRouter() {
  return createMemoryRouter(
    [
      { path: '/login', element: <div>Login page</div> },
      {
        element: <ProtectedLayout />,
        children: [{ path: '/merchants', element: <div>Merchants page</div> }],
      },
    ],
    { initialEntries: ['/merchants'] },
  );
}

describe('ProtectedLayout', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('redirects to /login when no JWT is present', () => {
    render(<RouterProvider router={buildRouter()} />);
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });

  it('renders the protected child when a JWT is present', () => {
    localStorage.setItem('uc:jwt', 'dev-fake-jwt-token');
    render(<RouterProvider router={buildRouter()} />);
    expect(screen.getByText('Merchants page')).toBeInTheDocument();
  });
});

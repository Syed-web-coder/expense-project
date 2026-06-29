// src/router.tsx
import {
  createBrowserRouter,
  Navigate,
} from 'react-router-dom';
import { ProtectedLayout } from './ProtectedLayout';
import { MerchantListPage    } from './pages/MerchantListPage';
import { MerchantDetailPage  } from './pages/MerchantDetailPage';
import { MerchantSummaryPage } from './pages/MerchantSummaryPage';
import { LoginPage             } from './pages/LoginPage';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <ProtectedLayout />,
    children: [
      { path: '/merchants',                  element: <MerchantListPage    /> },
      { path: '/merchants/:id',              element: <MerchantDetailPage  /> },
      { path: '/merchants/:id/summary',      element: <MerchantSummaryPage /> },
      { path: '/',  element: <Navigate to="/merchants" replace /> },
    ],
  },
]);

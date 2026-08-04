// src/router.tsx
import {
  createBrowserRouter,
  Navigate,
  useParams,
} from 'react-router-dom';
import { ProtectedLayout } from './ProtectedLayout';
import { MerchantListPage    } from './pages/MerchantListPage';
import { MerchantDetailPage  } from './pages/MerchantDetailPage';
import { MerchantSummaryPage } from './pages/MerchantSummaryPage';
import { MerchantChatPanel    } from './pages/MerchantChatPanel';
import { ChatPage              } from './pages/ChatPage';
import { LoginPage             } from './pages/LoginPage';

// The :id route param wasn't being read anywhere -- MerchantDetailPage
// only accepts a merchantId PROP (it can't call useParams() itself; its
// existing test suite renders it standalone with no Router context), so
// every /merchants/:id visit silently fell back to the component's
// 'stub-id-1' default regardless of which id was actually in the URL.
function MerchantDetailRoute() {
  const { id } = useParams<{ id: string }>();
  // id is always defined here: this component only renders behind a
  // matched '/merchants/:id' route. useParams()'s wider return type is
  // just React Router being conservative about routes with optional
  // segments elsewhere in the tree -- not the case for this one.
  return <MerchantDetailPage merchantId={id!} />;
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <ProtectedLayout />,
    children: [
      { path: '/merchants',                  element: <MerchantListPage    /> },
      { path: '/merchants/:id',              element: <MerchantDetailRoute /> },
      { path: '/merchants/:id/summary',      element: <MerchantSummaryPage /> },
      { path: '/merchants/:id/chat',         element: <MerchantChatPanel   /> },
      { path: '/chat',                       element: <ChatPage             /> },
      { path: '/',  element: <Navigate to="/merchants" replace /> },
    ],
  },
]);

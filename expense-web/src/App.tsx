import { ErrorBoundary } from './components/ErrorBoundary';
import { MerchantDetailPage } from './pages/MerchantDetailPage';

const MERCHANT_ROUTE = /^#\/merchants\/([^/]+)$/;

function App() {
  const match = MERCHANT_ROUTE.exec(window.location.hash);
  if (!match) {
    return <p>Navigate to <code>#/merchants/stub-id-1</code> to view the merchant detail page.</p>;
  }
  return (
    <ErrorBoundary
      fallback={(err, reset) => (
        <div role="alert" className="error-card">
          <h2>Something went wrong</h2>
          <pre>{err.message}</pre>
          <button onClick={reset}>Try again</button>
        </div>
      )}
    >
      <MerchantDetailPage merchantId={match[1] ?? 'stub-id-1'} />
    </ErrorBoundary>
  );
}

export default App;

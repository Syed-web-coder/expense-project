import { MerchantDetailPage } from './pages/MerchantDetailPage';

function App() {
  if (window.location.hash === '#/merchants/stub-id-1') {
    return <MerchantDetailPage />;
  }

  return <p>Navigate to <code>#/merchants/stub-id-1</code> to view the merchant detail page.</p>;
}

export default App;

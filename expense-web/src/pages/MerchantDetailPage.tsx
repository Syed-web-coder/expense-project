import { useState } from 'react';
import { useQuery } from '@apollo/client';
import { graphql } from '../gql/generated';
import { ThresholdSlider } from '../components/ThresholdSlider';
import { ThresholdReadout } from '../components/ThresholdReadout';
import { getMccCategory } from '../types/merchant';

export const MerchantDetailDocument = graphql(`
  query MerchantDetail($id: ID!) {
    merchant(id: $id) {
      id
      name
      mccCode
      lines {
        line
        amount
      }
    }
  }
`);

export function MerchantDetailPage({ merchantId = 'stub-id-1' }) {
  const [shouldThrow, setShouldThrow] = useState(false);

  if (shouldThrow) {
    throw new Error('Triggered test error');
  }

  const { loading, error, data } = useQuery(MerchantDetailDocument, {
    variables: { id: merchantId },
  });

  if (loading) return <div role="status" className="loading-state">Loading…</div>;
  if (error)   return <div role="alert"  className="error-state">Error: {error.message}</div>;
  if (!data?.merchant) return <p className="not-found-state">Not found.</p>;

  const merchant = data.merchant;
  const transactionCount = merchant.lines.length;
  // NOTE: LineItem.amount is a GraphQL Float, not a BigDecimal-as-string.
  // Summing floats risks precision loss on real money; flagged as a backend/
  // schema gap. toFixed(2) here is a display-only approximation.
  const totalSpend = merchant.lines.reduce((sum, l) => sum + l.amount, 0).toFixed(2);
  const category = getMccCategory(merchant.mccCode);

  return (
    <div className="merchant-detail-page">
      <a href="/merchants" className="merchant-detail-back">&larr; Back to dashboard</a>

      <div className="merchant-detail-header">
        <h1 className="merchant-detail-id">{merchant.name || merchant.id}</h1>
        {merchant.name && <p className="merchant-detail-subid">{merchant.id}</p>}
        <span className={`mcc-badge mcc-badge--${category.color}`}>{category.label}</span>
      </div>

      <div className="merchant-info-list">
        <p className="merchant-info-row">MCC Code: {merchant.mccCode}</p>
        <p className="merchant-info-row">Transaction Count: {transactionCount}</p>
        <p className="merchant-info-row merchant-info-row--accent">Total Spend: {totalSpend}</p>
      </div>

      <div className="merchant-detail-grid">
        <div className="merchant-detail-card">
          <div className="merchant-detail-section-title">Individual purchases</div>
          <table className="merchant-purchases-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {merchant.lines.map((line) => (
                <tr key={line.line}>
                  <td>{line.line}</td>
                  <td>${line.amount.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="threshold-section">
          <div className="merchant-detail-section-title">Alert threshold</div>
          <ThresholdSlider />
          <ThresholdReadout />
          {import.meta.env.DEV && (
            <button type="button" className="dev-trigger-btn" onClick={() => setShouldThrow(true)}>
              Trigger error
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

import { useState } from 'react';
import { useQuery } from '@apollo/client';
import { graphql } from '../gql/generated';
import { FilterStrip } from '../components/FilterStrip';
import { useDebouncedSearch } from '../hooks/useDebouncedSearch';
import { ThresholdSlider } from '../components/ThresholdSlider';
import { ThresholdReadout } from '../components/ThresholdReadout';
import { getMccCategory } from '../types/merchant';

export const MerchantDetailDocument = graphql(`
  query MerchantDetail($id: ID!) {
    merchant(id: $id) {
      id
      mccCode
      lines {
        line
        amount
      }
    }
  }
`);

type Props = {
  readonly merchantId?: string;
};

export function MerchantDetailPage({ merchantId = 'stub-id-1' }: Props) {
  const debouncedSearch = useDebouncedSearch();
  const [shouldThrow, setShouldThrow] = useState(false);

  if (shouldThrow) {
    throw new Error('Triggered test error');
  }

  const { loading, error, data } = useQuery(MerchantDetailDocument, {
    variables: { id: merchantId },
  });

  if (loading) return <div role="status" className="loading-state">Loading…</div>;
  if (error) return <div role="alert" className="error-state">Error: {error.message}</div>;
  if (!data?.merchant) return <p className="not-found-state">Not found.</p>;

  const { merchant } = data;
  const transactionCount = merchant.lines.length;
  // NOTE: LineItem.amount is a GraphQL Float, not a BigDecimal-as-string,
  // despite the convention documented in types/merchant.ts ("money is
  // never represented as a JS number"). Summing floats risks precision
  // loss on real money; flagged as a real backend/schema gap rather than
  // silently worked around. toFixed(2) here is a display-only approximation.
  const totalSpend = merchant.lines.reduce((sum, l) => sum + l.amount, 0).toFixed(2);
  const category = getMccCategory(merchant.mccCode);

  return (
    <div className="merchant-detail-page">
      <FilterStrip />
      <p className="filter-search-text">filtering for: '{debouncedSearch}'</p>
      <div className="merchant-detail-card">
        <div className="merchant-detail-header">
          <div>
            <h1 className="merchant-detail-id">{merchant.id}</h1>
            <span className={`mcc-badge mcc-badge--${category.color}`}>{category.label}</span>
          </div>
        </div>
        <div className="merchant-info-list">
          <p className="merchant-info-row">MCC Code: {merchant.mccCode}</p>
          <p className="merchant-info-row">Transaction Count: {transactionCount}</p>
          <p className="merchant-info-row merchant-info-row--accent">Total Spend: {totalSpend}</p>
        </div>
        <div className="threshold-section">
          <ThresholdSlider />
          <ThresholdReadout />
        </div>
        {import.meta.env.DEV && (
          <button type="button" className="dev-trigger-btn" onClick={() => setShouldThrow(true)}>
            Trigger error
          </button>
        )}
      </div>
    </div>
  );
}

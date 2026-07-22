import { useState } from 'react';
import { useQuery } from '@apollo/client';
import { graphql } from '../gql/generated';
import { FilterStrip } from '../components/FilterStrip';
import { useDebouncedSearch } from '../hooks/useDebouncedSearch';
import { ThresholdSlider } from '../components/ThresholdSlider';
import { ThresholdReadout } from '../components/ThresholdReadout';

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

  if (loading) return <div role="status">Loading…</div>;
  if (error) return <div role="alert">Error: {error.message}</div>;
  if (!data?.merchant) return <p>Not found.</p>;

  const { merchant } = data;
  const transactionCount = merchant.lines.length;
  // NOTE: LineItem.amount is a GraphQL Float, not a BigDecimal-as-string,
  // despite the convention documented in types/merchant.ts ("money is
  // never represented as a JS number"). Summing floats risks precision
  // loss on real money; flagged as a real backend/schema gap rather than
  // silently worked around. toFixed(2) here is a display-only approximation.
  const totalSpend = merchant.lines.reduce((sum, l) => sum + l.amount, 0).toFixed(2);

  return (
    <div>
      <FilterStrip />
      <p>filtering for: '{debouncedSearch}'</p>
      <h1>{merchant.id}</h1>
      <p>MCC Code: {merchant.mccCode}</p>
      <p>Transaction Count: {transactionCount}</p>
      <p>Total Spend: {totalSpend}</p>
      <ThresholdSlider />
      <ThresholdReadout />
      {import.meta.env.DEV && (
        <button type="button" onClick={() => setShouldThrow(true)}>
          Trigger error
        </button>
      )}
    </div>
  );
}

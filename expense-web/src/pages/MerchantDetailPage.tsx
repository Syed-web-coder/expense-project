import { useEffect, useReducer, useState } from 'react';
import { fetchMerchant } from '../hooks/useMerchant';
import { useDebouncedSearch } from '../hooks/useDebouncedSearch';
import { FilterStrip } from '../components/FilterStrip';
import { ThresholdSlider } from '../components/ThresholdSlider';
import { ThresholdReadout } from '../components/ThresholdReadout';
import { detailReducer, INITIAL_DETAIL_STATE } from './MerchantDetailPage.reducer';

type Props = {
  readonly merchantId?: string;
};

export function MerchantDetailPage({ merchantId = 'stub-id-1' }: Props) {
  const [state, dispatch] = useReducer(detailReducer, INITIAL_DETAIL_STATE);
  const debouncedSearch = useDebouncedSearch();
  const [shouldThrow, setShouldThrow] = useState(false);

  if (shouldThrow) {
    throw new Error('Triggered test error');
  }

  useEffect(() => {
    let cancelled = false;
    dispatch({ type: 'fetch/start' });
    fetchMerchant(merchantId)
      .then((data) => {
        if (!cancelled) dispatch({ type: 'fetch/success', payload: data });
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          dispatch({ type: 'fetch/error', error: err instanceof Error ? err.message : String(err) });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [merchantId]);

  if (state.status === 'idle' || state.status === 'loading') return <p>Loading…</p>;
  if (state.status === 'error') return <p>Error: {state.error}</p>;
  if (state.status === 'empty') return <p>Not found.</p>;

  const { data } = state;
  return (
    <div>
      <FilterStrip />
      <p>filtering for: '{debouncedSearch}'</p>
      <h1>{data.id}</h1>
      <p>MCC Code: {data.mccCode}</p>
      <p>Transaction Count: {data.transactionCount}</p>
      <p>Total Spend: {data.totalSpend}</p>
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

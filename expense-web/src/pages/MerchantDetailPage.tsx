import { useState } from 'react';
import { useMerchant } from '../hooks/useMerchant';
import { ThresholdSlider } from '../components/ThresholdSlider';
import { ThresholdReadout } from '../components/ThresholdReadout';

export function MerchantDetailPage() {
  const { data, loading, error } = useMerchant('stub-id-1');
  const [threshold, setThreshold] = useState<number>(50);

  if (loading) return <p>Loading…</p>;
  if (error) return <p>Error: {error}</p>;
  if (!data) return <p>No data.</p>;

  return (
    <div>
      <h1>{data.id}</h1>
      <p>MCC Code: {data.mccCode}</p>
      <p>Transaction Count: {data.transactionCount}</p>
      <p>Total Spend: {data.totalSpend}</p>
      <ThresholdSlider value={threshold} onChange={setThreshold} />
      <ThresholdReadout value={threshold} />
    </div>
  );
}

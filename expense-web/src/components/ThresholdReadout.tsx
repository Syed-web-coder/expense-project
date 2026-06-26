import { useMerchantFilterStore } from '../stores/useMerchantFilterStore';

export function ThresholdReadout() {
  const threshold = useMerchantFilterStore((s) => s.threshold);
  return <div role="status">Threshold: {threshold}%</div>;
}

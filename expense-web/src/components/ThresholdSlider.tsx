import { useMerchantFilterStore } from '../stores/useMerchantFilterStore';

export function ThresholdSlider() {
  const threshold = useMerchantFilterStore((s) => s.threshold);
  const setThreshold = useMerchantFilterStore((s) => s.setThreshold);
  return (
    <label className="threshold-label">
      Threshold
      <input
        type="range"
        className="threshold-slider"
        min={0}
        max={100}
        value={threshold}
        onChange={(e) => setThreshold(Number(e.currentTarget.value))}
      />
    </label>
  );
}

type Props = {
  readonly value: number;
};

export function ThresholdReadout({ value }: Props) {
  return <div role="status">Threshold: {value}%</div>;
}

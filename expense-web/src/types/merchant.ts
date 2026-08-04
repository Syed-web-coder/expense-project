// amount is BigDecimal-as-string, scale 2 — money is never represented as a JS number
export type MerchantLine = {
  readonly id: string;
  readonly amount: string;
};

export type Merchant = {
  readonly id: string;
  readonly mccCode: string;
  readonly transactionCount: number;
  readonly totalSpend: string;
  readonly lines: ReadonlyArray<MerchantLine>;
};

export type MccColor = 'indigo' | 'green' | 'yellow' | 'red' | 'default';

export type MccCategory = {
  readonly label: string;
  readonly color: MccColor;
};

const MCC_MAP: Readonly<Record<string, MccCategory>> = {
  '5411': { label: 'Grocery', color: 'green' },
  '5421': { label: 'Grocery', color: 'green' },
  '5499': { label: 'Grocery', color: 'green' },
  '5812': { label: 'Restaurant', color: 'yellow' },
  '5814': { label: 'Fast Food', color: 'yellow' },
  '4121': { label: 'Taxi / Rideshare', color: 'indigo' },
  '4111': { label: 'Transit', color: 'indigo' },
  '4131': { label: 'Bus Lines', color: 'indigo' },
  '7393': { label: 'Business Services', color: 'indigo' },
  '5943': { label: 'Office Supplies', color: 'indigo' },
  '4911': { label: 'Utilities', color: 'red' },
  '4814': { label: 'Telecom', color: 'red' },
};

export function getMccCategory(code: string | null | undefined): MccCategory {
  if (!code) return { label: 'Unknown', color: 'default' };
  return MCC_MAP[code] ?? { label: `MCC ${code}`, color: 'default' };
}

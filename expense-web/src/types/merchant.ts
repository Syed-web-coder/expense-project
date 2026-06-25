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

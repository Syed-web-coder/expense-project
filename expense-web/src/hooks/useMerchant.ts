import type { Merchant } from '../types/merchant';

/**
 * Stub data source for W4 D2 — fetches from public/mocks/merchant.json.
 * Real Apollo Client query lands on W4 D3.
 * Only "stub-id-1" resolves to data; any other id resolves to null so the
 * reducer's fetch/success(null) -> 'empty' branch is reachable.
 */
export async function fetchMerchant(id: string): Promise<Merchant | null> {
  if (id !== 'stub-id-1') return null;
  const res = await fetch('/mocks/merchant.json');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return (await res.json()) as Merchant;
}

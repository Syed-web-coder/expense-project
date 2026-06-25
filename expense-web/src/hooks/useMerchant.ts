import { useState, useEffect } from 'react';
import type { Merchant } from '../types/merchant';

type State =
  | { status: 'loading' }
  | { status: 'ok'; data: Merchant }
  | { status: 'error'; error: string };

/**
 * Stub data source for W4 D1 — fetches from public/mocks/merchant.json.
 * Real Apollo Client query lands on W4 D3.
 * Only "stub-id-1" returns data today.
 */
export function useMerchant(id: string): { data: Merchant | null; loading: boolean; error: string | null } {
  const [state, setState] = useState<State>({ status: 'loading' });

  useEffect(() => {
    let cancelled = false;

    fetch('/mocks/merchant.json')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<Merchant>;
      })
      .then((data) => {
        if (!cancelled) setState({ status: 'ok', data });
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({ status: 'error', error: err instanceof Error ? err.message : String(err) });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  if (state.status === 'ok') return { data: state.data, loading: false, error: null };
  if (state.status === 'error') return { data: null, loading: false, error: state.error };
  return { data: null, loading: true, error: null };
}

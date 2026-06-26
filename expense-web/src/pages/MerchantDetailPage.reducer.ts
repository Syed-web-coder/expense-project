import type { Merchant } from '../types/merchant';

export type DetailState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: Merchant }
  | { status: 'error'; error: string }
  | { status: 'empty' };

export type DetailAction =
  | { type: 'fetch/start' }
  | { type: 'fetch/success'; payload: Merchant | null }
  | { type: 'fetch/error'; error: string }
  | { type: 'reset' };

export const INITIAL_DETAIL_STATE: DetailState = { status: 'idle' };

export function detailReducer(state: DetailState, action: DetailAction): DetailState {
  switch (action.type) {
    case 'fetch/start':
      return { status: 'loading' };
    case 'fetch/success':
      return action.payload === null
        ? { status: 'empty' }
        : { status: 'success', data: action.payload };
    case 'fetch/error':
      return { status: 'error', error: action.error };
    case 'reset':
      return INITIAL_DETAIL_STATE;
    default: {
      const _exhaustive: never = action;
      void _exhaustive;
      return state;
    }
  }
}

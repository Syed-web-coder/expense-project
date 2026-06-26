import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useMerchantFilterStore } from '../stores/useMerchantFilterStore';
import { useDebouncedSearch } from '../hooks/useDebouncedSearch';

beforeEach(() => {
  vi.useFakeTimers();
  useMerchantFilterStore.setState(useMerchantFilterStore.getInitialState(), true);
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useDebouncedSearch', () => {
  it('lags the source by delayMs', () => {
    const { result } = renderHook(() => useDebouncedSearch(300));
    expect(result.current).toBe('');
    act(() => useMerchantFilterStore.getState().setSearchText('foo'));
    act(() => {
      vi.advanceTimersByTime(299);
    });
    expect(result.current).toBe('');
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe('foo');
  });

  it('cancels the pending write when the source changes again', () => {
    const { result } = renderHook(() => useDebouncedSearch(300));
    act(() => useMerchantFilterStore.getState().setSearchText('foo'));
    act(() => {
      vi.advanceTimersByTime(100);
    });
    act(() => useMerchantFilterStore.getState().setSearchText('foobar'));
    act(() => {
      vi.advanceTimersByTime(299);
    });
    expect(result.current).toBe('');
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe('foobar');
  });
});

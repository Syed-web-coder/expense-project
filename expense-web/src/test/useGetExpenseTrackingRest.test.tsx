// src/test/useGetExpenseTrackingRest.test.tsx
import { describe, it, expect } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useGetExpenseTrackingRest } from '../hooks/useGetExpenseTrackingRest';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe('useGetExpenseTrackingRest', () => {
  it('returns merchant data once the MSW REST handler resolves', async () => {
    const { result } = renderHook(() => useGetExpenseTrackingRest('stub-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.mccCode).toBe('5411');
  });

  it('does not fetch when id is empty', () => {
    const { result } = renderHook(() => useGetExpenseTrackingRest(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
  });

  it('starts in a loading state before the MSW handler resolves', () => {
    const { result } = renderHook(() => useGetExpenseTrackingRest('stub-1'), {
      wrapper: createWrapper(),
    });

    expect(result.current.isLoading).toBe(true);
  });
});

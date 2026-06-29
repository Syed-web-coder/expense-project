// src/hooks/useGetExpenseTrackingRest.ts
import { useQuery } from '@tanstack/react-query';

export type MerchantRest = {
  readonly id: string;
  readonly mccCode: string;
  readonly capturedAt: string;
};

export function useGetExpenseTrackingRest(id: string) {
  return useQuery<MerchantRest>({
    queryKey: ['expense', id],
    enabled: Boolean(id),
    queryFn: async () => {
      const res = await fetch(`http://localhost:8080/api/v1/merchants/${id}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return (await res.json()) as MerchantRest;
    },
  });
}

import { tool } from 'ai';
import { z } from 'zod';

export const merchantTools = {
  lookupMerchant: tool({
    description:
      'Look up a single merchant by id. ' +
      'Returns the canonical record stored in the W3 D2 REST backend.',
    inputSchema: z.object({
      id: z.string(),
    }),
    execute: async ({ id }) => {
      const res = await fetch(`http://localhost:8080/api/v1/merchants/${id}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    },
  }),
  classifyDeduction: tool({
    description:
      'Search the merchant corpus by merchantId. ' +
      'Returns a small array the assistant can quote inline.',
    inputSchema: z.object({
      merchantId: z.string(),
    }),
    execute: async ({ merchantId }) => {
      const res = await fetch(
        `http://localhost:8080/api/v1/merchants?merchantId=${merchantId}`,
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    },
  }),
};

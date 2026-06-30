// src/test/handlers.ts
import { graphql, http, HttpResponse } from 'msw';
import { sseHandlers } from './sse-handlers';

export const handlers = [
  ...sseHandlers,
  graphql.query('LatestMerchants', () =>
    HttpResponse.json({
      data: {
        latestMerchants: [
          { id: 'stub-1', mccCode: '5411', capturedAt: '2025-01-01T00:00:00Z', lines: [{ line: 1, amount: 10.5 }] },
          { id: 'stub-2', mccCode: '4111', capturedAt: '2025-01-02T00:00:00Z', lines: [{ line: 1, amount: 20.0 }] },
          { id: 'stub-3', mccCode: '4911', capturedAt: '2025-01-03T00:00:00Z', lines: [{ line: 1, amount: 30.0 }] },
        ],
      },
    }),
  ),
  graphql.mutation('SummarizeMerchant', () =>
    HttpResponse.json({
      data: {
        summarizeMerchant: {
          mccCode: '5411',
          totalSpend: 100.0,
          transactionCount: 5,
          primaryCategory: 'Groceries',
          confidence: 'HIGH',
          tokensIn: 50,
          tokensOut: 20,
        },
      },
    }),
  ),
  http.get('http://localhost:8080/api/v1/merchants/:id', ({ params }) =>
    HttpResponse.json({
      id: String(params.id),
      mccCode: '5411',
      capturedAt: '2025-01-04T00:00:00Z',
    }),
  ),
  http.get('/api/v1/merchants', () =>
    HttpResponse.json([{ id: 'stub-1', mccCode: '5411', capturedAt: '2025-01-01T00:00:00Z' }]),
  ),
];

export const merchantErrorHandler = http.get('/api/v1/merchants', () =>
  HttpResponse.json({ error: 'boom' }, { status: 500 }),
);

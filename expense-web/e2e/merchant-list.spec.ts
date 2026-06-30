import { test, expect } from '@playwright/test';

test.describe('ExpenseTracking W4 capstone happy-path', () => {
  test('merchant list page loads and shows merchants', async ({ page }) => {
    // Intercept GraphQL requests — no real backend runs during E2E.
    await page.route('http://localhost:8080/graphql', async (route) => {
      const body = route.request().postDataJSON() as { operationName?: string } | null;
      if (body?.operationName === 'LatestMerchants') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: {
              latestMerchants: [
                { id: 'stub-1', mccCode: '5411', capturedAt: '2025-01-01T00:00:00Z', lines: [{ line: 1, amount: 10.5 }] },
                { id: 'stub-2', mccCode: '4111', capturedAt: '2025-01-02T00:00:00Z', lines: [{ line: 1, amount: 20.0 }] },
                { id: 'stub-3', mccCode: '4911', capturedAt: '2025-01-03T00:00:00Z', lines: [{ line: 1, amount: 30.0 }] },
              ],
            },
          }),
        });
      } else {
        await route.continue();
      }
    });

    // ProtectedLayout reads 'uc:jwt' (src/ProtectedLayout.tsx:7).
    await page.addInitScript(() => {
      window.localStorage.setItem('uc:jwt', 'dev-fake-jwt-token');
    });

    await page.goto('/merchants');
    await expect(page.getByRole('list', { name: /merchant-list/i })).toBeVisible({ timeout: 10000 });
    const items = page.getByRole('listitem');
    await expect(items.first()).toBeVisible();
  });
});

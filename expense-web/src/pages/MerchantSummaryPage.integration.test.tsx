import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import type { MockedResponse } from '@apollo/client/testing';
import { renderWithProviders } from '../test/renderWithProviders';
import { MerchantListPage } from './MerchantListPage';
import { MerchantSummaryPage } from './MerchantSummaryPage';
import { LatestMerchantsDocument, SummarizeMerchantDocument } from '../gql/generated/graphql';

const latestMerchantsMock: MockedResponse = {
  request: { query: LatestMerchantsDocument, variables: {} },
  result: {
    data: {
      latestMerchants: [
        { id: 'stub-1', mccCode: '5411', capturedAt: '2025-01-01T00:00:00Z', lines: [{ line: 1, amount: 10.5 }] },
        { id: 'stub-2', mccCode: '4111', capturedAt: '2025-01-02T00:00:00Z', lines: [{ line: 1, amount: 20.0 }] },
        { id: 'stub-3', mccCode: '4911', capturedAt: '2025-01-03T00:00:00Z', lines: [{ line: 1, amount: 30.0 }] },
      ],
    },
  },
};

const summarizeMock: MockedResponse = {
  request: { query: SummarizeMerchantDocument, variables: { id: 'stub-1' } },
  result: {
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
  },
};

const summarizeErrorMock = {
  request: { query: SummarizeMerchantDocument, variables: { id: 'stub-1' } },
  result: { errors: [{ message: 'Something went wrong' }] },
} as MockedResponse;

function renderSummaryPage(apolloMocks: MockedResponse[] = []) {
  return renderWithProviders(
    <Routes>
      <Route path="/merchants/:id" element={<MerchantSummaryPage />} />
    </Routes>,
    { route: '/merchants/stub-1', apolloMocks },
  );
}

describe('MerchantListPage - MSW integration', () => {
  it('renders all merchants from the GraphQL MSW handler', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    const items = await screen.findAllByRole('listitem');
    expect(items.length).toBeGreaterThanOrEqual(1);
  });

  it('renders mccCode from MSW stub data', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    await screen.findAllByRole('listitem');
    expect(screen.getByText(/5411/)).toBeInTheDocument();
  });

  it('renders links to merchant detail pages', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    await screen.findAllByRole('listitem');
    const links = screen.getAllByRole('link');
    expect(links[0]).toHaveAttribute('href', expect.stringContaining('/merchants/'));
  });

  it('shows loading state before MSW resolves', () => {
    renderWithProviders(<MerchantListPage />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});

describe('MerchantSummaryPage - MSW integration', () => {
  it('renders the summarize button', () => {
    renderSummaryPage();
    expect(screen.getByRole('button', { name: /summarize/i })).toBeInTheDocument();
  });

  it('button is enabled before any click', () => {
    renderSummaryPage();
    expect(screen.getByRole('button', { name: /summarize/i })).not.toBeDisabled();
  });

  it('shows summary data after successful mutation', async () => {
    const { user } = renderSummaryPage([summarizeMock]);
    await user.click(screen.getByRole('button', { name: /summarize/i }));
    expect(await screen.findByText(/groceries/i)).toBeInTheDocument();
  });

  it('shows error alert when mutation fails', async () => {
    const { user } = renderSummaryPage([summarizeErrorMock]);
    await user.click(screen.getByRole('button', { name: /summarize/i }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});

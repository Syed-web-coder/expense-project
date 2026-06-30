import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { axe } from 'jest-axe';
import { renderWithProviders } from '../test/renderWithProviders';
import { MerchantListPage } from './MerchantListPage';
import { LatestMerchantsDocument } from '../gql/generated/graphql';

const latestMerchantsMock = {
  request: {
    query: LatestMerchantsDocument,
    variables: {},
  },
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

describe('MerchantListPage - component contract', () => {
  it('renders the merchant list container', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    expect(await screen.findByRole('list', { name: /merchant-list/i })).toBeInTheDocument();
  });

  it('shows loading skeleton while query is in-flight', () => {
    renderWithProviders(<MerchantListPage />);
    expect(screen.getByRole('status')).toHaveTextContent(/loading/i);
  });

  it('renders merchant items once query resolves', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toBeInTheDocument();
  });

  it('renders all three stub merchants from MSW handler', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(3);
  });

  it('each merchant item contains a link', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    await screen.findAllByRole('listitem');
    const links = screen.getAllByRole('link');
    expect(links.length).toBeGreaterThanOrEqual(3);
  });

  it('first merchant link points to the correct merchant route', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    await screen.findAllByRole('listitem');
    const links = screen.getAllByRole('link');
    expect(links[0]).toHaveAttribute('href', '/merchants/stub-1');
  });

  it('renders mccCode in the merchant label', async () => {
    renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    await screen.findAllByRole('listitem');
    expect(screen.getByText(/5411/i)).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(<MerchantListPage />, { apolloMocks: [latestMerchantsMock] });
    await screen.findAllByRole('listitem');
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});

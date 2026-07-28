import { describe, it, expect } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { renderWithProviders } from './renderWithProviders';
import { MerchantDetailPage, MerchantDetailDocument } from '../pages/MerchantDetailPage';

// LineItem.amount is a GraphQL Float in the real schema (see the NOTE in
// MerchantDetailPage.tsx) -- these mock amounts intentionally sum to a
// value with more than 2 decimal places (10.1 + 20.2 = 30.299999999999997
// in raw JS float arithmetic) so the toFixed(2) display-rounding path is
// actually exercised, not just the happy-path integers.
const merchantDetailMock = {
  request: {
    query: MerchantDetailDocument,
    variables: { id: 'stub-id-1' },
  },
  result: {
    data: {
      merchant: {
        id: 'stub-id-1',
        mccCode: '5943',
        lines: [
          { line: 1, amount: 10.1 },
          { line: 2, amount: 20.2 },
        ],
      },
    },
  },
};

describe('MerchantDetailPage', () => {
  it('renders entity id and a sample field from the mock JSON', async () => {
    renderWithProviders(<MerchantDetailPage />, { apolloMocks: [merchantDetailMock] });
    await waitFor(() => expect(screen.getByRole('heading')).toHaveTextContent('stub-id-1'));
    expect(screen.getByText(/5943/)).toBeInTheDocument();
  });

  it('sums line amounts into Total Spend, rounded for display', async () => {
    renderWithProviders(<MerchantDetailPage />, { apolloMocks: [merchantDetailMock] });
    await waitFor(() => screen.getByRole('heading'));
    expect(screen.getByText(/Total Spend: 30\.30/)).toBeInTheDocument();
  });

  it('updates the readout when the slider is moved (lifted state)', async () => {
    renderWithProviders(<MerchantDetailPage />, { apolloMocks: [merchantDetailMock] });
    await waitFor(() => screen.getByRole('heading'));
    const slider = screen.getByLabelText(/Threshold/i);
    fireEvent.change(slider, { target: { value: '51' } });
    expect(screen.getByRole('status')).toHaveTextContent(/Threshold:\s*51%/);
  });

  it('shows "Not found." when the query resolves to a null merchant', async () => {
    const notFoundMock = {
      request: { query: MerchantDetailDocument, variables: { id: 'missing-id' } },
      result: { data: { merchant: null } },
    };
    renderWithProviders(<MerchantDetailPage merchantId="missing-id" />, {
      apolloMocks: [notFoundMock],
    });
    await waitFor(() => expect(screen.getByText('Not found.')).toBeInTheDocument());
  });
});

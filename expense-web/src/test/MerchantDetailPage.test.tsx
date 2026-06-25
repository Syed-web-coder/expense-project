import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MerchantDetailPage } from '../pages/MerchantDetailPage';

const MOCK = {
  id: 'stub-id-1',
  mccCode: '5943',
  transactionCount: 47,
  totalSpend: '3120.50',
  lines: [
    { id: 'line-1', amount: '100.00' },
    { id: 'line-2', amount: '250.00' },
  ],
};

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify(MOCK)))));
});

describe('MerchantDetailPage', () => {
  it('renders entity id and a sample field from the mock JSON', async () => {
    render(<MerchantDetailPage />);
    await waitFor(() => expect(screen.getByRole('heading')).toHaveTextContent('stub-id-1'));
    expect(screen.getByText(/5943/)).toBeInTheDocument();
  });

  it('updates the readout when the slider is moved (lifted state)', async () => {
    render(<MerchantDetailPage />);
    await waitFor(() => screen.getByRole('heading'));
    const slider = screen.getByLabelText(/Threshold/i);
    fireEvent.change(slider, { target: { value: '51' } });
    expect(screen.getByRole('status')).toHaveTextContent(/Threshold:\s*51%/);
  });
});

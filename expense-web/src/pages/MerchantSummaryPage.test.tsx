import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { axe } from 'jest-axe';
import { renderWithProviders } from '../test/renderWithProviders';
import { MerchantSummaryPage } from './MerchantSummaryPage';

describe('MerchantSummaryPage - component contract', () => {
  it('renders the Summarize button', () => {
    renderWithProviders(<MerchantSummaryPage />, { route: '/merchants/stub-1' });
    expect(screen.getByRole('button', { name: /summarize/i })).toBeInTheDocument();
  });

  it('button is enabled before any click', () => {
    renderWithProviders(<MerchantSummaryPage />, { route: '/merchants/stub-1' });
    expect(screen.getByRole('button', { name: /summarize/i })).not.toBeDisabled();
  });

  it('has no axe accessibility violations on initial render', async () => {
    const { container } = renderWithProviders(<MerchantSummaryPage />, { route: '/merchants/stub-1' });
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});

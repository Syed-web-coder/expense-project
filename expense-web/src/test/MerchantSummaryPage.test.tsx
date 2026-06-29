// src/test/MerchantSummaryPage.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApolloClient, ApolloProvider, InMemoryCache } from '@apollo/client';
import { MemoryRouter } from 'react-router-dom';
import { MerchantSummaryPage } from '../pages/MerchantSummaryPage';

describe('MerchantSummaryPage', () => {
  it('shows the summary once the mutation resolves', async () => {
    const client = new ApolloClient({
      uri: 'http://localhost:8080/graphql',
      cache: new InMemoryCache(),
    });

    render(
      <ApolloProvider client={client}>
        <MemoryRouter initialEntries={['/merchants/stub-1/summary']}>
          <MerchantSummaryPage />
        </MemoryRouter>
      </ApolloProvider>,
    );

    await userEvent.click(screen.getByRole('button', { name: /summarize/i }));

    await waitFor(() =>
      expect(screen.getByText(/Groceries/)).toBeInTheDocument(),
    );
  });
});

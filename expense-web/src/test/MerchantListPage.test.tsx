// src/test/MerchantListPage.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { ApolloProvider } from '@apollo/client';
import { MemoryRouter } from 'react-router-dom';
import { apolloClient } from '../apollo/client';
import { MerchantListPage } from '../pages/MerchantListPage';

describe('MerchantListPage', () => {
  it('renders three rows once the MSW handler resolves', async () => {
    render(
      <ApolloProvider client={apolloClient}>
        <MemoryRouter>
          <MerchantListPage />
        </MemoryRouter>
      </ApolloProvider>,
    );

    await waitFor(() =>
      expect(screen.getAllByRole('listitem')).toHaveLength(3),
    );
    expect(screen.getByText(/5411/)).toBeInTheDocument();
  });
});

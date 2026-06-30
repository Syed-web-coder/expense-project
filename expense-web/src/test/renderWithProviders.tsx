import type { ReactElement } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MockedProvider, type MockedResponse } from '@apollo/client/testing';

interface ProviderOptions {
  readonly route?: string;
  readonly apolloMocks?: ReadonlyArray<MockedResponse>;
  readonly queryClient?: QueryClient;
}

export function renderWithProviders(
  ui: ReactElement,
  opts: ProviderOptions & Omit<RenderOptions, 'wrapper'> = {},
) {
  const {
    route = '/merchants',
    apolloMocks = [],
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 } },
    }),
    ...rtl
  } = opts;

  const user = userEvent.setup();
  const utils = render(ui, {
    wrapper: ({ children }) => (
      <MockedProvider mocks={apolloMocks} addTypename={false}>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
        </QueryClientProvider>
      </MockedProvider>
    ),
    ...rtl,
  });

  return { user, queryClient, ...utils };
}

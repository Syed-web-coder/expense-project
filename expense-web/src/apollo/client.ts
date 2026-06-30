// src/apollo/client.ts
import {
  ApolloClient,
  InMemoryCache,
  HttpLink,
  from,
} from '@apollo/client';
import { setContext } from '@apollo/client/link/context';

// THREAT MODEL: storing the JWT in localStorage exposes it to any XSS
// that runs on the page. We accept that today because the W6 cookie
// story (HttpOnly, SameSite=Strict, server-set) isn't built yet —
// see §9 Sticking Points.
const httpLink = new HttpLink({ uri: 'http://localhost:8080/graphql' });

const JWT_PATTERN = /^[^.]+\.[^.]+\.[^.]+$/;

const authLink = setContext((_op, { headers }: { headers?: Record<string, string> }) => {
  const token = localStorage.getItem('uc:jwt');
  const isValidJwt = token !== null && JWT_PATTERN.test(token);
  return {
    headers: {
      ...headers,
      ...(isValidJwt ? { authorization: `Bearer ${token}` } : {}),
    },
  };
});

export const apolloClient = new ApolloClient({
  link: from([authLink, httpLink]),
  cache: new InMemoryCache({
    typePolicies: {
      Merchant: { keyFields: ['id'] },
    },
  }),
});

import { useQuery } from '@apollo/client';
import { graphql } from '../gql/generated';

const LatestMerchantsDocument = graphql(`
  query LatestMerchants {
    latestMerchants(limit: 20) {
      id
      mccCode
      capturedAt
      lines {
        line
        amount
      }
    }
  }
`);

export function MerchantListPage() {
  const { loading, error, data } = useQuery(LatestMerchantsDocument);

  if (loading) {
    return <div role="status">Loading merchants…</div>;
  }

  if (error) {
    return <div role="alert">Error: {error.message}</div>;
  }

  return (
    <ul aria-label="merchant-list">
      {data?.latestMerchants.map((merchant) => (
        <li key={merchant.id}>
          <a href={`/merchants/${merchant.id}`}>
            {merchant.mccCode} — captured {merchant.capturedAt}
          </a>
        </li>
      ))}
    </ul>
  );
}

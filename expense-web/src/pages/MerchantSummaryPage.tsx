import { useMutation } from '@apollo/client';
import { useParams } from 'react-router-dom';
import { graphql } from '../gql/generated';

const SummarizeMerchantDocument = graphql(`
  mutation SummarizeMerchant($id: ID!) {
    summarizeMerchant(id: $id) {
      mccCode
      totalSpend
      transactionCount
      primaryCategory
      confidence
      tokensIn
      tokensOut
    }
  }
`);

export function MerchantSummaryPage() {
  const { id } = useParams<{ id: string }>();
  const [summarize, { data, loading, error }] = useMutation(SummarizeMerchantDocument, {
    optimisticResponse: {
      summarizeMerchant: {
        mccCode: '…',
        totalSpend: 0,
        transactionCount: 0,
        primaryCategory: 'Thinking…',
        confidence: 'MEDIUM',
        tokensIn: 0,
        tokensOut: 0,
      },
    },
  });

  const summary = data?.summarizeMerchant;

  return (
    <div>
      <button onClick={() => { summarize({ variables: { id: id! } }).catch(() => {}); }} disabled={loading}>
        Summarize
      </button>
      {error && <div role="alert">Error: {error.message}</div>}
      {summary && (
        <div>
          <p>Category: {summary.primaryCategory}</p>
          <p>Total spend: {summary.totalSpend}</p>
          <p>Transactions: {summary.transactionCount}</p>
          <p>Confidence: {summary.confidence}</p>
        </div>
      )}
    </div>
  );
}

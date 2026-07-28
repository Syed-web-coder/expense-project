import { useState } from 'react';
import { useQuery } from '@apollo/client';
import { graphql } from '../gql/generated';
import { ThresholdSlider } from '../components/ThresholdSlider';
import { ThresholdReadout } from '../components/ThresholdReadout';

const MCC_CATEGORIES: Record<string, string> = {
  '4111': 'Transportation',
  '4911': 'Utilities',
  '4121': 'Rideshare',
  '5411': 'Grocery Stores',
  '5812': 'Restaurants',
  '7393': 'Security Services',
  '7922': 'Streaming / Entertainment',
};

const card = {
  background: '#15151c',
  border: '1px solid #26262f',
  borderRadius: 14,
  padding: '20px 22px',
};

const label = {
  fontSize: 13,
  color: '#9a9aa8',
  marginBottom: 6,
};

const bigNumber = {
  fontSize: 28,
  fontWeight: 600,
  color: '#f4f4f6',
};

export const MerchantDetailDocument = graphql(`
  query MerchantDetail($id: ID!) {
    merchant(id: $id) {
      id
      name
      mccCode
      lines {
        line
        amount
      }
    }
  }
`);

export function MerchantDetailPage({ merchantId = 'stub-id-1' }) {
  const [shouldThrow, setShouldThrow] = useState(false);

  if (shouldThrow) {
    throw new Error('Triggered test error');
  }

  const { loading, error, data } = useQuery(MerchantDetailDocument, {
    variables: { id: merchantId },
  });

  if (loading) {
    return <div role="status" style={{ color: '#f4f4f6', padding: 32 }}>Loading…</div>;
  }
  if (error) {
    return <div role="alert" style={{ color: '#f87171', padding: 32 }}>Error: {error.message}</div>;
  }
  if (!data || !data.merchant) {
    return <p style={{ color: '#f4f4f6', padding: 32 }}>Not found.</p>;
  }

  const merchant = data.merchant;
  const transactionCount = merchant.lines.length;
  const totalSpend = merchant.lines.reduce(function (sum, l) { return sum + l.amount; }, 0).toFixed(2);
  const category = merchant.mccCode && MCC_CATEGORIES[merchant.mccCode]
    ? MCC_CATEGORIES[merchant.mccCode]
    : null;

  return (
    <div style={{ background: '#0b0b0f', minHeight: '100vh', color: '#f4f4f6', padding: '32px 40px', fontFamily: 'system-ui, sans-serif' }}>
      <a href="/merchants" style={{ color: '#a78bfa', fontSize: 13, textDecoration: 'none' }}>&larr; Back to dashboard</a>

      <h1 style={{ fontSize: 26, fontWeight: 700, marginTop: 12, marginBottom: 4 }}>
        {merchant.name || merchant.id}
      </h1>
      <p style={{ color: '#5f5f6c', fontSize: 12, marginTop: 0, marginBottom: 28 }}>{merchant.id}</p>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0,1fr))', gap: 16, marginBottom: 28 }}>
        <div style={card}>
          <div style={label}>Category</div>
          <div style={{ fontSize: 18, fontWeight: 600 }}>
            {merchant.mccCode || '—'}{category ? ' — ' + category : ''}
          </div>
        </div>
        <div style={card}>
          <div style={label}>Transaction count</div>
          <div style={bigNumber}>{transactionCount}</div>
        </div>
        <div style={card}>
          <div style={label}>Total spend</div>
          <div style={bigNumber}>${totalSpend}</div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 20 }}>
        <div style={card}>
          <h2 style={{ fontSize: 18, marginTop: 0, marginBottom: 16 }}>Individual purchases</h2>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', fontSize: 12, color: '#9a9aa8', textTransform: 'uppercase' }}>
                <th style={{ paddingBottom: 10 }}>#</th>
                <th style={{ paddingBottom: 10 }}>Amount</th>
              </tr>
            </thead>
            <tbody>
              {merchant.lines.map(function (line) {
                return (
                  <tr key={line.line} style={{ borderTop: '1px solid #26262f' }}>
                    <td style={{ padding: '10px 0', color: '#c7c7d1' }}>{line.line}</td>
                    <td style={{ padding: '10px 0', color: '#c7c7d1' }}>${line.amount.toFixed(2)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div style={card}>
          <h2 style={{ fontSize: 18, marginTop: 0, marginBottom: 16 }}>Alert threshold</h2>
          <ThresholdSlider />
          <ThresholdReadout />
          {import.meta.env.DEV && (
            <button
              type="button"
              onClick={function () { setShouldThrow(true); }}
              style={{ marginTop: 16, padding: '8px 12px', background: 'transparent', border: '1px solid #26262f', borderRadius: 8, color: '#9a9aa8', cursor: 'pointer', fontSize: 13 }}
            >
              Trigger error
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

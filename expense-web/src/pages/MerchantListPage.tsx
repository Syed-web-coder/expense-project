import { useState } from 'react';
import { useQuery } from '@apollo/client';
import { graphql } from '../gql/generated';
import { FilterStrip } from '../components/FilterStrip';
import { useDebouncedSearch } from '../hooks/useDebouncedSearch';
import { useMerchantFilterStore } from '../stores/useMerchantFilterStore';

const LatestMerchantsDocument = graphql(`
  query LatestMerchants {
    latestMerchants(limit: 20) {
      id
      name
      mccCode
      capturedAt
      lines {
        line
        amount
      }
    }
  }
`);

const MCC_CATEGORIES: Record<string, string> = {
  '4111': 'Transportation',
  '4911': 'Utilities',
  '5411': 'Grocery Stores',
  '5812': 'Restaurants',
  '7922': 'Streaming / Entertainment',
};

const card: React.CSSProperties = {
  background: '#15151c',
  border: '1px solid #26262f',
  borderRadius: 14,
  padding: '20px 22px',
};

const label: React.CSSProperties = {
  fontSize: 13,
  color: '#9a9aa8',
  marginBottom: 6,
};

const bigNumber: React.CSSProperties = {
  fontSize: 32,
  fontWeight: 600,
  color: '#f4f4f6',
};

export function MerchantListPage() {
  const { loading, error, data, refetch } = useQuery(LatestMerchantsDocument);
  const debouncedSearch = useDebouncedSearch();
  const mccFilter = useMerchantFilterStore((s) => s.mccFilter);
  const [merchantName, setMerchantName] = useState('');
  const [amount, setAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setSubmitError(null);
    try {
      const res = await fetch('/api/v1/merchants/expenses', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'ngrok-skip-browser-warning': 'true' },
        body: JSON.stringify({ merchantName, amount: Number(amount) }),
      });
      if (!res.ok) {
        throw new Error(`Request failed: ${res.status}`);
      }
      setMerchantName('');
      setAmount('');
      await refetch();
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div role="status" style={{ color: '#f4f4f6', padding: 24 }}>Loading merchants…</div>;
  }

  if (error) {
    return <div role="alert" style={{ color: '#f87171', padding: 24 }}>Error: {error.message}</div>;
  }

  const filteredMerchants = (data?.latestMerchants ?? []).filter((merchant) => {
    const matchesSearch = debouncedSearch.trim() === ''
      || (merchant.name ?? '').toLowerCase().includes(debouncedSearch.trim().toLowerCase());
    const matchesMcc = mccFilter.length === 0
      || (merchant.mccCode != null && mccFilter.includes(merchant.mccCode));
    return matchesSearch && matchesMcc;
  });

  const totalMerchants = filteredMerchants.length;
  const totalTransactions = filteredMerchants.reduce((sum, m) => sum + m.lines.length, 0);
  const totalSpend = filteredMerchants
    .reduce((sum, m) => sum + m.lines.reduce((s, l) => s + l.amount, 0), 0)
    .toFixed(2);
  const categoryCount = new Set(filteredMerchants.map((m) => m.mccCode).filter(Boolean)).size;

  return (
    <div style={{ background: '#0b0b0f', minHeight: '100vh', color: '#f4f4f6', padding: '32px 40px', fontFamily: 'system-ui, sans-serif' }}>
      <h1 style={{ fontSize: 28, fontWeight: 700, margin: 0 }}>Dashboard</h1>
      <p style={{ color: '#9a9aa8', marginTop: 4, marginBottom: 28 }}>Overview of your tracked expenses</p>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0,1fr))', gap: 16, marginBottom: 28 }}>
        <div style={card}>
          <div style={label}>Total merchants</div>
          <div style={bigNumber}>{totalMerchants}</div>
        </div>
        <div style={card}>
          <div style={label}>Total transactions</div>
          <div style={bigNumber}>{totalTransactions}</div>
        </div>
        <div style={card}>
          <div style={label}>Total spend</div>
          <div style={bigNumber}>${totalSpend}</div>
        </div>
        <div style={card}>
          <div style={label}>Categories</div>
          <div style={bigNumber}>{categoryCount}</div>
        </div>
      </div>

      <div style={{ ...card, marginBottom: 28 }}>
        <FilterStrip />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.6fr 1fr', gap: 20 }}>
        <div style={card}>
          <h2 style={{ fontSize: 18, marginTop: 0, marginBottom: 16 }}>Merchants</h2>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ textAlign: 'left', fontSize: 12, color: '#9a9aa8', textTransform: 'uppercase' }}>
                <th style={{ paddingBottom: 10 }}>Merchant</th>
                <th style={{ paddingBottom: 10 }}>Category</th>
                <th style={{ paddingBottom: 10 }}>Txns</th>
                <th style={{ paddingBottom: 10 }}>Spend</th>
              </tr>
            </thead>
            <tbody>
              {filteredMerchants.map((merchant) => {
                const merchantTotal = merchant.lines.reduce((s, l) => s + l.amount, 0).toFixed(2);
                const category = merchant.mccCode ? MCC_CATEGORIES[merchant.mccCode] ?? merchant.mccCode : '—';
                return (
                  <tr key={merchant.id} style={{ borderTop: '1px solid #26262f' }}>
                    <td style={{ padding: '10px 0' }}>
                      <a href={`/merchants/${merchant.id}`} style={{ color: '#a78bfa', textDecoration: 'none' }}>
                        {merchant.name ?? merchant.id}
                      </a>
                    </td>
                    <td style={{ padding: '10px 0', color: '#c7c7d1' }}>{category}</td>
                    <td style={{ padding: '10px 0', color: '#c7c7d1' }}>{merchant.lines.length}</td>
                    <td style={{ padding: '10px 0', color: '#c7c7d1' }}>${merchantTotal}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div style={card}>
          <h2 style={{ fontSize: 18, marginTop: 0, marginBottom: 16 }}>Add expense</h2>
          <form onSubmit={(e) => { void handleSubmit(e); }}>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="merchantName" style={label}>Merchant name</label>
              <input
                id="merchantName"
                value={merchantName}
                onChange={(e) => setMerchantName(e.target.value)}
                placeholder="Starbucks"
                required
                style={{ width: '100%', padding: '10px 12px', background: '#0b0b0f', border: '1px solid #26262f', borderRadius: 8, color: '#f4f4f6' }}
              />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="amount" style={label}>Amount</label>
              <input
                id="amount"
                type="number"
                step="0.01"
                min="0"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="6.00"
                required
                style={{ width: '100%', padding: '10px 12px', background: '#0b0b0f', border: '1px solid #26262f', borderRadius: 8, color: '#f4f4f6' }}
              />
            </div>
            {submitError && <p role="alert" style={{ color: '#f87171', fontSize: 13 }}>Error: {submitError}</p>}
            <button
              type="submit"
              disabled={submitting}
              style={{ width: '100%', padding: '10px 12px', background: '#7c3aed', color: '#fff', border: 'none', borderRadius: 8, fontWeight: 600, cursor: 'pointer' }}
            >
              {submitting ? 'Adding…' : 'Add expense'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

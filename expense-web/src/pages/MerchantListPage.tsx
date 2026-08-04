import { useQuery, useMutation } from '@apollo/client';
import { graphql } from '../gql/generated';
import { getMccCategory } from '../types/merchant';
import { FilterStrip } from '../components/FilterStrip';
import { StatCard } from '../components/StatCard';
import { AddExpenseForm } from '../components/AddExpenseForm';
import { useMerchantFilterStore } from '../stores/useMerchantFilterStore';

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

const DashboardStatsDocument = graphql(`
  query DashboardStats {
    totalMerchants
    totalTransactions
    totalSpend
    categoryCount
  }
`);

const AddExpenseDocument = graphql(`
  mutation AddExpense($merchantId: ID!, $amount: Float!) {
    addExpense(merchantId: $merchantId, amount: $amount) {
      id
      merchantId
      merchantName
      amount
      occurredAt
      kind
    }
  }
`);

const currencyFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function MerchantListPage() {
  const searchText = useMerchantFilterStore((s) => s.searchText);
  const mccFilter  = useMerchantFilterStore((s) => s.mccFilter);

  const { loading, error, data } = useQuery(LatestMerchantsDocument);
  const { data: statsData } = useQuery(DashboardStatsDocument);

  const [addExpense, { loading: adding, error: addError }] = useMutation(AddExpenseDocument, {
    refetchQueries: [DashboardStatsDocument, LatestMerchantsDocument],
  });

  if (loading) {
    return <div role="status" className="loading-state">Loading merchants…</div>;
  }

  if (error) {
    return <div role="alert" className="error-state">Error: {error.message}</div>;
  }

  const merchants = data?.latestMerchants ?? [];

  const filtered = merchants.filter((m) => {
    if (mccFilter.length > 0 && !mccFilter.includes(m.mccCode ?? '')) return false;
    const q = searchText.trim().toLowerCase();
    if (q && !m.id.toLowerCase().includes(q) && !(m.mccCode ?? '').toLowerCase().includes(q)) return false;
    return true;
  });

  const stats = statsData;

  return (
    <div>
      <div className="page-header">
        <h2 className="page-title">Dashboard</h2>
        <p className="page-subtitle">Overview and 20 most recent merchants</p>
      </div>

      <div className="stats-grid">
        <StatCard label="Total merchants"    value={stats?.totalMerchants    ?? '—'} />
        <StatCard label="Total transactions" value={stats?.totalTransactions ?? '—'} />
        <StatCard
          label="Total spend"
          value={stats?.totalSpend != null ? currencyFmt.format(stats.totalSpend) : '—'}
        />
        <StatCard label="Categories" value={stats?.categoryCount ?? '—'} />
      </div>

      <AddExpenseForm
        merchants={merchants}
        onSubmit={(merchantId, amount) => addExpense({ variables: { merchantId, amount } }).then(() => undefined)}
        submitting={adding}
        error={addError?.message}
      />

      <FilterStrip />

      <ul aria-label="merchant-list" className="merchant-list">
        {filtered.map((merchant) => {
          const category = getMccCategory(merchant.mccCode);
          return (
            <li key={merchant.id} className="merchant-card">
              <a href={`/merchants/${merchant.id}`} className="merchant-card-link">
                <div className="merchant-card-header">
                  <span className="merchant-card-id">{merchant.id}</span>
                  <span className={`mcc-badge mcc-badge--${category.color}`}>
                    {category.label}
                  </span>
                </div>
                <div className="merchant-card-meta">
                  <span className="mcc-code-chip">{merchant.mccCode}</span>
                  <span className="merchant-card-date">captured {merchant.capturedAt}</span>
                  <span className="merchant-card-lines">
                    {merchant.lines.length} line{merchant.lines.length !== 1 ? 's' : ''}
                  </span>
                </div>
              </a>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

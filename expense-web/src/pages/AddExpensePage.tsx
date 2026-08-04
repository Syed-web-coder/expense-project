import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export function AddExpensePage() {
  const [merchantName, setMerchantName] = useState('');
  const [amount, setAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const res = await fetch('/api/v1/merchants/expenses', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'ngrok-skip-browser-warning': 'true' },
        body: JSON.stringify({ merchantName, amount: Number(amount) }),
      });
      if (!res.ok) {
        throw new Error(`Request failed: ${res.status}`);
      }
      const merchant = (await res.json()) as { id: string };
      void navigate(`/merchants/${merchant.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <h1>Add expense</h1>
      <form onSubmit={(e) => { void handleSubmit(e); }}>
        <div>
          <label htmlFor="merchantName">Merchant name</label>
          <input
            id="merchantName"
            value={merchantName}
            onChange={(e) => setMerchantName(e.target.value)}
            placeholder="Starbucks"
            required
          />
        </div>
        <div>
          <label htmlFor="amount">Amount</label>
          <input
            id="amount"
            type="number"
            step="0.01"
            min="0"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="6.00"
            required
          />
        </div>
        {error && <p role="alert">Error: {error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Adding…' : 'Add expense'}
        </button>
      </form>
    </div>
  );
}

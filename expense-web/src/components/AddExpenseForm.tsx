import { useState } from 'react';

interface Merchant {
  id: string;
  mccCode: string | null;
}

interface AddExpenseFormProps {
  merchants: Merchant[];
  onSubmit: (merchantId: string, amount: number) => Promise<void>;
  submitting: boolean;
  error?: string | undefined;
}

export function AddExpenseForm({ merchants, onSubmit, submitting, error }: AddExpenseFormProps) {
  const [merchantId, setMerchantId] = useState('');
  const [amount, setAmount] = useState('');
  const [success, setSuccess] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSuccess(false);
    try {
      await onSubmit(merchantId, parseFloat(amount));
      setSuccess(true);
      setAmount('');
    } catch {
      // error surfaces via props.error from the parent's useMutation state
    }
  }

  return (
    <form className="add-expense-form" onSubmit={handleSubmit} aria-label="Add expense">
      <h3 className="add-expense-form__title">Add expense</h3>
      <div className="add-expense-form__row">
        <label className="add-expense-form__label">
          Merchant
          <select
            className="add-expense-form__select"
            value={merchantId}
            onChange={(e) => { setMerchantId(e.currentTarget.value); setSuccess(false); }}
            required
          >
            <option value="">Select merchant…</option>
            {merchants.map((m) => (
              <option key={m.id} value={m.id}>
                {m.id}{m.mccCode ? ` (${m.mccCode})` : ''}
              </option>
            ))}
          </select>
        </label>

        <label className="add-expense-form__label">
          Amount (USD)
          <input
            type="number"
            min="0.01"
            step="0.01"
            placeholder="0.00"
            className="add-expense-form__input"
            value={amount}
            onChange={(e) => { setAmount(e.currentTarget.value); setSuccess(false); }}
            required
          />
        </label>

        <button
          type="submit"
          className="add-expense-form__submit"
          disabled={submitting || !merchantId}
        >
          {submitting ? 'Adding…' : 'Add expense'}
        </button>
      </div>

      {error && <p className="add-expense-form__error">{error}</p>}
      {success && !error && <p className="add-expense-form__success">Expense added.</p>}
    </form>
  );
}

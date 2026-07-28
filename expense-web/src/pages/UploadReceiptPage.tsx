import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

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

export function UploadReceiptPage() {
  const [dragOver, setDragOver] = useState(false);
  const [fileName, setFileName] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [merchantName, setMerchantName] = useState('');
  const [amount, setAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [success, setSuccess] = useState(false);
  const inputRef = useRef(null);
  const navigate = useNavigate();

  function handleFile(file) {
    setFileName(file.name);
    const guess = file.name.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
    setMerchantName((current) => current || guess);
    if (file.type.startsWith('image/')) {
      setPreviewUrl(URL.createObjectURL(file));
    } else {
      setPreviewUrl(null);
    }
  }

  function onDrop(e) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) handleFile(file);
  }

  async function handleSubmit(e) {
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
        throw new Error('Request failed: ' + res.status);
      }
      const merchant = await res.json();
      setSuccess(true);
      setTimeout(function () { navigate('/merchants/' + merchant.id); }, 800);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ background: '#0b0b0f', minHeight: '100vh', color: '#f4f4f6', padding: '32px 40px', fontFamily: 'system-ui, sans-serif' }}>
      <h1 style={{ fontSize: 28, fontWeight: 700, margin: 0 }}>Upload receipt</h1>
      <p style={{ color: '#9a9aa8', marginTop: 4, marginBottom: 28 }}>Add an expense by uploading a receipt</p>

      <div
        style={Object.assign({}, card, {
          border: dragOver ? '2px dashed #7c3aed' : '2px dashed #26262f',
          textAlign: 'center',
          padding: '48px 24px',
          marginBottom: 24,
          cursor: 'pointer',
        })}
        onDragOver={function (e) { e.preventDefault(); setDragOver(true); }}
        onDragLeave={function () { setDragOver(false); }}
        onDrop={onDrop}
        onClick={function () { inputRef.current && inputRef.current.click(); }}
      >
        <input
          ref={inputRef}
          type="file"
          accept="image/*,.pdf"
          style={{ display: 'none' }}
          onChange={function (e) {
            const file = e.target.files && e.target.files[0];
            if (file) handleFile(file);
          }}
        />
        {previewUrl ? (
          <img src={previewUrl} alt="Receipt preview" style={{ maxHeight: 220, borderRadius: 8, marginBottom: 12 }} />
        ) : (
          <div style={{ fontSize: 40, marginBottom: 12 }}>Upload</div>
        )}
        <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 4 }}>
          {fileName ? fileName : 'Drag & drop a receipt here'}
        </div>
        <div style={{ color: '#9a9aa8', fontSize: 14 }}>
          {fileName ? 'Click to choose a different file' : 'or click to browse files'}
        </div>
        <div style={{ color: '#5f5f6c', fontSize: 12, marginTop: 8 }}>
          Supports PDF, JPG, PNG
        </div>
      </div>

      {fileName && (
        <div style={Object.assign({}, card, { maxWidth: 420 })}>
          <h2 style={{ fontSize: 18, marginTop: 0, marginBottom: 4 }}>Confirm expense</h2>
          <p style={{ color: '#9a9aa8', fontSize: 13, marginTop: 0, marginBottom: 16 }}>
            We could not read the receipt automatically. Confirm the details below.
          </p>
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="merchantName" style={label}>Merchant name</label>
              <input
                id="merchantName"
                value={merchantName}
                onChange={function (e) { setMerchantName(e.target.value); }}
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
                onChange={function (e) { setAmount(e.target.value); }}
                placeholder="0.00"
                required
                style={{ width: '100%', padding: '10px 12px', background: '#0b0b0f', border: '1px solid #26262f', borderRadius: 8, color: '#f4f4f6' }}
              />
            </div>
            {submitError && <p role="alert" style={{ color: '#f87171', fontSize: 13 }}>Error: {submitError}</p>}
            {success && <p style={{ color: '#4ade80', fontSize: 13 }}>Expense added, redirecting</p>}
            <button
              type="submit"
              disabled={submitting}
              style={{ width: '100%', padding: '10px 12px', background: '#7c3aed', color: '#fff', border: 'none', borderRadius: 8, fontWeight: 600, cursor: 'pointer' }}
            >
              {submitting ? 'Adding...' : 'Add expense'}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}

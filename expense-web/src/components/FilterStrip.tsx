import { useMerchantFilterStore } from '../stores/useMerchantFilterStore';

const MCC_CODES = ['5812', '4121', '7393'] as const;

export function FilterStrip() {
  const mccFilter = useMerchantFilterStore((s) => s.mccFilter);
  const setMccFilter = useMerchantFilterStore((s) => s.setMccFilter);
  const dateRange = useMerchantFilterStore((s) => s.dateRange);
  const setSearchText = useMerchantFilterStore((s) => s.setSearchText);
  const searchText = useMerchantFilterStore((s) => s.searchText);
  const includeArchived = useMerchantFilterStore((s) => s.includeArchived);
  const setIncludeArchived = useMerchantFilterStore((s) => s.setIncludeArchived);

  function toggleMcc(code: string) {
    setMccFilter(
      mccFilter.includes(code)
        ? mccFilter.filter((c) => c !== code)
        : [...mccFilter, code],
    );
  }

  return (
    <div>
      <div role="group" aria-label="MCC code filter">
        {MCC_CODES.map((code) => (
          <button
            key={code}
            type="button"
            aria-pressed={mccFilter.includes(code)}
            onClick={() => toggleMcc(code)}
          >
            {code}
          </button>
        ))}
      </div>

      <label>
        From
        <input type="date" value={dateRange[0]} readOnly />
      </label>

      <label>
        Search
        <input
          type="text"
          value={searchText}
          onChange={(e) => setSearchText(e.currentTarget.value)}
        />
      </label>

      <label>
        Include archived
        <input
          type="checkbox"
          checked={includeArchived}
          onChange={(e) => setIncludeArchived(e.currentTarget.checked)}
        />
      </label>
    </div>
  );
}

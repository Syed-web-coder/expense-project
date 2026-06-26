import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';

type FilterState = {
  readonly mccFilter:       ReadonlyArray<string>;
  readonly dateRange:       readonly [string, string | null];
  readonly searchText:      string;
  readonly includeArchived: boolean;
  readonly threshold:       number;
};

type FilterActions = {
  readonly setMccFilter:       (next: ReadonlyArray<string>) => void;
  readonly setSearchText:      (next: string)  => void;
  readonly setThreshold:       (next: number)  => void;
  readonly setIncludeArchived: (next: boolean) => void;
  readonly reset:              () => void;
};

const INITIAL: FilterState = {
  mccFilter: [],
  dateRange: ['', null],
  searchText: '',
  includeArchived: false,
  threshold: 50,
};

// Only `threshold` is persisted across reloads — see partialize below.
// Search text persisted across reloads would be a UX bug (engineer types
// "foo" for an unrelated reason, returns next week, sees results still
// filtered by "foo"); the filter chips and date range are session state.
export const useMerchantFilterStore = create<FilterState & FilterActions>()(
  devtools(
    persist(
      (set) => ({
        ...INITIAL,
        setMccFilter: (next) => set({ mccFilter: next }, false, 'filters/setMccFilter'),
        setSearchText: (next) => set({ searchText: next }, false, 'filters/setSearchText'),
        setThreshold: (next) => set({ threshold: next }, false, 'filters/setThreshold'),
        setIncludeArchived: (next) => set({ includeArchived: next }, false, 'filters/setIncludeArchived'),
        reset: () => set(INITIAL, false, 'filters/reset'),
      }),
      {
        name: 'expense-web:filters',
        partialize: (s) => ({ threshold: s.threshold }),
      },
    ),
    { name: 'useMerchantFilterStore' },
  ),
);

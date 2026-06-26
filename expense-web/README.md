# expense-web

Frontend for the expense-tracking capstone — Vite + React 19 + TypeScript.

## Setup

```bash
pnpm install
pnpm dev
```

Then navigate to `http://localhost:5173/#/merchants/stub-id-1` (routing is
temporary hash-based scaffolding — see Known Limitations below).

## Scripts

| Command | Purpose |
|---|---|
| `pnpm dev` | Start the Vite dev server |
| `pnpm typecheck` | `tsc --noEmit` |
| `pnpm lint` | `eslint .` |
| `pnpm test` | `vitest run` |
| `pnpm build` | `tsc -b && vite build` |

## Architecture (as of W4 D2)

### State management

State is split across three layers, smallest primitive that fits each job:

- **`useReducer`** (`src/pages/MerchantDetailPage.reducer.ts`) — owns the
  page's fetch lifecycle as a discriminated union:
  `idle | loading | success | error | empty`. Pure reducer, unit-tested in
  isolation, with a compile-time exhaustiveness check so a missed action
  variant fails the build.
- **Zustand store** (`src/stores/useMerchantFilterStore.ts`) — owns
  cross-cutting filter state (MCC chips, date range, search text, archived
  toggle, threshold) that's shared across components several levels apart in
  the tree. Wrapped in `devtools` + `persist` middleware; `partialize` keeps
  only `threshold` in `localStorage` — search text persisting across reloads
  would be a UX bug (stale filter on return visits).
- **`useDebouncedSearch`** (`src/hooks/useDebouncedSearch.ts`) — a custom
  hook that reads `searchText` from the store and exposes a value lagged by
  `delayMs` (default 300ms), with proper `useEffect` cleanup so stale timers
  never fire after a newer keystroke.

### Error handling

`src/components/ErrorBoundary.tsx` is a class component (React 19 has no
hook-based equivalent yet) wrapping `MerchantDetailPage` in `App.tsx`. Catches
render-time throws via `getDerivedStateFromError`/`componentDidCatch`, logs to
console, and renders a fallback with a "Try again" button that resets state
and re-mounts the subtree.

A dev-only "Trigger error" button is gated behind `import.meta.env.DEV`. It
sets a state flag and throws *during the next render* rather than inside the
click handler itself — React error boundaries don't catch errors thrown
inside event handlers, only during rendering/lifecycle methods.

### Data fetching

`src/hooks/useMerchant.ts` exports a plain `fetchMerchant(id)` async function
(not a hook with its own state) — the page's `useReducer` is the single
source of truth for loading/error/data, so the fetcher doesn't maintain
competing internal state. Currently stubbed against `public/mocks/merchant.json`;
only `'stub-id-1'` resolves to data, anything else resolves to `null` →
the reducer's `empty` status.

## Known limitations (intentional — temporary scaffolding)

- **Routing** (`src/App.tsx`) is a single regex match against
  `location.hash`, not a real router, and doesn't listen for `hashchange`.
  Replaced by TanStack Router on W4 D3.
- **Data fetching** is a stubbed JSON file. Replaced by an Apollo Client
  query against the `/graphql` endpoint on W4 D3 — the reducer's
  `fetch/success | fetch/error` shape was deliberately designed to line up
  1:1 with Apollo's `data | error` result.
- **`FilterStrip`**'s MCC chip codes and date-range input are stub UI; no
  `setDateRange` action exists yet in the store.

## Testing notes

`src/test/setup.ts` includes a small in-memory `Storage` polyfill. Recent
Node versions (20.11+/22+/24+/26+) ship an experimental built-in
`localStorage` global that can conflict with jsdom's own implementation in
the test environment, causing zustand's `persist` middleware to throw on
`setItem`. The polyfill sidesteps this by always providing a working
`Storage` implementation, independent of Node/jsdom version interactions.

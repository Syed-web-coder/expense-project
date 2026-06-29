import './server';
import '@testing-library/jest-dom';

// Node 20.11+/22+/24+/26+ ships an experimental built-in `localStorage`
// global that is non-functional without the --localstorage-file flag, and
// it can end up being the only `localStorage` visible to tests under jsdom
// (or simply leaving it undefined). Define a small in-memory Storage
// polyfill explicitly so zustand's `persist` middleware always has a
// working implementation, independent of what jsdom or the Node runtime
// would otherwise provide.
class MemoryStorage implements Storage {
  private store = new Map<string, string>();
  get length(): number {
    return this.store.size;
  }
  clear(): void {
    this.store.clear();
  }
  getItem(key: string): string | null {
    return this.store.has(key) ? this.store.get(key)! : null;
  }
  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }
  removeItem(key: string): void {
    this.store.delete(key);
  }
  setItem(key: string, value: string): void {
    this.store.set(key, value);
  }
}

Object.defineProperty(globalThis, 'localStorage', {
  value: new MemoryStorage(),
  configurable: true,
  writable: true,
});

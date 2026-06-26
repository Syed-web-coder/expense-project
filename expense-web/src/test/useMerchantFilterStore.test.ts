import { describe, it, expect, beforeEach } from 'vitest';
import { useMerchantFilterStore } from '../stores/useMerchantFilterStore';

beforeEach(() => {
  useMerchantFilterStore.setState(useMerchantFilterStore.getInitialState(), true);
});

describe('useMerchantFilterStore', () => {
  it('setSearchText updates state.searchText', () => {
    useMerchantFilterStore.getState().setSearchText('foo');
    expect(useMerchantFilterStore.getState().searchText).toBe('foo');
  });

  it('setThreshold updates state.threshold', () => {
    useMerchantFilterStore.getState().setThreshold(80);
    expect(useMerchantFilterStore.getState().threshold).toBe(80);
  });

  it('setMccFilter is last-write-wins', () => {
    useMerchantFilterStore.getState().setMccFilter(['A', 'B']);
    useMerchantFilterStore.getState().setMccFilter(['C']);
    expect(useMerchantFilterStore.getState().mccFilter).toEqual(['C']);
  });

  it('reset() returns state to the initial shape', () => {
    useMerchantFilterStore.getState().setSearchText('foo');
    useMerchantFilterStore.getState().setThreshold(80);
    useMerchantFilterStore.getState().reset();
    expect(useMerchantFilterStore.getState().searchText).toBe('');
    expect(useMerchantFilterStore.getState().threshold).toBe(50);
  });
});

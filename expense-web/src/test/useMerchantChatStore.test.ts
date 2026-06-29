// src/test/useMerchantChatStore.test.ts
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useMerchantChatStore } from '../stores/useMerchantChatStore';
import type { UIMessage } from 'ai';

const stubMessage: UIMessage = {
  id: 'msg-1',
  role: 'assistant',
  parts: [{ type: 'text', text: 'hello' }],
};

beforeEach(() => {
  useMerchantChatStore.getState().clear();
  localStorage.removeItem('uc:merchant-chat');
});

describe('useMerchantChatStore', () => {
  it('starts with an empty messages array', () => {
    expect(useMerchantChatStore.getState().messages).toEqual([]);
  });

  it('appendAssistantMessage adds a message to state', () => {
    useMerchantChatStore.getState().appendAssistantMessage(stubMessage);
    expect(useMerchantChatStore.getState().messages).toEqual([stubMessage]);
  });

  it('appendAssistantMessage is append-only', () => {
    const second: UIMessage = { ...stubMessage, id: 'msg-2' };
    useMerchantChatStore.getState().appendAssistantMessage(stubMessage);
    useMerchantChatStore.getState().appendAssistantMessage(second);
    expect(useMerchantChatStore.getState().messages).toEqual([stubMessage, second]);
  });

  it('clear() empties the messages array', () => {
    useMerchantChatStore.getState().appendAssistantMessage(stubMessage);
    useMerchantChatStore.getState().clear();
    expect(useMerchantChatStore.getState().messages).toEqual([]);
  });

  it('persists appended messages to localStorage under uc:merchant-chat', () => {
    useMerchantChatStore.getState().appendAssistantMessage(stubMessage);
    const raw = localStorage.getItem('uc:merchant-chat');
    expect(raw).not.toBeNull();
    const parsed = JSON.parse(raw as string);
    expect(parsed.state.messages).toEqual([stubMessage]);
  });

  it('rehydrates persisted messages after the store is re-created', async () => {
    useMerchantChatStore.getState().appendAssistantMessage(stubMessage);

    // Genuinely simulate a fresh page load: reset Vitest's module
    // registry and re-import the store module so a brand-new zustand
    // instance is created. Mutating the existing singleton's state would
    // also trigger persist's write-through and destroy the very data
    // we're trying to rehydrate from -- this is why we re-import instead.
    vi.resetModules();
    const fresh = await import('../stores/useMerchantChatStore');
    await fresh.useMerchantChatStore.persist.rehydrate();

    expect(fresh.useMerchantChatStore.getState().messages).toEqual([stubMessage]);
  });
});

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UIMessage } from 'ai';

interface MerchantChatStoreState {
  readonly messages: readonly UIMessage[];
  appendAssistantMessage: (m: UIMessage) => void;
  clear: () => void;
}

export const useMerchantChatStore = create<MerchantChatStoreState>()(
  persist(
    (set) => ({
      messages: [],
      appendAssistantMessage: (m) =>
        set((s) => ({ messages: [...s.messages, m] })),
      clear: () => set({ messages: [] }),
    }),
    { name: 'uc:merchant-chat' },
  ),
);

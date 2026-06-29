import { useChat } from '@ai-sdk/react';
import { DefaultChatTransport } from 'ai';
import { useParams } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { useMerchantChatStore } from '../stores/useMerchantChatStore';
import { ToolCallCard } from './ToolCallCard';

export function MerchantChatPanel(): React.ReactElement {
  const { id = '' } = useParams<{ id: string }>();
  const [input, setInput] = useState('');
  const persistMessage = useMerchantChatStore((s) => s.appendAssistantMessage);

  const { messages, sendMessage, status, error, stop, regenerate } = useChat({
    id: `merchant-${id}`,
    transport: new DefaultChatTransport({ api: '/api/chat' }),
    onFinish: ({ message }) => {
      // CRITICAL: only persist on completion. Writing partial tokens
      // to Zustand mid-stream breaks the persist middleware's rehydration.
      persistMessage(message);
    },
  });

  const isLoading = status === 'submitted' || status === 'streaming';

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (input.trim() === '') return;
    sendMessage({ text: input });
    setInput('');
  };

  const endRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <section aria-label="merchant-chat">
      <ul aria-label="chat-transcript">
        {messages.map((m) => (
          <li key={m.id} data-role={m.role}>
            <strong>{m.role}:</strong>{' '}
            {m.parts.map((part, i) => {
              if (part.type === 'text') {
                return <span key={i}>{part.text}</span>;
              }
              if (part.type.startsWith('tool-')) {
                const toolPart = part as unknown as {
                  state: string;
                  input: unknown;
                  output?: unknown;
                };
                return (
                  <ToolCallCard
                    key={i}
                    toolName={part.type.slice(5)}
                    toolState={toolPart.state}
                    input={toolPart.input}
                    output={toolPart.output}
                  />
                );
              }
              return null;
            })}
          </li>
        ))}
      </ul>
      <div ref={endRef} />

      {isLoading && <p role="status">Assistant is replying...</p>}
      {error && <p role="alert">Error: {error.message}</p>}

      <form onSubmit={handleSubmit}>
        <input
          aria-label="chat-input"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={isLoading}
        />
        <button type="submit" disabled={isLoading || input.trim() === ''}>
          Send
        </button>
        <button type="button" onClick={stop} disabled={!isLoading}>
          Stop
        </button>
        <button type="button" onClick={() => regenerate()} disabled={isLoading}>
          Regenerate
        </button>
      </form>
    </section>
  );
}

import { useChat } from '@ai-sdk/react';
import { DefaultChatTransport } from 'ai';
import { useParams } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';

export function MerchantChatPanel(): React.ReactElement {
  const { id = '' } = useParams<{ id: string }>();
  const [input, setInput] = useState('');

  const { messages, sendMessage, status, error, stop, regenerate } = useChat({
    id: `merchant-${id}`,
    transport: new DefaultChatTransport({ api: '/api/chat' }),
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
            {m.parts.map((part, i) =>
              part.type === 'text' ? <span key={i}>{part.text}</span> : null,
            )}
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

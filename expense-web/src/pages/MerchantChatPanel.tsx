import { useChat } from '@ai-sdk/react';
import { DefaultChatTransport } from 'ai';
import { useParams } from 'react-router-dom';
import { useState } from 'react';

export function MerchantChatPanel(): React.ReactElement {
  const { id = '' } = useParams<{ id: string }>();
  const [input, setInput] = useState('');

  const { messages, sendMessage } = useChat({
    id: `merchant-${id}`,
    transport: new DefaultChatTransport({ api: '/api/chat' }),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (input.trim() === '') return;
    sendMessage({ text: input });
    setInput('');
  };

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

      <form onSubmit={handleSubmit}>
        <input
          aria-label="chat-input"
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <button type="submit">Send</button>
      </form>
    </section>
  );
}

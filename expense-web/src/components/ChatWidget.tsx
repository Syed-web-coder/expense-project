import { useState } from 'react';

// TODO: move to an env var (e.g. import.meta.env.VITE_CHAT_API_URL) once
// expense-agent-svc has a real deployed URL. Defaults to local dev.
const CHAT_API_URL = 'http://localhost:8000/v1/chat/stream';

// TODO: replace with the real signed-in tenant id once auth is wired through.
const TENANT_ID = 'demo';

type Message = { role: 'user' | 'assistant'; text: string };

// expense-agent-svc streams newline-delimited "<code>:<json>\n" lines.
// 0: streaming text delta   2: final synthesized answer   3: error
function parseStreamLine(line: string): { code: string; payload: unknown } | null {
  const sep = line.indexOf(':');
  if (sep === -1) return null;
  const code = line.slice(0, sep);
  try {
    return { code, payload: JSON.parse(line.slice(sep + 1)) };
  } catch {
    return null;
  }
}

export function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [threadId] = useState(function () {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `thread-${Date.now()}`;
  });
  const [messages, setMessages] = useState<Message[]>([
    { role: 'assistant', text: "Hi! I'm your expense assistant — ask me about your transactions." },
  ]);

  async function handleSend(e: React.FormEvent) {
    e.preventDefault();
    const question = input.trim();
    if (!question || sending) return;

    setMessages(function (prev) {
      return prev.concat([{ role: 'user', text: question }]);
    });
    setInput('');
    setSending(true);

    // Placeholder assistant message we'll fill in as the response streams/arrives.
    setMessages(function (prev) {
      return prev.concat([{ role: 'assistant', text: '...' }]);
    });

    try {
      const res = await fetch(CHAT_API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question, tenant_id: TENANT_ID, thread_id: threadId }),
      });

      if (!res.ok || !res.body) {
        throw new Error(`chat request failed: ${res.status}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let streamed = '';

      function updateLastAssistant(text: string) {
        setMessages(function (prev) {
          const next = prev.slice();
          next[next.length - 1] = { role: 'assistant', text };
          return next;
        });
      }

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? ''; // keep the last (possibly partial) line for next chunk

        for (const line of lines) {
          if (!line) continue;
          const parsed = parseStreamLine(line);
          if (!parsed) continue;

          if (parsed.code === '0') {
            const delta = (parsed.payload as { delta?: string }).delta ?? '';
            streamed += delta;
            updateLastAssistant(streamed);
          } else if (parsed.code === '2') {
            const finalAnswer = (parsed.payload as { finalAnswer?: { text?: string } }).finalAnswer;
            updateLastAssistant(finalAnswer?.text ?? (streamed || 'No answer returned.'));
          } else if (parsed.code === '3') {
            const err = (parsed.payload as { detail?: string }).detail ?? 'Something went wrong.';
            updateLastAssistant(`Sorry — ${err}`);
          }
        }
      }
    } catch {
      setMessages(function (prev) {
        const next = prev.slice();
        next[next.length - 1] = {
          role: 'assistant',
          text: "Sorry, I couldn't reach the expense assistant. Is expense-agent-svc running?",
        };
        return next;
      });
    } finally {
      setSending(false);
    }
  }

  return (
    <div style={{ position: 'fixed', bottom: 24, right: 24, fontFamily: 'system-ui, sans-serif', zIndex: 1000 }}>
      {open && (
        <div
          style={{
            width: 320,
            height: 420,
            background: '#15151c',
            border: '1px solid #26262f',
            borderRadius: 14,
            marginBottom: 12,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
          }}
        >
          <div style={{ padding: '14px 16px', borderBottom: '1px solid #26262f', color: '#f4f4f6', fontWeight: 600, fontSize: 14 }}>
            Expense Assistant
          </div>
          <div style={{ flex: 1, overflowY: 'auto', padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: 10 }}>
            {messages.map(function (m, i) {
              return (
                <div
                  key={i}
                  style={{
                    alignSelf: m.role === 'user' ? 'flex-end' : 'flex-start',
                    background: m.role === 'user' ? '#7c3aed' : '#0b0b0f',
                    color: m.role === 'user' ? '#fff' : '#c7c7d1',
                    padding: '8px 12px',
                    borderRadius: 10,
                    fontSize: 13,
                    maxWidth: '80%',
                  }}
                >
                  {m.text}
                </div>
              );
            })}
          </div>
          <form onSubmit={(e) => { void handleSend(e); }} style={{ display: 'flex', borderTop: '1px solid #26262f', padding: 10, gap: 8 }}>
            <input
              value={input}
              onChange={function (e) { setInput(e.target.value); }}
              placeholder="Ask about your expenses..."
              disabled={sending}
              style={{ flex: 1, padding: '8px 10px', background: '#0b0b0f', border: '1px solid #26262f', borderRadius: 8, color: '#f4f4f6', fontSize: 13 }}
            />
            <button
              type="submit"
              disabled={sending}
              style={{ padding: '8px 12px', background: '#7c3aed', color: '#fff', border: 'none', borderRadius: 8, fontSize: 13, cursor: sending ? 'default' : 'pointer', opacity: sending ? 0.6 : 1 }}
            >
              {sending ? '...' : 'Send'}
            </button>
          </form>
        </div>
      )}
      <button
        type="button"
        onClick={function () { setOpen(function (v) { return !v; }); }}
        style={{
          width: 56,
          height: 56,
          borderRadius: '50%',
          background: '#7c3aed',
          color: '#fff',
          border: 'none',
          fontSize: 22,
          cursor: 'pointer',
          boxShadow: '0 4px 14px rgba(124,58,237,0.5)',
        }}
      >
        {open ? '×' : '💬'}
      </button>
    </div>
  );
}

import { useState, useRef, useEffect } from 'react';

const AGENT_URL = 'http://localhost:8081/v1/chat/stream';
// TODO: replace with the authenticated tenant from auth context
const TENANT_ID = 'tenant-a';

type Citation = { doc_id: string; quote: string };

type FinalAnswer = {
  text: string;
  citations: Citation[];
  confidence: number;
};

type MessageRole = 'user' | 'assistant' | 'error';

type ChatMessage = {
  id: string;
  role: MessageRole;
  content: string;
  finalAnswer?: FinalAnswer;
  isStreaming: boolean;
};

function confidenceClass(score: number): string {
  if (score > 0.8) return 'confidence-badge--green';
  if (score >= 0.5) return 'confidence-badge--yellow';
  return 'confidence-badge--red';
}

export function ChatPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  // thread_id persists for the lifetime of this page load, giving the agent
  // continuity across follow-up questions within the same session.
  const [threadId] = useState<string>(() => crypto.randomUUID());
  const endRef = useRef<HTMLDivElement | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  async function sendMessage(question: string) {
    const userMsgId = crypto.randomUUID();
    const assistantMsgId = crypto.randomUUID();

    setMessages((prev) => [
      ...prev,
      { id: userMsgId, role: 'user', content: question, isStreaming: false },
      { id: assistantMsgId, role: 'assistant', content: '', isStreaming: true },
    ]);
    setIsStreaming(true);

    const abort = new AbortController();
    abortRef.current = abort;

    const patchAssistant = (patch: Partial<ChatMessage>) =>
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantMsgId ? { ...m, ...patch } : m,
        ),
      );

    try {
      const res = await fetch(AGENT_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          question,
          tenant_id: TENANT_ID,
          thread_id: threadId,
        }),
        signal: abort.signal,
      });

      if (!res.ok || !res.body) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = '';
      let terminal = false;

      while (!terminal) {
        const { done, value } = await reader.read();
        if (done) break;

        buf += decoder.decode(value, { stream: true });
        const lines = buf.split('\n');
        buf = lines.pop() ?? '';

        for (const raw of lines) {
          const line = raw.trim();
          if (!line) continue;

          const colonIdx = line.indexOf(':');
          if (colonIdx === -1) continue;

          const prefix = line.slice(0, colonIdx);
          const jsonStr = line.slice(colonIdx + 1);

          let payload: unknown;
          try {
            payload = JSON.parse(jsonStr);
          } catch {
            continue;
          }

          if (prefix === '0') {
            // Partial text delta — payload is {"delta": "<text>"}
            const delta = typeof (payload as { delta?: string }).delta === 'string'
              ? (payload as { delta: string }).delta
              : '';
            setMessages((prev) =>
              prev.map((m) =>
                m.id === assistantMsgId
                  ? { ...m, content: m.content + delta }
                  : m,
              ),
            );
          } else if (prefix === '2') {
            // Final answer — payload: { finalAnswer: FinalAnswer | string }
            const data = payload as { finalAnswer: FinalAnswer | string };
            const fa = data.finalAnswer;
            if (typeof fa === 'string') {
              patchAssistant({ content: fa, isStreaming: false });
            } else {
              patchAssistant({
                content: fa.text,
                finalAnswer: fa,
                isStreaming: false,
              });
            }
            terminal = true;
          } else if (prefix === '3') {
            // Error frame — payload: { error: string; detail?: string }
            const err = payload as { error: string; detail?: string };
            patchAssistant({
              role: 'error',
              content: err.detail ?? err.error ?? 'An error occurred.',
              isStreaming: false,
            });
            terminal = true;
          }

          if (terminal) break;
        }
      }
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') {
        // User cancelled — leave whatever partial text is there, just stop streaming
        patchAssistant({ isStreaming: false });
      } else {
        patchAssistant({
          role: 'error',
          content: err instanceof Error ? err.message : 'Request failed.',
          isStreaming: false,
        });
      }
    } finally {
      setIsStreaming(false);
      abortRef.current = null;
    }
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const q = input.trim();
    if (!q || isStreaming) return;
    setInput('');
    void sendMessage(q);
  }

  return (
    <div className="chat-page">
      <div className="page-header">
        <h2 className="page-title">Expense Agent</h2>
        <p className="page-subtitle">
          Ask questions about your expenses, budgets, or transactions
        </p>
      </div>

      <div className="chat-container">
        <div
          className="chat-messages"
          role="log"
          aria-label="chat-transcript"
          aria-live="polite"
        >
          {messages.length === 0 && (
            <div className="chat-empty-state">
              <p>Ask me anything about your expenses or transactions.</p>
            </div>
          )}

          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`chat-message chat-message--${msg.role}`}
              data-role={msg.role}
            >
              {msg.role === 'user' && (
                <div className="chat-bubble chat-bubble--user">
                  <p className="chat-bubble-text">{msg.content}</p>
                </div>
              )}

              {msg.role === 'assistant' && (
                <div className="chat-bubble chat-bubble--assistant">
                  {msg.isStreaming && msg.content === '' ? (
                    <span className="chat-thinking">Thinking</span>
                  ) : (
                    <>
                      <p className="chat-bubble-text">
                        {msg.content}
                        {msg.isStreaming && (
                          <span className="chat-cursor" aria-hidden="true" />
                        )}
                      </p>

                      {msg.finalAnswer && !msg.isStreaming && (
                        <div className="chat-answer-meta">
                          <span
                            className={`confidence-badge ${confidenceClass(msg.finalAnswer.confidence)}`}
                          >
                            {Math.round(msg.finalAnswer.confidence * 100)}%
                            confidence
                          </span>

                          {msg.finalAnswer.citations.length > 0 && (
                            <details className="chat-sources">
                              <summary className="chat-sources-toggle">
                                {msg.finalAnswer.citations.length} source
                                {msg.finalAnswer.citations.length !== 1
                                  ? 's'
                                  : ''}
                              </summary>
                              <ul className="chat-citations">
                                {msg.finalAnswer.citations.map((c, i) => (
                                  <li key={i} className="chat-citation">
                                    <span className="chat-citation-id">
                                      {c.doc_id}
                                    </span>
                                    <blockquote className="chat-citation-quote">
                                      {c.quote}
                                    </blockquote>
                                  </li>
                                ))}
                              </ul>
                            </details>
                          )}
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}

              {msg.role === 'error' && (
                <div className="chat-bubble chat-bubble--error" role="alert">
                  <p className="chat-bubble-text">⚠ {msg.content}</p>
                </div>
              )}
            </div>
          ))}

          <div ref={endRef} />
        </div>

        <form className="chat-form" onSubmit={handleSubmit}>
          <input
            className="chat-input"
            aria-label="chat-input"
            type="text"
            placeholder="Ask about your expenses…"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            disabled={isStreaming}
          />
          {isStreaming ? (
            <button
              type="button"
              className="chat-stop-btn"
              onClick={() => abortRef.current?.abort()}
            >
              Stop
            </button>
          ) : (
            <button
              type="submit"
              className="chat-send-btn"
              disabled={!input.trim()}
            >
              Send
            </button>
          )}
        </form>
      </div>
    </div>
  );
}

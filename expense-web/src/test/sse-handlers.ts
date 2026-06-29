// src/test/sse-handlers.ts
import { http, HttpResponse } from 'msw';

function frame(payload: unknown): Uint8Array {
  return new TextEncoder().encode(`data: ${JSON.stringify(payload)}\n\n`);
}

const STUB_WORDS = ['stub ', 'merchant ', 'reply.'];
const WORD_INTERVAL_MS = 100;

export const sseHandlers = [
  http.post('/api/chat', () => {
    let i = 0;
    let finished = false;

    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(frame({ type: 'start' }));
        controller.enqueue(frame({ type: 'start-step' }));
        controller.enqueue(frame({ type: 'text-start', id: 'txt-0' }));
      },
      // pull() only fires when the consumer is actually ready for the
      // next chunk -- backpressure-aware, unlike a free-running
      // setInterval that buffers every word regardless of whether
      // anyone is still reading. This is what makes a Stop mid-stream
      // test meaningful: once the client aborts and stops calling
      // read(), pull() simply never fires again, so no further words
      // get enqueued.
      async pull(controller) {
        if (finished) return;
        await new Promise((r) => setTimeout(r, WORD_INTERVAL_MS));

        if (i >= STUB_WORDS.length) {
          if (!finished) {
            finished = true;
            controller.enqueue(frame({ type: 'text-end', id: 'txt-0' }));
            controller.enqueue(frame({ type: 'finish-step' }));
            controller.enqueue(frame({ type: 'finish', finishReason: 'stop' }));
            controller.enqueue(new TextEncoder().encode('data: [DONE]\n\n'));
            controller.close();
          }
          return;
        }

        controller.enqueue(
          frame({ type: 'text-delta', id: 'txt-0', delta: STUB_WORDS[i] }),
        );
        i++;
      },
      cancel() {
        finished = true;
      },
    });

    return new HttpResponse(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache, no-transform',
        'x-vercel-ai-ui-message-stream': 'v1',
      },
    });
  }),
];

import { Hono } from 'hono';
import { streamText, convertToModelMessages, stepCountIs } from 'ai';
import { merchantTools } from './chat-tools';
import { createOpenAICompatible } from '@ai-sdk/openai-compatible';

const upstream = createOpenAICompatible({
  name: 'spring-ai',
  baseURL: 'http://localhost:8080/ai',
});

export const chat = new Hono().post('/chat', async (c) => {
  const { messages } = await c.req.json();

  const result = streamText({
    model: upstream.chatModel('uptime-crew-assistant'),
    system: 'You are an assistant that helps engineers categorise merchant expenses. When asked about a merchant, call lookupMerchant first. When asked whether a charge is deductible, call classifyDeduction with the merchant id.',
    messages: await convertToModelMessages(messages),
    tools: merchantTools,
    stopWhen: stepCountIs(3),
    abortSignal: c.req.raw.signal,
  });

  return result.toUIMessageStreamResponse({
    headers: {
      'Cache-Control': 'no-cache, no-transform',
      'X-Accel-Buffering': 'no',
    },
  });
});

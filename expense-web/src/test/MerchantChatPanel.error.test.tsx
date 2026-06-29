// src/test/MerchantChatPanel.error.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { MerchantChatPanel } from '../pages/MerchantChatPanel';
import { server } from './server';

let testCounter = 0;

function renderPanel() {
  // Each test gets a unique merchant id. useChat shares chat state
  // globally across mounts with the same `id` string, so reusing one
  // id across `it()` blocks leaks a prior test's completed/in-flight
  // conversation into the next test -- confirmed by a "completed
  // message" test finishing in 41ms, far too fast to have actually
  // streamed anything itself.
  const id = `stub-${++testCounter}`;
  render(
    <MemoryRouter initialEntries={[`/merchants/${id}`]}>
      <Routes>
        <Route path="/merchants/:id" element={<MerchantChatPanel />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('MerchantChatPanel error handling', () => {
  it('shows a role="alert" pane when the proxy returns 500', async () => {
    server.use(
      http.post('/api/chat', () => new HttpResponse(null, { status: 500 })),
    );
    renderPanel();
    const input = screen.getByLabelText('chat-input');
    await userEvent.type(input, 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
  });

  it('re-enables the input field after the error', async () => {
    server.use(
      http.post('/api/chat', () => new HttpResponse(null, { status: 500 })),
    );
    renderPanel();
    const input = screen.getByLabelText('chat-input');
    await userEvent.type(input, 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByLabelText('chat-input')).toBeEnabled();
  });

  it('re-enables the Send button once new text is typed after an error', async () => {
    server.use(
      http.post('/api/chat', () => new HttpResponse(null, { status: 500 })),
    );
    renderPanel();
    const input = screen.getByLabelText('chat-input');
    await userEvent.type(input, 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());

    await userEvent.type(input, 'retry');
    expect(screen.getByRole('button', { name: 'Send' })).toBeEnabled();
  });
});

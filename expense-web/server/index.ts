import { serve } from '@hono/node-server';
import { Hono } from 'hono';
import { chat } from './api/chat';

const app = new Hono();
app.route('/api', chat);

const port = 3001;
serve({ fetch: app.fetch, port });
console.log(`Hono proxy listening on http://localhost:${port}`);

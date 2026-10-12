import { expect, test } from '@playwright/test';
import { openAdminSession } from '../staff-api';

const apiBase = 'http://127.0.0.1:8083/api';

test('anonymous purchase reads are rejected, and logout revokes the session token', async ({ request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toLowerCase();
  const admin = {
    email: `session-${stamp}@example.com`,
    name: 'Session Admin',
    password: 'Browser!Test1',
  };

  const anon = await request.get(`${apiBase}/purchases`);
  expect(anon.status()).toBe(401);

  const auth = await openAdminSession(request, apiBase, admin);

  const listed = await request.get(`${apiBase}/purchases`, { headers: auth });
  expect(listed.ok(), await listed.text()).toBeTruthy();

  const logout = await request.post(`${apiBase}/auth/logout`, { headers: auth });
  expect(logout.ok(), await logout.text()).toBeTruthy();

  const after = await request.get(`${apiBase}/purchases`, { headers: auth });
  expect(after.status()).toBe(401);

  const pending = await request.get(`${apiBase}/auth/pending-signups`);
  expect(pending.status()).toBe(401);
  expect(await pending.text()).not.toContain('verificationToken');
});

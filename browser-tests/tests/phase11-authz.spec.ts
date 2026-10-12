import { expect, test } from '@playwright/test';
import { openAdminSession } from '../staff-api';

const apiBase = 'http://127.0.0.1:8083/api';

test('anonymous callers cannot read documents, and the test header is not a session', async ({ request }) => {
  const purchases = await request.get(`${apiBase}/purchases`, {
    headers: { 'X-Automan-Test-Anonymous': '1' },
  });
  expect(purchases.status()).toBe(401);

  for (const path of [
    '/purchases/1/pdf',
    '/invoice-history/1/pdf',
    '/purchases/1/media/1/url',
    '/auth/users/count',
    '/auth/pending-signups',
    '/purchases/test',
  ]) {
    const response = await request.get(`${apiBase}${path}`);
    expect(response.status(), path).toBe(401);
  }

  const exported = await request.post(`${apiBase}/purchases/export/xlsx`, { data: {} });
  expect(exported.status()).toBe(401);

  const elevated = await request.post(`${apiBase}/auth/signup`, {
    data: {
      email: `phase11-${Date.now().toString(36)}@example.com`,
      name: 'No Admin',
      password: 'Browser!Test1',
      role: 'ADMIN',
    },
  });
  expect(elevated.status()).toBe(400);
  expect(await elevated.text()).toContain('Viewer');
});

test('an editor can create a purchase but cannot manage users', async ({ request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toLowerCase();
  const admin = {
    email: `phase11-admin-${stamp}@example.com`,
    name: 'Phase Admin',
    password: 'Browser!Test1',
  };
  const editor = {
    email: `phase11-editor-${stamp}@example.com`,
    name: 'Phase Editor',
    password: 'Browser!Test1',
  };
  const auth = await openAdminSession(request, apiBase, admin);
  const createdEditor = await request.post(`${apiBase}/users`, {
    headers: auth,
    data: { ...editor, role: 'EDITOR' },
  });
  expect(createdEditor.ok(), await createdEditor.text()).toBeTruthy();
  const editorId = Number((await createdEditor.json()).id);

  const editorLogin = await request.post(`${apiBase}/auth/login`, {
    data: { email: editor.email, password: editor.password },
  });
  expect(editorLogin.ok(), await editorLogin.text()).toBeTruthy();
  const editorAuth = { Authorization: `Bearer ${((await editorLogin.json()) as { token: string }).token}` };

  const chassis = `ED${stamp}`.slice(0, 20).toUpperCase();
  const purchase = await request.post(`${apiBase}/purchases`, {
    headers: editorAuth,
    data: { date: '09/30/2026', chassis, carName: `Editor ${stamp}`, price: '10000', local: false },
  });
  expect(purchase.ok(), await purchase.text()).toBeTruthy();
  const purchaseId = Number((await purchase.json()).id);

  const users = await request.get(`${apiBase}/users`, { headers: editorAuth });
  expect(users.status()).toBe(403);
  const review = await request.post(`${apiBase}/auth/signup-review`, {
    headers: editorAuth,
    data: { id: 1, action: 'approve' },
  });
  expect(review.status()).toBe(403);

  const debug = await request.get(`${apiBase}/purchases/test`, { headers: editorAuth });
  expect(debug.status()).toBe(404);

  await request.delete(`${apiBase}/purchases/${purchaseId}`, { headers: auth });
  await request.delete(`${apiBase}/users/${editorId}`, { headers: auth });
});

test('a viewer can read a purchase and cannot change it', async ({ request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toLowerCase();
  const admin = {
    email: `phase11-vadmin-${stamp}@example.com`,
    name: 'Viewer Admin',
    password: 'Browser!Test1',
  };
  const viewer = {
    email: `phase11-viewer-${stamp}@example.com`,
    name: 'Viewer',
    password: 'Browser!Test1',
  };
  const auth = await openAdminSession(request, apiBase, admin);
  const createdViewer = await request.post(`${apiBase}/users`, {
    headers: auth,
    data: { ...viewer, role: 'VIEWER' },
  });
  expect(createdViewer.ok(), await createdViewer.text()).toBeTruthy();
  const viewerId = Number((await createdViewer.json()).id);
  const chassis = `VW${stamp}`.slice(0, 20).toUpperCase();
  const purchase = await request.post(`${apiBase}/purchases`, {
    headers: auth,
    data: { date: '09/30/2026', chassis, carName: `Read ${stamp}`, price: '10000', local: false },
  });
  expect(purchase.ok(), await purchase.text()).toBeTruthy();
  const purchaseId = Number((await purchase.json()).id);

  const viewerLogin = await request.post(`${apiBase}/auth/login`, {
    data: { email: viewer.email, password: viewer.password },
  });
  expect(viewerLogin.ok(), await viewerLogin.text()).toBeTruthy();
  const viewerAuth = { Authorization: `Bearer ${((await viewerLogin.json()) as { token: string }).token}` };

  const read = await request.get(`${apiBase}/purchases/purchase/${purchaseId}`, { headers: viewerAuth });
  expect(read.ok(), await read.text()).toBeTruthy();
  const write = await request.post(`${apiBase}/purchases`, {
    headers: viewerAuth,
    data: { date: '09/30/2026', chassis: `NO${stamp}`.slice(0, 20).toUpperCase(), price: '1', local: false },
  });
  expect(write.status()).toBe(403);
  expect(await write.text()).not.toContain('authToken');

  await request.delete(`${apiBase}/purchases/${purchaseId}`, { headers: auth });
  await request.delete(`${apiBase}/users/${viewerId}`, { headers: auth });
});

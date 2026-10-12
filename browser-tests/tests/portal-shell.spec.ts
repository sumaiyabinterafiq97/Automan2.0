import { expect, test } from '@playwright/test';
import { openAdminSession } from '../staff-api';

const apiBase = 'http://127.0.0.1:8083/api';

test('linked client and consignee portals stay separate from the staff app', async ({ page, request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toLowerCase();
  const admin = {
    email: `portal-admin-${stamp}@example.com`,
    name: 'Portal Admin',
    password: 'Browser!Test1',
  };
  const auth = await openAdminSession(request, apiBase, admin);
  const consignee = await request.post(`${apiBase}/admin/party-access/consignees`, {
    data: { legalName: `Portal Consignee ${stamp}` },
    headers: auth,
  });
  expect(consignee.ok(), await consignee.text()).toBeTruthy();
  const consigneeId = ((await consignee.json()) as { id: number }).id;

  const client = await request.post(`${apiBase}/clients`, {
    data: { clientNumber: `P2E${stamp.slice(0, 8)}`.toUpperCase(), clientName: `Portal Client ${stamp}`, currency: 'JPY' },
    headers: auth,
  });
  expect(client.ok(), await client.text()).toBeTruthy();
  const clientId = ((await client.json()) as { id: number }).id;
  const assigned = await request.put(`${apiBase}/admin/party-access/clients/${clientId}/consignee`, {
    data: { consigneeId },
    headers: auth,
  });
  expect(assigned.ok(), await assigned.text()).toBeTruthy();

  const clientUser = {
    email: `portal-client-${stamp}@example.com`,
    name: 'Portal Client User',
    password: 'Browser!Test1',
  };
  const consigneeUser = {
    email: `portal-consignee-${stamp}@example.com`,
    name: 'Portal Consignee User',
    password: 'Browser!Test1',
  };
  const unlinkedUser = {
    email: `portal-unlinked-${stamp}@example.com`,
    name: 'Portal Unlinked',
    password: 'Browser!Test1',
  };
  const clientUserId = await createUser(request, auth, clientUser, 'CLIENT');
  const consigneeUserId = await createUser(request, auth, consigneeUser, 'CONSIGNEE');
  await createUser(request, auth, unlinkedUser, 'CLIENT');
  expect((await link(request, auth, clientUserId, 'CLIENT', clientId)).ok()).toBeTruthy();
  expect((await link(request, auth, consigneeUserId, 'CONSIGNEE', consigneeId)).ok()).toBeTruthy();

  await login(page, clientUser.email, clientUser.password);
  await expect(page.locator('[data-portal-role="client"]')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Purchased cars' })).toBeVisible();
  await expect(page.locator('#sidebar')).toBeHidden();
  await expect(page.locator('#newBtn')).toBeHidden();
  await expect(page.locator('#purchaseTable')).toHaveCount(0);
  await expect(page.getByText(`Portal Client ${stamp}`)).toBeVisible();

  const privateCalls: string[] = [];
  page.on('request', (req) => {
    if (req.url().includes('/portal/client/cars') || req.url().includes('/purchases')) privateCalls.push(req.url());
  });
  await page.goto('/purchase');
  await expect(page).toHaveURL(/\/portal\/client$/);
  await expect(page.locator('#purchaseTable')).toHaveCount(0);
  await expect(page.locator('#newBtn')).toBeHidden();
  expect(privateCalls.filter((url) => url.includes('/purchases'))).toEqual([]);

  await page.locator('#portalLogoutBtn').click();
  await expect(page).toHaveURL(/\/login$/);

  await login(page, consigneeUser.email, consigneeUser.password);
  await expect(page.locator('[data-portal-role="consignee"]')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Ongoing shipments' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Purchased cars' })).toHaveCount(0);
  await expect(page.locator('#sidebar')).toBeHidden();
  await page.goto('/invoice');
  await expect(page).toHaveURL(/\/portal\/consignee$/);
  await expect(page.locator('#sidebar')).toBeHidden();
  await page.locator('#portalLogoutBtn').click();

  const blocked: string[] = [];
  page.on('request', (req) => {
    const url = req.url();
    if (url.includes('/portal/client/dashboard') || url.includes('/portal/client/cars') || url.includes('/purchases')) {
      blocked.push(url);
    }
  });
  await login(page, unlinkedUser.email, unlinkedUser.password);
  await expect(page.locator('[data-portal-state="waiting"]')).toBeVisible();
  await expect(page.getByText('Portal access has not been assigned')).toBeVisible();
  expect(blocked.filter((url) => url.includes('/portal/client/dashboard') || url.includes('/portal/client/cars'))).toEqual([]);

  await page.evaluate(() => window.localStorage.setItem('authToken', 'expired-token'));
  await page.goto('/portal/client');
  await expect(page).toHaveURL(/\/login$/);

  await login(page, admin.email, admin.password);
  await expect(page.locator('#purchaseTable')).toBeVisible();
  await page.goto('/portal/client');
  await expect(page).toHaveURL(/\/purchase$/);
  await expect(page.locator('[data-portal-role="client"]')).toHaveCount(0);
  await expect(page.locator('#newBtn')).toBeVisible();
});

test('an empty linked portal shows an empty state, and a failed request stays an error', async ({ page, request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toLowerCase();
  const admin = {
    email: `portal-empty-admin-${stamp}@example.com`,
    name: 'Portal Empty Admin',
    password: 'Browser!Test1',
  };
  const auth = await openAdminSession(request, apiBase, admin);
  const consignee = await request.post(`${apiBase}/admin/party-access/consignees`, {
    data: { legalName: `Empty Consignee ${stamp}` },
    headers: auth,
  });
  expect(consignee.ok(), await consignee.text()).toBeTruthy();
  const consigneeId = ((await consignee.json()) as { id: number }).id;
  const client = await request.post(`${apiBase}/clients`, {
    data: { clientNumber: `P2F${stamp.slice(0, 8)}`.toUpperCase(), clientName: `Empty Client ${stamp}`, currency: 'JPY' },
    headers: auth,
  });
  expect(client.ok(), await client.text()).toBeTruthy();
  const clientId = ((await client.json()) as { id: number }).id;
  expect((await request.put(`${apiBase}/admin/party-access/clients/${clientId}/consignee`, {
    data: { consigneeId },
    headers: auth,
  })).ok()).toBeTruthy();
  const clientUser = {
    email: `portal-empty-${stamp}@example.com`,
    name: 'Empty Client User',
    password: 'Browser!Test1',
  };
  const clientUserId = await createUser(request, auth, clientUser, 'CLIENT');
  expect((await link(request, auth, clientUserId, 'CLIENT', clientId)).ok()).toBeTruthy();

  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, clientUser.email, clientUser.password);
  await expect(page.locator('[data-portal-role="client"]')).toBeVisible();
  await expect(page.getByText('Purchased cars').first()).toBeVisible();
  await expect(page.locator('#sidebar')).toBeHidden();
  await expect(page.locator('[data-portal-state="ready"]')).toBeVisible();
  await page.getByRole('button', { name: 'Purchased cars' }).click();
  await expect(page.locator('[data-portal-state="empty"]')).toContainText('No purchased cars yet.');
  await expect(page.locator('#purchaseTable')).toHaveCount(0);

  await page.route('**/api/portal/client/dashboard', (route) => route.fulfill({
    status: 500,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'The request could not be completed.' }),
  }));
  await page.getByRole('button', { name: 'Dashboard' }).click();
  await expect(page.locator('[data-portal-state="error"]')).toBeVisible();
  await expect(page.locator('[data-portal-state="empty"]')).toHaveCount(0);
  await expect(page.getByText('No purchased cars yet.')).toHaveCount(0);
});

async function createUser(
  request: import('@playwright/test').APIRequestContext,
  auth: { Authorization: string },
  user: { email: string; name: string; password: string },
  role: string,
) {
  const created = await request.post(`${apiBase}/users`, { data: { ...user, role }, headers: auth });
  expect(created.ok(), await created.text()).toBeTruthy();
  return ((await created.json()) as { id: number }).id;
}

async function link(
  request: import('@playwright/test').APIRequestContext,
  auth: { Authorization: string },
  userId: number,
  partyType: string,
  partyId: number,
) {
  return request.post(`${apiBase}/admin/party-access/links`, {
    data: { userId, partyType, partyId },
    headers: auth,
  });
}

async function login(page: import('@playwright/test').Page, email: string, password: string) {
  await page.goto('/login');
  await page.locator('#si_email').fill(email);
  await page.locator('#si_pass').fill(password);
  await page.locator('#btn_signin').click();
}

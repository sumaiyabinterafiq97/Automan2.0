import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('approving one pending signup lets only that user log in', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const admin = {
    email: `approve-admin-${stamp.toLowerCase()}@example.com`,
    name: 'Approve Admin',
    password: 'Browser!Test1',
  };
  const approved = {
    email: `approved-${stamp.toLowerCase()}@example.com`,
    name: `Ada ${stamp}`,
    password: 'Browser!Test1',
  };
  const waiting = {
    email: `waiting-${stamp.toLowerCase()}@example.com`,
    name: `Bea ${stamp}`,
    password: 'Browser!Test1',
  };

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    await signup(request, approved);
    await signup(request, waiting);

    await login(page, admin);
    await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
    await page.locator('#userManagementBtn').scrollIntoViewIfNeeded({ timeout: 30_000 });
    await expect(page.locator('#userManagementBtn')).toBeVisible();
    await page.locator('#userManagementBtn').click({ timeout: 30_000 });
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible();
    await page.locator('#pendingRequestBtn').click({ timeout: 30_000 });
    await expect(page.getByRole('heading', { name: 'Pending Signups' })).toBeVisible();

    const pending = page.locator('#pendingSignupsTable');
    await expect(pending).toContainText(approved.name);
    await expect(pending).toContainText(waiting.name);
    await pending.locator('div').filter({ hasText: approved.name }).getByRole('button', { name: 'Accept' }).click({ timeout: 30_000 });

    await expect(page.locator('#message')).toHaveText('User approved. They have been notified by email.');
    await expect(pending).not.toContainText(approved.name);
    await expect(pending).toContainText(waiting.name);

    await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
    await page.locator('#logoutBtn').click({ timeout: 30_000 });
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await expect(page).toHaveURL(/\/login$/);

    await page.locator('#si_email').fill(waiting.email);
    await page.locator('#si_pass').fill(waiting.password);
    await page.locator('#btn_signin').click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('#signinMessage')).toHaveText('Invalid credentials');
    await expect(page.locator('#purchaseTable')).toBeHidden();

    await page.locator('#si_email').fill(approved.email);
    await page.locator('#si_pass').fill(approved.password);
    await page.locator('#btn_signin').click();
    await expect(page.locator('#purchaseTable')).toBeVisible();
    await page.getByRole('button', { name: 'Open menu' }).click();
    await expect(page.getByText('Role: VIEWER')).toBeVisible();
  } finally {
    const users = await request.get(`${apiBase}/users`);
    if (users.ok()) {
      const list = (await users.json()) as Array<{ id: number; email: string }>;
      const created = list.find((user) => user.email === approved.email);
      if (created) await request.delete(`${apiBase}/users/${created.id}`);
    }
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function signup(
  request: APIRequestContext,
  user: { email: string; name: string; password: string },
) {
  const created = await request.post(`${apiBase}/auth/signup`, {
    data: { ...user, role: 'VIEWER' },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
}

async function login(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

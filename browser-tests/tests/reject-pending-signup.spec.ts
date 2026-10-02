import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('rejecting one pending signup leaves the other request pending', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const admin = {
    email: `reject-admin-${stamp.toLowerCase()}@example.com`,
    name: 'Reject Admin',
    password: 'Browser!Test1',
  };
  const rejected = {
    email: `rejected-${stamp.toLowerCase()}@example.com`,
    name: `Cara ${stamp}`,
    password: 'Browser!Test1',
  };
  const waiting = {
    email: `still-waiting-${stamp.toLowerCase()}@example.com`,
    name: `Dina ${stamp}`,
    password: 'Browser!Test1',
  };

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    await signup(request, rejected);
    await signup(request, waiting);

    await login(page, admin);
    await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
    await page.locator('#userManagementBtn').scrollIntoViewIfNeeded({ timeout: 30_000 });
    await page.locator('#userManagementBtn').click({ timeout: 30_000 });
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await page.locator('#pendingRequestBtn').click({ timeout: 30_000 });
    await expect(page.getByRole('heading', { name: 'Pending Signups' })).toBeVisible();

    const pending = page.locator('#pendingSignupsTable');
    await expect(pending).toContainText(rejected.name);
    await expect(pending).toContainText(waiting.name);
    await pending.locator('div').filter({ hasText: rejected.name }).getByRole('button', { name: 'Reject' }).click({ timeout: 30_000 });

    await expect(page.locator('#message')).toHaveText('Signup request rejected. The user has been notified.');
    await expect(pending).not.toContainText(rejected.name);
    await expect(pending).toContainText(waiting.name);

    await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
    await page.locator('#logoutBtn').click({ timeout: 30_000 });
    await expect(page).toHaveURL(/\/login$/);

    await expectRejectedLogin(page, rejected);
    await expectRejectedLogin(page, waiting);
  } finally {
    const users = await request.get(`${apiBase}/users`);
    if (users.ok()) {
      const list = (await users.json()) as Array<{ id: number; email: string }>;
      for (const email of [rejected.email, waiting.email]) {
        const created = list.find((user) => user.email === email);
        if (created) await request.delete(`${apiBase}/users/${created.id}`);
      }
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

async function expectRejectedLogin(page: Page, user: { email: string; password: string }) {
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('#signinMessage')).toHaveText('Invalid credentials');
  await expect(page.locator('#purchaseTable')).toBeHidden();
}

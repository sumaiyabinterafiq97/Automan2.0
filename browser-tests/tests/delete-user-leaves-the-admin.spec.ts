import { expect, test, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('deleting one user removes only that account and leaves the admin able to log in', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const admin = {
    email: `keep-admin-${stamp.toLowerCase()}@example.com`,
    name: `Keep Admin ${stamp}`,
    password: 'Browser!Test1',
  };
  const removed = {
    email: `drop-user-${stamp.toLowerCase()}@example.com`,
    name: `Drop User ${stamp}`,
    password: 'Browser!Test1',
  };
  let removedId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    const created = await request.post(`${apiBase}/users`, { data: { ...removed, role: 'EDITOR' } });
    expect(created.ok(), await created.text()).toBeTruthy();
    removedId = Number((await created.json()).id);

    await login(page, removed);
    await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
    await page.locator('#logoutBtn').click({ timeout: 30_000 });
    await expect(page).toHaveURL(/\/login$/);

    await login(page, admin);
    await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
    await page.locator('#userManagementBtn').scrollIntoViewIfNeeded({ timeout: 30_000 });
    await page.locator('#userManagementBtn').click({ timeout: 30_000 });
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible();
    await expect(page.locator('#usersTable')).toContainText(admin.email);
    await expect(page.locator('#usersTable')).toContainText(removed.email);

    await page.locator(`.edit-user-btn[aria-label="Edit user ${removed.name}"]`).click({ timeout: 30_000 });
    await expect(page.getByRole('heading', { name: 'Edit User' })).toBeVisible();
    await expect(page.locator('#editUserEmail')).toHaveValue(removed.email);

    const confirm = new Promise<string>((resolve) => {
      page.once('dialog', async (dialog) => {
        const message = dialog.message();
        await dialog.accept();
        resolve(message);
      });
    });
    await page.locator('#deleteUserBtn').click({ timeout: 30_000 });
    expect(await confirm).toBe('Are you sure you want to delete this user? This action cannot be undone.');

    await expect(page.locator('#message')).toHaveText('User deleted successfully');
    await expect(page.getByRole('heading', { name: 'User Management' })).toBeVisible();
    await expect(page.locator('#usersTable')).toContainText(admin.email);
    await expect(page.locator('#usersTable')).not.toContainText(removed.email);
    removedId = null;

    await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
    await page.locator('#logoutBtn').click({ timeout: 30_000 });
    await expect(page).toHaveURL(/\/login$/);

    await page.locator('#si_email').fill(removed.email);
    await page.locator('#si_pass').fill(removed.password);
    await page.locator('#btn_signin').click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('#signinMessage')).toHaveText('Invalid credentials');
    await expect(page.locator('#purchaseTable')).toBeHidden();

    await login(page, admin);
    await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
    await expect(page.getByText('Role: ADMIN')).toBeVisible();
  } finally {
    if (removedId != null) await request.delete(`${apiBase}/users/${removedId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function login(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

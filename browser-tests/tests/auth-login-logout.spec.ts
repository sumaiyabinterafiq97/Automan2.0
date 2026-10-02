import { expect, test } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('wrong password is rejected, then login, logout, and a protected route return to login', async ({
  page,
  request,
}) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
  const admin = {
    email: `auth-${stamp.toLowerCase()}@example.com`,
    name: 'Auth Admin',
    password: 'Browser!Test1',
  };

  const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
  expect(setup.ok(), await setup.text()).toBeTruthy();

  await page.goto('/login');
  await expect(page.locator('#si_email')).toBeVisible();
  await page.locator('#si_email').fill(admin.email);
  await page.locator('#si_pass').fill('Wrong!Pass1');
  await page.locator('#btn_signin').click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('#signinMessage')).toBeVisible();
  await expect(page.locator('#signinMessage')).toHaveText('Invalid credentials');
  await expect(page.locator('#btn_signin')).toBeVisible();
  await expect(page.locator('#purchaseTable')).toBeHidden();

  await page.locator('#si_pass').fill(admin.password);
  await page.locator('#btn_signin').click();

  await expect(page.locator('#purchaseTable')).toBeVisible();
  await expect(page.locator('#newBtn')).toBeVisible();
  await expect(page).not.toHaveURL(/\/login\/?$/);

  await page.getByRole('button', { name: 'Open menu' }).click();
  await page.locator('#logoutBtn').click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('#si_email')).toBeVisible();
  await expect(page.locator('#btn_signin')).toBeVisible();

  await page.goto('/purchase');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('#si_email')).toBeVisible();
  await expect(page.locator('#btn_signin')).toBeVisible();
  await expect(page.locator('#purchaseTable')).toBeHidden();
});

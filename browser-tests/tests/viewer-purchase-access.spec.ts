import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('a viewer can see the purchase list but is not offered New+ or Edit', async ({ page, request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
  const chassis = `VW${stamp}`.slice(0, 20);
  const admin = {
    email: `viewer-admin-${stamp.toLowerCase()}@example.com`,
    name: 'Viewer Admin',
    password: 'Browser!Test1',
  };
  const viewer = {
    email: `viewer-${stamp.toLowerCase()}@example.com`,
    name: 'Viewer User',
    password: 'Browser!Test1',
  };
  let purchaseId: number | null = null;
  let viewerId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    const createdViewer = await request.post(`${apiBase}/users`, { data: { ...viewer, role: 'VIEWER' } });
    expect(createdViewer.ok(), await createdViewer.text()).toBeTruthy();
    viewerId = Number((await createdViewer.json()).id);

    const purchase = await request.post(`${apiBase}/purchases`, {
      data: {
        date: '09/30/2026',
        chassis,
        carName: `Viewer Car ${stamp}`,
        price: '10000',
        local: false,
      },
    });
    expect(purchase.ok(), await purchase.text()).toBeTruthy();
    purchaseId = Number((await purchase.json()).id);

    await login(page, viewer);
    await expect(page.locator('#purchaseTable')).toContainText(chassis);
    await page.getByRole('button', { name: 'Open menu' }).click();
    await expect(page.locator('#newBtn')).toBeHidden();
    await expect(page.getByText('Role: VIEWER')).toBeVisible();
    await expect(page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${chassis}"]`)).toHaveCount(0);
  } finally {
    if (purchaseId != null) await request.delete(`${apiBase}/purchases/${purchaseId}`);
    if (viewerId != null) await request.delete(`${apiBase}/users/${viewerId}`);
  }
});

async function login(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

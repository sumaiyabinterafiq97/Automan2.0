import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { openAdminSession } from '../staff-api';

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
  let auth: { Authorization: string } | undefined;

  try {
    auth = await openAdminSession(request, apiBase, admin);
    const createdViewer = await request.post(`${apiBase}/users`, { data: { ...viewer, role: 'VIEWER' }, headers: auth });
    expect(createdViewer.ok(), await createdViewer.text()).toBeTruthy();
    viewerId = Number((await createdViewer.json()).id);

    const purchase = await request.post(`${apiBase}/purchases`, {
      headers: auth,
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
    await expect(page.locator('#quickPurchaseBtn')).toBeHidden();
    await page.locator('#shipmentHeader').click();
    await expect(page.locator('#carBookingBtn')).toBeHidden();
    await expect(page.locator('#shipmentStatusBtn')).toBeVisible();
    await page.locator('#invoiceNavHeader').click();
    await expect(page.locator('#anInvoiceBtn')).toBeHidden();
    await expect(page.locator('#invoiceHistorySidebarBtn')).toBeVisible();
    await page.goto('/booking');
    await expect(page).toHaveURL(/\/purchase$/);
    await page.goto('/invoice');
    await expect(page).toHaveURL(/\/purchase$/);
    await expect(page.getByText('Role: VIEWER')).toBeVisible();
    await expect(page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${chassis}"]`)).toHaveCount(0);
  } finally {
    if (auth && purchaseId != null) await request.delete(`${apiBase}/purchases/${purchaseId}`, { headers: auth });
    if (auth && viewerId != null) await request.delete(`${apiBase}/users/${viewerId}`, { headers: auth });
  }
});

async function login(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

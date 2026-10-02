import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('purchase date filter shows only the purchase inside the chosen day', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const inRangeChassis = `IN${stamp}`.slice(0, 20);
  const outRangeChassis = `OUT${stamp}`.slice(0, 20);
  const admin = {
    email: `datefilter-${stamp.toLowerCase()}@example.com`,
    name: 'Date Filter Admin',
    password: 'Browser!Test1',
  };
  const purchaseIds: number[] = [];

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    purchaseIds.push(await createPurchase(request, inRangeChassis, 'September 30, 2026'));
    purchaseIds.push(await createPurchase(request, outRangeChassis, 'January 15, 2026'));

    await login(page, admin);
    await expect(page.locator('#purchaseTable')).toContainText(inRangeChassis);
    await expect(page.locator('#purchaseTable')).toContainText(outRangeChassis);

    await page.locator('#purchaseDateQuickFilterBtn').click({ timeout: 30_000 });
    const menu = page.getByRole('dialog', { name: 'Filter by purchase date' });
    await expect(menu).toBeVisible();
    await menu.locator('#purchaseDateQuickFilterFromText').fill('09/30/2026');
    await menu.locator('#purchaseDateQuickFilterToText').fill('09/30/2026');
    await menu.locator('#purchaseDateQuickFilterApplyBtn').click();

    await expect(page.locator('#purchaseTable')).toContainText(inRangeChassis);
    await expect(page.locator('#purchaseTable')).not.toContainText(outRangeChassis);

    await page.locator('#purchaseDateQuickFilterBtn').click({ timeout: 30_000 });
    await expect(menu).toBeVisible();
    await menu.locator('#purchaseDateQuickClearBtn').click();

    await expect(page.locator('#purchaseTable')).toContainText(inRangeChassis);
    await expect(page.locator('#purchaseTable')).toContainText(outRangeChassis);
  } finally {
    for (const id of purchaseIds) await request.delete(`${apiBase}/purchases/${id}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createPurchase(request: APIRequestContext, chassis: string, date: string) {
  const created = await request.post(`${apiBase}/purchases`, {
    data: {
      date,
      chassis,
      carName: `Date ${chassis}`,
      auctionHouse: 'Supplier',
      country: 'Kenya',
      price: '125000',
      local: false,
      rixoConfirmed: 'TRUE',
    },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  return Number((await created.json()).id);
}

async function login(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

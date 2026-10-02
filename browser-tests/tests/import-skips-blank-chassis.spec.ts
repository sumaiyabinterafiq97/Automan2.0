import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('csv import saves the valid chassis and skips blank or dash chassis', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const importedChassis = `IM${stamp}`.slice(0, 20);
  const keptChassis = `KP${stamp}`.slice(0, 20);
  const importedName = `Imported ${stamp}`;
  const keptName = `Kept ${stamp}`;
  const blankName = `No Chassis ${stamp}`;
  const dashName = `Dash Chassis ${stamp}`;
  const admin = {
    email: `import-${stamp.toLowerCase()}@example.com`,
    name: 'Import Admin',
    password: 'Browser!Test1',
  };
  let keptId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    keptId = await createPurchase(request, keptChassis, keptName);

    await login(page, admin);
    await page.getByRole('button', { name: 'Open menu' }).click();
    await page.locator('#importBtn').click();
    await expect(page.getByRole('heading', { name: 'Import CSV' })).toBeVisible();

    const csv = [
      'CHASSIS,CAR NAME,SUPPLIER NAME,COUNTRY,DATE',
      `${importedChassis},${importedName},USS Tokyo,Bangladesh,2026-04-01`,
      `,${blankName},USS Tokyo,Bangladesh,2026-04-01`,
      `-,${dashName},USS Tokyo,Bangladesh,2026-04-01`,
    ].join('\n');
    await page.locator('#csvFile').setInputFiles({
      name: 'purchases.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csv),
    });
    await expect(page.locator('#modalImportBtn')).toBeEnabled();
    await page.locator('#modalImportBtn').click();

    await expect(page.locator('#message')).toHaveText('Successfully imported 1 purchases!');
    await expect(page.locator('#importModal')).toHaveCount(0);
    await expect(page.locator('#purchaseTable')).toContainText(importedChassis);
    await expect(page.locator('#purchaseTable')).toContainText(importedName);
    await expect(page.locator('#purchaseTable')).toContainText(keptChassis);
    await expect(page.locator('#purchaseTable')).toContainText(keptName);
    await expect(page.locator('#purchaseTable')).not.toContainText(blankName);
    await expect(page.locator('#purchaseTable')).not.toContainText(dashName);

    await page.locator('#purchaseSearchInput').fill(blankName);
    await page.locator('#purchaseSearchInput').press('Enter');
    await expect(page.locator('#purchaseTable')).toContainText('No matches');
    await page.locator('#purchaseSearchInput').fill(dashName);
    await page.locator('#purchaseSearchInput').press('Enter');
    await expect(page.locator('#purchaseTable')).toContainText('No matches');
    await page.locator('#purchaseSearchInput').fill(keptChassis);
    await page.locator('#purchaseSearchInput').press('Enter');
    await expect(page.locator('#purchaseTable')).toContainText(keptChassis);
    await expect(page.locator('#purchaseTable')).toContainText(keptName);
  } finally {
    if (keptId != null) await request.delete(`${apiBase}/purchases/${keptId}`);
    await deletePurchaseByChassis(request, importedChassis);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createPurchase(request: APIRequestContext, chassis: string, carName: string) {
  const created = await request.post(`${apiBase}/purchases`, {
    data: {
      date: '09/30/2026',
      chassis,
      carName,
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

async function deletePurchaseByChassis(request: APIRequestContext, chassis: string) {
  const check = await request.get(`${apiBase}/purchases/check-duplicate`, { params: { chassis } });
  if (!check.ok()) return;
  const body = (await check.json()) as { duplicate?: boolean; existingPurchaseId?: number };
  if (body.duplicate && body.existingPurchaseId != null) {
    await request.delete(`${apiBase}/purchases/${body.existingPurchaseId}`);
  }
}

async function login(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

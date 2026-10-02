import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('deleting one purchase leaves the other purchase in the list', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const removedChassis = `DA${stamp}`.slice(0, 20);
  const keptChassis = `DB${stamp}`.slice(0, 20);
  const removedName = `Remove ${stamp}`;
  const keptName = `Keep ${stamp}`;
  const admin = {
    email: `purchasedel-${stamp.toLowerCase()}@example.com`,
    name: 'Purchase Delete Admin',
    password: 'Browser!Test1',
  };
  const purchaseIds: number[] = [];

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    purchaseIds.push(await createPurchase(request, removedChassis, removedName));
    purchaseIds.push(await createPurchase(request, keptChassis, keptName));

    await login(page, admin);
    await expect(page.locator('#purchaseTable')).toContainText(removedChassis);
    await expect(page.locator('#purchaseTable')).toContainText(keptChassis);

    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${removedChassis}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/edit/${removedChassis}$`));
    const confirm = new Promise<string>((resolve) => {
      page.once('dialog', async (dialog) => {
        const message = dialog.message();
        await dialog.accept();
        resolve(message);
      });
    });
    await page.locator('#deleteBtn').click();
    expect(await confirm).toBe('Are you sure you want to delete this purchase?');

    await expect(page).toHaveURL(/\/purchase$/);
    await expect(page.locator('#purchaseTable')).toContainText(keptChassis);
    await expect(page.locator('#purchaseTable')).toContainText(keptName);
    await expect(page.locator('#purchaseTable')).not.toContainText(removedChassis);

    await page.locator('#purchaseSearchInput').fill(removedChassis);
    await page.locator('#purchaseSearchInput').press('Enter');
    await expect(page.locator('#purchaseTable')).toContainText('No matches');

    await page.locator('#purchaseSearchInput').fill(keptChassis);
    await page.locator('#purchaseSearchInput').press('Enter');
    await expect(page.locator('#purchaseTable')).toContainText(keptChassis);
    await expect(page.locator('#purchaseTable')).toContainText(keptName);
    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${keptChassis}"]`).click();
    await expect(page.locator('#editCarNameInput')).toHaveValue(keptName);
  } finally {
    for (const id of purchaseIds) await request.delete(`${apiBase}/purchases/${id}`);
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

async function login(page: Page, admin: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(admin.email);
  await page.locator('#si_pass').fill(admin.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

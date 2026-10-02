import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('deleting one stock location leaves the other stock location', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const removed = { stockLocation: `SLDROP${stamp}`, pol: `PD${stamp}` };
  const kept = { stockLocation: `SLKEEP${stamp}`, pol: `PK${stamp}` };
  const admin = {
    email: `stockmapdel-${stamp.toLowerCase()}@example.com`,
    name: 'Stock Map Delete Admin',
    password: 'Browser!Test1',
  };
  let removedId: number | null = null;
  let keptId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    removedId = await createStock(request, removed);
    keptId = await createStock(request, kept);

    await login(page, admin);
    await openMenu(page, '#masterStockLocationMapBtn', '#masterMapHeader');
    await expect(page.getByRole('heading', { name: 'Stock Location Map' })).toBeVisible();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();

    await page.locator('#slmSearchInput').fill(removed.stockLocation, { timeout: 30_000 });
    await expect(page.locator('#slmTable')).toContainText(removed.stockLocation);
    await expect(page.locator('#slmTable')).toContainText(removed.pol);
    await expect(page.locator('#slmTable')).not.toContainText(kept.stockLocation);
    await page.locator('#slmTable').getByRole('button', { name: 'Edit' }).click({ timeout: 30_000 });
    await expect(page.getByRole('heading', { name: 'Edit Stock Location' })).toBeVisible();

    await page.locator('#deleteSlmBtn').click({ timeout: 30_000 });
    const confirm = page.locator('#rixoMappingDeleteConfirmOverlay');
    await expect(confirm.getByRole('heading', { name: 'Delete stock location' })).toBeVisible();
    await expect(confirm).toContainText(
      'Delete this stock location map row? This does not change Booking, Supplier Map, or Master Set lists.',
    );
    await confirm.locator('#rixoMappingDeleteConfirmOk').click({ timeout: 30_000 });

    await expect(page.locator('#message')).toHaveText('Deleted');
    await expect(page.locator('#slmModal')).toHaveCount(0);
    await expect(page.locator('#rixoMappingDeleteConfirmOverlay')).toHaveCount(0);
    await expect(page.locator('#slmTable')).toContainText('No matches for your search.');
    await expect(page.locator('#slmTable')).not.toContainText(removed.stockLocation);

    await page.locator('#slmSearchClearBtn').click({ timeout: 30_000 });
    await page.locator('#slmSearchInput').fill(kept.stockLocation, { timeout: 30_000 });
    await expect(page.locator('#slmTable')).toContainText(kept.stockLocation);
    await expect(page.locator('#slmTable')).toContainText(kept.pol);
    await expect(page.locator('#slmTable')).not.toContainText(removed.stockLocation);
    await expect(page.locator('#slmTable')).not.toContainText(removed.pol);
    removedId = null;
  } finally {
    if (removedId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${removedId}`);
    if (keptId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${keptId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createStock(request: APIRequestContext, row: { stockLocation: string; pol: string }) {
  const created = await request.post(`${apiBase}/stock-location-map/mappings/add`, { data: row });
  expect(created.ok(), await created.text()).toBeTruthy();
  return Number((await created.json()).data.id);
}

async function openMenu(page: Page, buttonId: string, sectionHeaderId: string) {
  await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
  const header = page.locator(sectionHeaderId);
  if ((await header.getAttribute('aria-expanded')) !== 'true') {
    await header.click({ timeout: 30_000 });
  }
  await page.locator(buttonId).scrollIntoViewIfNeeded({ timeout: 30_000 });
  await page.locator(buttonId).click({ timeout: 30_000 });
}

async function login(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

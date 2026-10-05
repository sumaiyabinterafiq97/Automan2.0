import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('opening Edit keeps the saved stock location, and a later supplier change can still open the chooser', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const savedSupplier = `SLSA${stamp}`.slice(0, 24);
  const otherSupplier = `SLSB${stamp}`.slice(0, 24);
  const savedStock = `STOCKA1${stamp}`.slice(0, 24);
  const otherSavedStock = `STOCKA2${stamp}`.slice(0, 24);
  const firstChoice = `STOCKB1${stamp}`.slice(0, 24);
  const secondChoice = `STOCKB2${stamp}`.slice(0, 24);
  const chassis = `SL${stamp}`.slice(0, 20);
  const admin = {
    email: `stockloc-${stamp.toLowerCase()}@example.com`,
    name: 'Stock Location Admin',
    password: 'Browser!Test1',
  };
  const mappingIds: number[] = [];
  let purchaseId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();

    mappingIds.push(await addMapping(request, savedSupplier, savedStock, `RIXO${stamp}`, `${savedStock}-V`));
    mappingIds.push(await addMapping(request, savedSupplier, otherSavedStock, `RIXO${stamp}`, `${otherSavedStock}-V`));
    const sharedVenue = `VENUEB${stamp}`.slice(0, 24);
    mappingIds.push(await addMapping(request, otherSupplier, firstChoice, `RIXO${stamp}`, sharedVenue));
    mappingIds.push(await addMapping(request, otherSupplier, secondChoice, `RIXO${stamp}`, sharedVenue));

    purchaseId = await createPurchase(request, chassis, savedSupplier, savedStock);

    await login(page, admin);
    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${chassis}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/edit/${chassis}$`));
    await expect(page.getByRole('heading', { name: 'Edit Purchase' })).toBeVisible();
    await expect(page.locator('#editStockLocationInput')).toHaveValue(savedStock);
    await page.waitForFunction(() => {
      const flags = window as Window & { __editPurchaseHydrating?: boolean };
      return flags.__editPurchaseHydrating !== true;
    });
    await page.waitForTimeout(2000);

    await expect(page.locator('.chassis-field-selection-backdrop')).toHaveCount(0);
    await expect(page.locator('#editStockLocationInput')).toHaveValue(savedStock);
    await expect(page.locator('#editAuctionNameInput')).toHaveValue(savedSupplier);

    await page.evaluate((supplier) => {
      const selectId = 'editAuctionName';
      const select = document.getElementById(selectId);
      const input = document.getElementById(`${selectId}Input`);
      if (select instanceof HTMLSelectElement) {
        const exists = Array.from(select.options).some((option) => option.value === supplier);
        if (!exists) {
          const option = document.createElement('option');
          option.value = supplier;
          option.textContent = supplier;
          select.appendChild(option);
        }
        select.value = supplier;
      }
      if (input instanceof HTMLInputElement) input.value = supplier;
      const sync = (window as Window & { syncComboboxInput?: (id: string) => void }).syncComboboxInput;
      if (typeof sync === 'function') sync(selectId);
    }, otherSupplier);

    const chooser = page.locator('.chassis-field-selection-backdrop');
    await expect(chooser).toBeVisible();
    await expect(chooser).toContainText('Select Stock Location');
    await expect(chooser).toContainText(firstChoice);
    await expect(chooser).toContainText(secondChoice);
  } finally {
    if (purchaseId != null) await request.delete(`${apiBase}/purchases/${purchaseId}`);
    for (const id of mappingIds) await request.delete(`${apiBase}/rixo/mappings/${id}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function addMapping(
  request: APIRequestContext,
  auctionHouse: string,
  stockLocation: string,
  rixoCompany: string,
  venueId: string,
) {
  const created = await request.post(`${apiBase}/rixo/mappings/add`, {
    data: { auctionHouse, stockLocation, rixoCompany, venueId },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const body = await created.json();
  const id = Number(body?.data?.id);
  expect(Number.isInteger(id) && id > 0, JSON.stringify(body)).toBeTruthy();
  return id;
}

async function createPurchase(request: APIRequestContext, chassis: string, auctionHouse: string, stockLocation: string) {
  const created = await request.post(`${apiBase}/purchases`, {
    data: {
      date: '09/30/2026',
      chassis,
      carName: `Stock ${chassis}`,
      auctionHouse,
      stockLocation,
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

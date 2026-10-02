import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('an invoice with no invoice number is not saved', async ({ page, request }) => {
  test.setTimeout(180_000);
  const fixture = uniqueFixture('INVN');
  const ids = await seedBookedPurchase(request, fixture);

  try {
    await login(page, fixture.admin);
    await shipPurchase(page, fixture);

    await openMenu(page, '#anInvoiceBtn', '#invoiceNavHeader');
    await page.locator('#closeSidebar').click();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await expect(page.getByRole('heading', { name: 'Create Local Customer Invoice' })).toBeVisible();
    await page.waitForFunction((name) => {
      const select = document.querySelector('#invoiceClient');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
    }, fixture.clientName);
    await page.locator('#invoiceClient').selectOption(fixture.clientName);
    await expect(page.locator('#invoiceListTableBody')).toContainText(fixture.chassis);
    await expect(page.locator('#invoiceNumber')).toHaveValue('');

    await page.locator('#invoiceSaveBtn').click();
    await expect(page.locator('#message')).toHaveText('Please enter an invoice number');
    await expect(page.getByRole('heading', { name: 'Create Local Customer Invoice' })).toBeVisible();
    await expect(page.getByRole('dialog', { name: 'Saved' })).toHaveCount(0);
    await expect(page.locator('#invoiceListTableBody')).toContainText(fixture.chassis);

    await openMenu(page, '#invoiceHistorySidebarBtn', '#invoiceNavHeader');
    await expect(page.getByRole('heading', { name: 'Invoice History' })).toBeVisible();
    await page.locator('#invoiceHistorySearchInput').fill(fixture.chassis);
    await expect(page.locator('#invoiceHistoryTable')).toContainText('No matches');
    await expect(page.locator('#invoiceHistoryTable')).not.toContainText(fixture.chassis);
  } finally {
    await cleanup(request, ids);
  }
});

type Fixture = {
  stamp: string;
  chassis: string;
  country: string;
  stockLocation: string;
  pol: string;
  clientName: string;
  supplierName: string;
  vessel: string;
  bookingNo: string;
  admin: { email: string; name: string; password: string };
};

type Ids = { purchaseId: number | null; clientId: number | null; stockMapId: number | null; chassis: string };

function uniqueFixture(prefix: string): Fixture {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
  return {
    stamp,
    chassis: `${prefix}${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 20),
    country: 'Kenya',
    stockLocation: `YARD${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 24),
    pol: `PORT${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16),
    clientName: `Client ${stamp}`,
    supplierName: `Supplier ${stamp}`,
    vessel: `Vessel ${stamp}`,
    bookingNo: `${Date.now()}${Math.floor(Math.random() * 90 + 10)}`,
    admin: {
      email: `blank-${stamp.toLowerCase()}@example.com`,
      name: 'Invoice Admin',
      password: 'Browser!Test1',
    },
  };
}

async function seedBookedPurchase(request: APIRequestContext, fixture: Fixture): Promise<Ids> {
  const setup = await request.post(`${apiBase}/auth/setup`, { data: fixture.admin });
  expect(setup.ok(), await setup.text()).toBeTruthy();
  const client = await request.post(`${apiBase}/clients`, {
    data: {
      clientNumber: `C${fixture.bookingNo}`,
      clientName: fixture.clientName,
      creditLimit: 100000000,
      currency: 'JPY',
      status: 'ACTIVE',
    },
  });
  expect(client.ok(), await client.text()).toBeTruthy();
  const stockMap = await request.post(`${apiBase}/stock-location-map/mappings/add`, {
    data: { stockLocation: fixture.stockLocation, pol: fixture.pol },
  });
  expect(stockMap.ok(), await stockMap.text()).toBeTruthy();
  const purchase = await request.post(`${apiBase}/purchases`, {
    data: {
      date: '09/30/2026',
      chassis: fixture.chassis,
      carName: `Invoice Car ${fixture.stamp}`,
      clientName: fixture.clientName,
      auctionHouse: fixture.supplierName,
      stockLocation: fixture.stockLocation,
      country: fixture.country,
      price: '125000',
      local: false,
      rixoConfirmed: 'TRUE',
    },
  });
  expect(purchase.ok(), await purchase.text()).toBeTruthy();
  return {
    purchaseId: Number((await purchase.json()).id),
    clientId: Number((await client.json()).id),
    stockMapId: Number((await stockMap.json()).data.id),
    chassis: fixture.chassis,
  };
}

async function login(page: Page, admin: Fixture['admin']) {
  await page.goto('/login');
  await page.locator('#si_email').fill(admin.email);
  await page.locator('#si_pass').fill(admin.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

async function shipPurchase(page: Page, fixture: Fixture) {
  await openMenu(page, '#carBookingBtn', '#shipmentHeader');
  await expect(page.getByRole('heading', { name: 'CREATE SHIPPING SCHEDULE' })).toBeVisible();
  await page.waitForFunction((name) => {
    const select = document.querySelector('#consigneeCountry');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
  }, fixture.country);
  await page.locator('#bookingCountryFabTrigger').click();
  await page.locator(`#bookingCountryFabActions [role="option"][data-value="${fixture.country}"]`).click();
  await page.waitForFunction((stock) => {
    const select = document.querySelector('#bookingStockLocations');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === stock);
  }, fixture.stockLocation);
  await page.locator('#bookingStockLocationsInput').fill(fixture.stockLocation);
  await page.locator('#bookingStockLocationsInput').press('Enter');
  await page.waitForFunction((port) => {
    const select = document.querySelector('#polPort');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === port);
  }, fixture.pol);
  await page.locator('#bookingPolFabTrigger').click();
  await page.locator(`#bookingPolFabActions [role="option"][data-value="${fixture.pol}"]`).click();
  await page.locator('#etdDateText').fill('09/30/2026');
  await page.locator('#etdDateText').blur();
  await page.locator('#bookingNo').fill(fixture.bookingNo);
  await page.locator('#vesselSelect').fill(fixture.vessel);
  await page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${fixture.chassis}"]`).check();
  await page.locator('#calculateBtn').click();
  await expect(page.getByRole('heading', { name: 'C&F Calculation' })).toBeVisible();
  await page.locator('#saveCnfBtn').click();
  const saved = page.getByRole('dialog', { name: 'Saved' });
  await expect(saved).toBeVisible();
  await saved.getByRole('button', { name: 'OK' }).click();
}

async function openMenu(page: Page, buttonId: string, sectionHeaderId?: string) {
  await page.getByRole('button', { name: 'Open menu' }).click();
  if (sectionHeaderId) {
    const header = page.locator(sectionHeaderId);
    if ((await header.getAttribute('aria-expanded')) !== 'true') {
      await header.click();
    }
  }
  await page.locator(buttonId).click();
}

async function cleanup(request: APIRequestContext, ids: Ids) {
  const history = await request.get(`${apiBase}/shipping-history`);
  if (history.ok()) {
    const rows = await history.json();
    const shippingIds = (Array.isArray(rows) ? rows : [])
      .filter((row) => String(row.chassis || '').includes(ids.chassis))
      .map((row) => Number(row.id))
      .filter((id) => Number.isInteger(id) && id > 0);
    if (shippingIds.length > 0) {
      await request.post(`${apiBase}/shipping-history/delete-batch`, { data: { ids: shippingIds } });
    }
  }
  if (ids.purchaseId != null) await request.delete(`${apiBase}/purchases/${ids.purchaseId}`);
  if (ids.stockMapId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${ids.stockMapId}`);
  if (ids.clientId != null) await request.delete(`${apiBase}/clients/${ids.clientId}`);
}

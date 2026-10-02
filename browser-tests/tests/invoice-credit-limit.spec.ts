import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('an invoice over the client credit limit is not saved and does not change the balance', async ({ page, request }) => {
  test.setTimeout(180_000);
  const stamp = uniqueStamp();
  const chassis = `CR${stamp}`.slice(0, 20);
  const clientName = `Client ${stamp}`;
  const invoiceNumber = `INV${stamp}`;
  const bookingNo = `${Date.now()}${Math.floor(Math.random() * 90 + 10)}`;
  const admin = {
    email: `credit-${stamp.toLowerCase()}@example.com`,
    name: 'Credit Admin',
    password: 'Browser!Test1',
  };
  const ids = { purchaseId: null as number | null, clientId: null as number | null, stockMapId: null as number | null, chassis };

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    ids.clientId = await createClient(request, `C${bookingNo}`, clientName, 100000);
    ids.stockMapId = await createStock(request, stamp);
    ids.purchaseId = await createPurchase(request, {
      chassis,
      clientName,
      stockLocation: stockOf(stamp),
      country: 'Kenya',
      price: '125000',
    });

    await login(page, admin);
    await ship(page, {
      country: 'Kenya',
      stockLocation: stockOf(stamp),
      pol: polOf(stamp),
      bookingNo,
      vessel: `Vessel ${stamp}`,
      chassis,
    });

    await openMenu(page, '#anInvoiceBtn', '#invoiceNavHeader');
    await page.locator('#closeSidebar').click();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await page.waitForFunction((name) => {
      const select = document.querySelector('#invoiceClient');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
    }, clientName);
    await page.locator('#invoiceClient').selectOption(clientName);
    await expect(page.locator('#invoiceListTableBody')).toContainText(chassis);
    await expect(page.locator('#invoiceTotalAmount')).toHaveText('Total Amount: ¥125,000');
    await page.locator('#invoiceNumber').fill(invoiceNumber);
    await page.locator('#invoiceSaveBtn').click();

    const blocked = page.getByRole('dialog', { name: 'Credit limit exceeded' });
    await expect(blocked).toContainText('This invoice cannot be saved because the client would exceed their credit limit.');
    await expect(blocked).toContainText('Invoice was not saved.');
    await expect(blocked).toContainText(clientName);
    await expect(page.getByRole('dialog', { name: 'Saved' })).toHaveCount(0);
    await blocked.getByRole('button', { name: 'OK' }).click();

    await openMenu(page, '#invoiceHistorySidebarBtn', '#invoiceNavHeader');
    await page.locator('#invoiceHistorySearchInput').fill(invoiceNumber);
    await expect(page.locator('#invoiceHistoryTable')).toContainText('No matches');
    await expect(page.locator('#invoiceHistoryTable')).not.toContainText(invoiceNumber);

    await openMenu(page, '#clientTransactionsSidebarBtn');
    await page.locator('#clientSearchInput').fill(clientName);
    await expect(page.locator('#clientListTable').getByText('¥0', { exact: true })).toBeVisible();
    await page.locator('#clientListTable').getByText(clientName, { exact: true }).click();
    await expect(page.locator('#currentBalanceValue')).toHaveText('¥0');
    await expect(page.locator('#clientEventsTable')).toContainText('No ledger entries yet');
    await expect(page.locator('#clientEventsTable')).not.toContainText(invoiceNumber);
  } finally {
    await request.post(`${apiBase}/invoice-history/batch-delete`, { data: { invoiceNumbers: [invoiceNumber] } });
    await deleteShipping(request, chassis);
    if (ids.purchaseId != null) await request.delete(`${apiBase}/purchases/${ids.purchaseId}`);
    if (ids.stockMapId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${ids.stockMapId}`);
    if (ids.clientId != null) await request.delete(`${apiBase}/clients/${ids.clientId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

function stockOf(stamp: string) {
  return `YARD${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 24);
}

function polOf(stamp: string) {
  return `PORT${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16);
}

async function createClient(request: APIRequestContext, clientNumber: string, clientName: string, creditLimit: number) {
  const created = await request.post(`${apiBase}/clients`, {
    data: { clientNumber, clientName, creditLimit, currency: 'JPY', status: 'ACTIVE' },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  return Number((await created.json()).id);
}

async function createStock(request: APIRequestContext, stamp: string) {
  const created = await request.post(`${apiBase}/stock-location-map/mappings/add`, {
    data: { stockLocation: stockOf(stamp), pol: polOf(stamp) },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  return Number((await created.json()).data.id);
}

async function createPurchase(
  request: APIRequestContext,
  purchase: { chassis: string; clientName: string; stockLocation: string; country: string; price: string },
) {
  const created = await request.post(`${apiBase}/purchases`, {
    data: {
      date: '09/30/2026',
      carName: `Car ${purchase.chassis}`,
      auctionHouse: 'Supplier',
      local: false,
      rixoConfirmed: 'TRUE',
      ...purchase,
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

async function ship(
  page: Page,
  trip: { country: string; stockLocation: string; pol: string; bookingNo: string; vessel: string; chassis: string },
) {
  await openMenu(page, '#carBookingBtn', '#shipmentHeader');
  await expect(page.getByRole('heading', { name: 'CREATE SHIPPING SCHEDULE' })).toBeVisible();
  await page.waitForFunction((name) => {
    const select = document.querySelector('#consigneeCountry');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
  }, trip.country);
  await page.locator('#bookingCountryFabTrigger').click();
  await page.locator(`#bookingCountryFabActions [role="option"][data-value="${trip.country}"]`).click();
  await page.waitForFunction((stock) => {
    const select = document.querySelector('#bookingStockLocations');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === stock);
  }, trip.stockLocation);
  await page.locator('#bookingStockLocationsInput').fill(trip.stockLocation);
  await page.locator('#bookingStockLocationsInput').press('Enter');
  await page.waitForFunction((port) => {
    const select = document.querySelector('#polPort');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === port);
  }, trip.pol);
  await page.locator('#bookingPolFabTrigger').click();
  await page.locator(`#bookingPolFabActions [role="option"][data-value="${trip.pol}"]`).click();
  await page.locator('#etdDateText').fill('09/30/2026');
  await page.locator('#etdDateText').blur();
  await page.locator('#bookingNo').fill(trip.bookingNo);
  await page.locator('#vesselSelect').fill(trip.vessel);
  await page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${trip.chassis}"]`).check();
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

async function deleteShipping(request: APIRequestContext, chassis: string) {
  const history = await request.get(`${apiBase}/shipping-history`);
  if (!history.ok()) return;
  const rows = await history.json();
  const shippingIds = (Array.isArray(rows) ? rows : [])
    .filter((row) => String(row.chassis || '').includes(chassis))
    .map((row) => Number(row.id))
    .filter((id) => Number.isInteger(id) && id > 0);
  if (shippingIds.length > 0) {
    await request.post(`${apiBase}/shipping-history/delete-batch`, { data: { ids: shippingIds } });
  }
}

import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('invoicing one client charges only that client and leaves the other client uninvoiced', async ({ page, request }) => {
  test.setTimeout(180_000);
  const stamp = uniqueStamp();
  const invoicedChassis = `CA${stamp}`.slice(0, 20);
  const otherChassis = `CB${stamp}`.slice(0, 20);
  const invoicedClient = `Alpha ${stamp}`;
  const otherClient = `Beta ${stamp}`;
  const invoicedVessel = `VA${stamp}`;
  const otherVessel = `VB${stamp}`;
  const invoiceNumber = `INV${stamp}`;
  const country = 'Kenya';
  const stockLocation = `YARD${stamp}`.slice(0, 24);
  const pol = `PORT${stamp}`.slice(0, 16);
  const bookingA = `${Date.now()}11`;
  const bookingB = `${Date.now()}22`;
  const admin = {
    email: `clients-${stamp.toLowerCase()}@example.com`,
    name: 'Two Client Admin',
    password: 'Browser!Test1',
  };
  const purchaseIds: number[] = [];
  let invoicedClientId: number | null = null;
  let otherClientId: number | null = null;
  let stockMapId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    invoicedClientId = await createClient(request, `C${bookingA}`, invoicedClient, 0);
    otherClientId = await createClient(request, `D${bookingB}`, otherClient, 80);
    const stockMap = await request.post(`${apiBase}/stock-location-map/mappings/add`, {
      data: { stockLocation, pol },
    });
    expect(stockMap.ok(), await stockMap.text()).toBeTruthy();
    stockMapId = Number((await stockMap.json()).data.id);
    purchaseIds.push(await createPurchase(request, invoicedChassis, invoicedClient, stockLocation, country));
    purchaseIds.push(await createPurchase(request, otherChassis, otherClient, stockLocation, country));

    await login(page, admin);
    await ship(page, { country, stockLocation, pol, bookingNo: bookingA, vessel: invoicedVessel, chassis: invoicedChassis });
    await shipSecond(page, { country, stockLocation, pol, bookingNo: bookingB, vessel: otherVessel, chassis: otherChassis, alreadyShipped: invoicedChassis });

    await openMenu(page, '#anInvoiceBtn', '#invoiceNavHeader');
    await page.locator('#closeSidebar').click();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await expect(page.getByRole('heading', { name: 'Create Local Customer Invoice' })).toBeVisible();
    await waitForOption(page, '#invoiceClient', invoicedClient);
    await page.locator('#invoiceClient').selectOption(invoicedClient);
    const invoicedLines = page.locator('#invoiceListTableBody');
    await expect(invoicedLines).toContainText(invoicedChassis);
    await expect(invoicedLines).not.toContainText(otherChassis);
    await expect(page.locator('#invoiceTotalAmount')).toHaveText('Total Amount: ¥125,000');
    await page.locator('#invoiceNumber').fill(invoiceNumber);
    await page.locator('#invoiceSaveBtn').click();
    await dismissSaved(page);

    await openMenu(page, '#invoiceHistorySidebarBtn', '#invoiceNavHeader');
    await page.locator('#invoiceHistorySearchInput').fill(invoiceNumber);
    const history = page.locator('#invoiceHistoryTableBody');
    await expect(history.locator('tr')).toHaveCount(1);
    await expect(history).toContainText(invoiceNumber);
    await expect(history).toContainText(invoicedClient);
    await expect(history).toContainText(invoicedChassis);
    await expect(history).toContainText('¥125,000');
    await expect(history).not.toContainText(otherClient);
    await expect(history).not.toContainText(otherChassis);

    await openClient(page, invoicedClient);
    await expect(page.locator('#currentBalanceValue')).toHaveText('−¥125000');
    await expect(page.locator('#clientEventsTable')).toContainText(invoiceNumber);
    await expect(page.locator('#clientEventsTable')).toContainText('¥125000');

    await openMenu(page, '#clientTransactionsSidebarBtn');
    await page.locator('#clientSearchInput').fill(otherClient);
    await expect(page.locator('#clientListTable').getByText('+¥80', { exact: true })).toBeVisible();
    await expect(page.locator('#clientListTable')).not.toContainText(invoicedClient);
    await expect(page.locator('#clientListTable')).not.toContainText(invoiceNumber);
    await page.locator('#clientListTable').getByText(otherClient, { exact: true }).click();
    await expect(page.locator('#currentBalanceValue')).toHaveText('+¥80');
    await expect(page.locator('#clientEventsTable')).not.toContainText(invoiceNumber);

    await openMenu(page, '#anInvoiceBtn', '#invoiceNavHeader');
    await page.locator('#closeSidebar').click();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await waitForOption(page, '#invoiceClient', otherClient);
    await page.locator('#invoiceClient').selectOption(otherClient);
    const otherLines = page.locator('#invoiceListTableBody');
    await expect(otherLines).toContainText(otherChassis);
    await expect(otherLines).not.toContainText(invoicedChassis);
    await expect(page.locator('#invoiceTotalAmount')).toHaveText('Total Amount: ¥125,000');

    await openPurchase(page, invoicedChassis);
    await expect(page.locator('input[name="editStatusInvoiceConfirmed"][value="TRUE"]')).toBeChecked();
    await openPurchase(page, otherChassis);
    await expect(page.locator('input[name="editStatusInvoiceConfirmed"][value="FALSE"]')).toBeChecked();
    await expect(page.locator('input[name="editStatusBookingRequested"][value="TRUE"]')).toBeChecked();
  } finally {
    await request.post(`${apiBase}/invoice-history/batch-delete`, { data: { invoiceNumbers: [invoiceNumber] } });
    await deleteShipping(request, [invoicedChassis, otherChassis]);
    for (const id of purchaseIds) await request.delete(`${apiBase}/purchases/${id}`);
    if (stockMapId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${stockMapId}`);
    if (invoicedClientId != null) await request.delete(`${apiBase}/clients/${invoicedClientId}`);
    if (otherClientId != null) await request.delete(`${apiBase}/clients/${otherClientId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createClient(request: APIRequestContext, clientNumber: string, clientName: string, currentBalance: number) {
  const created = await request.post(`${apiBase}/clients`, {
    data: { clientNumber, clientName, currentBalance, creditLimit: 100000000, currency: 'JPY', status: 'ACTIVE' },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  return Number((await created.json()).id);
}

async function createPurchase(
  request: APIRequestContext,
  chassis: string,
  clientName: string,
  stockLocation: string,
  country: string,
) {
  const created = await request.post(`${apiBase}/purchases`, {
    data: {
      date: '09/30/2026',
      chassis,
      carName: `Car ${chassis}`,
      clientName,
      auctionHouse: 'Supplier',
      stockLocation,
      country,
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

async function ship(
  page: Page,
  trip: { country: string; stockLocation: string; pol: string; bookingNo: string; vessel: string; chassis: string },
) {
  await openMenu(page, '#carBookingBtn', '#shipmentHeader');
  await waitForOption(page, '#consigneeCountry', trip.country);
  await page.locator('#bookingCountryFabTrigger').click();
  await page.locator(`#bookingCountryFabActions [role="option"][data-value="${trip.country}"]`).click();
  await waitForOption(page, '#bookingStockLocations', trip.stockLocation);
  await page.locator('#bookingStockLocationsInput').fill(trip.stockLocation);
  await page.locator('#bookingStockLocationsInput').press('Enter');
  await waitForOption(page, '#polPort', trip.pol);
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
  await dismissSaved(page);
}

async function shipSecond(
  page: Page,
  trip: { country: string; stockLocation: string; pol: string; bookingNo: string; vessel: string; chassis: string; alreadyShipped: string },
) {
  await openMenu(page, '#carBookingBtn', '#shipmentHeader');
  await expect(page.getByRole('heading', { name: 'CREATE SHIPPING SCHEDULE' })).toBeVisible();
  await waitForOption(page, '#consigneeCountry', trip.country);
  await page.locator('#bookingCountryFabTrigger').click();
  await page.locator(`#bookingCountryFabActions [role="option"][data-value="${trip.country}"]`).click();
  await waitForOption(page, '#bookingStockLocations', trip.stockLocation);
  await page.locator('#bookingStockLocationsInput').fill(trip.stockLocation);
  await page.locator('#bookingStockLocationsInput').press('Enter');
  await waitForOption(page, '#polPort', trip.pol);
  await page.locator('#bookingPolFabTrigger').click();
  await page.locator(`#bookingPolFabActions [role="option"][data-value="${trip.pol}"]`).click();
  await page.locator('#etdDateText').fill('09/30/2026');
  await page.locator('#etdDateText').blur();
  await page.locator('#bookingNo').fill(trip.bookingNo);
  await page.locator('#vesselSelect').fill(trip.vessel);
  await page.locator('#chassisSearchInput').fill(trip.chassis);
  await page.locator('#chassisSearchInput').press('Enter');
  const car = page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${trip.chassis}"]`);
  await expect(car).toBeVisible();
  await car.check();
  const already = page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${trip.alreadyShipped}"]`);
  if (await already.count()) await already.uncheck();
  await page.locator('#calculateBtn').click();
  await expect(page.locator('#cnfCarsTable')).toContainText(trip.chassis);
  await expect(page.locator('#cnfCarsTable')).not.toContainText(trip.alreadyShipped);
  await page.locator('#saveCnfBtn').click();
  await dismissSaved(page);
}

async function waitForOption(page: Page, selector: string, value: string) {
  await page.waitForFunction(
    ({ selector, value }) => {
      const select = document.querySelector(selector);
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === value);
    },
    { selector, value },
    { timeout: 30_000 },
  );
}

async function openClient(page: Page, clientName: string) {
  await openMenu(page, '#clientTransactionsSidebarBtn');
  await page.locator('#clientSearchInput').fill(clientName);
  await page.locator('#clientListTable').getByText(clientName, { exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Client Details' })).toBeVisible();
}

async function openPurchase(page: Page, chassis: string) {
  await openMenu(page, '#purchaseListBtn');
  await expect(page.locator('#purchaseTable')).toContainText(chassis);
  await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${chassis}"]`).click();
  await expect(page).toHaveURL(new RegExp(`/edit/${chassis}$`));
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

async function dismissSaved(page: Page) {
  const saved = page.getByRole('dialog', { name: 'Saved' });
  await expect(saved).toBeVisible();
  await saved.getByRole('button', { name: 'OK' }).click();
}

async function deleteShipping(request: APIRequestContext, chassisList: string[]) {
  const history = await request.get(`${apiBase}/shipping-history`);
  if (!history.ok()) return;
  const rows = await history.json();
  const shippingIds = (Array.isArray(rows) ? rows : [])
    .filter((row) => chassisList.some((chassis) => String(row.chassis || '').includes(chassis)))
    .map((row) => Number(row.id))
    .filter((id) => Number.isInteger(id) && id > 0);
  if (shippingIds.length > 0) {
    await request.post(`${apiBase}/shipping-history/delete-batch`, { data: { ids: shippingIds } });
  }
}

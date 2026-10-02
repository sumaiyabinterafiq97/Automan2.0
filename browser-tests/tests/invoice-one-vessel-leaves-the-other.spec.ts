import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('invoicing one vessel charges only that vehicle and leaves the other vessel uninvoiced', async ({ page, request }) => {
  test.setTimeout(180_000);
  const stamp = uniqueStamp();
  const invoicedChassis = `VA${stamp}`.slice(0, 20);
  const openChassis = `VB${stamp}`.slice(0, 20);
  const clientName = `Client ${stamp}`;
  const invoicedVessel = `VA${stamp}`;
  const openVessel = `VB${stamp}`;
  const invoiceNumber = `INV${stamp}`;
  const country = 'Kenya';
  const stockLocation = `YARD${stamp}`.slice(0, 24);
  const pol = `PORT${stamp}`.slice(0, 16);
  const bookingA = `${Date.now()}${Math.floor(Math.random() * 90 + 10)}`;
  const bookingB = `${Date.now() + 1}${Math.floor(Math.random() * 90 + 10)}`;
  const admin = {
    email: `vessel-${stamp.toLowerCase()}@example.com`,
    name: 'Vessel Invoice Admin',
    password: 'Browser!Test1',
  };
  const purchaseIds: number[] = [];
  let clientId: number | null = null;
  let stockMapId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    const client = await request.post(`${apiBase}/clients`, {
      data: { clientNumber: `C${bookingA}`, clientName, creditLimit: 100000000, currency: 'JPY', status: 'ACTIVE' },
    });
    expect(client.ok(), await client.text()).toBeTruthy();
    clientId = Number((await client.json()).id);
    const stockMap = await request.post(`${apiBase}/stock-location-map/mappings/add`, {
      data: { stockLocation, pol },
    });
    expect(stockMap.ok(), await stockMap.text()).toBeTruthy();
    stockMapId = Number((await stockMap.json()).data.id);
    for (const chassis of [invoicedChassis, openChassis]) {
      const purchase = await request.post(`${apiBase}/purchases`, {
        data: {
          date: '09/30/2026',
          chassis,
          carName: `Car ${chassis}`,
          clientName,
          auctionHouse: `Supplier ${stamp}`,
          stockLocation,
          country,
          price: '125000',
          local: false,
          rixoConfirmed: 'TRUE',
        },
      });
      expect(purchase.ok(), await purchase.text()).toBeTruthy();
      purchaseIds.push(Number((await purchase.json()).id));
    }

    await login(page, admin);
    await ship(page, { country, stockLocation, pol, bookingNo: bookingA, vessel: invoicedVessel, chassis: invoicedChassis });
    await openMenu(page, '#carBookingBtn', '#shipmentHeader');
    await expect(page.getByRole('heading', { name: 'CREATE SHIPPING SCHEDULE' })).toBeVisible();
    await page.waitForFunction((name) => {
      const select = document.querySelector('#consigneeCountry');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
    }, country, { timeout: 30_000 });
    await page.locator('#bookingCountryFabTrigger').click();
    await page.locator(`#bookingCountryFabActions [role="option"][data-value="${country}"]`).click();
    await page.waitForFunction((stock) => {
      const select = document.querySelector('#bookingStockLocations');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === stock);
    }, stockLocation, { timeout: 30_000 });
    await page.locator('#bookingStockLocationsInput').fill(stockLocation);
    await page.locator('#bookingStockLocationsInput').press('Enter');
    await page.waitForFunction((port) => {
      const select = document.querySelector('#polPort');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === port);
    }, pol, { timeout: 30_000 });
    await page.locator('#bookingPolFabTrigger').click();
    await page.locator(`#bookingPolFabActions [role="option"][data-value="${pol}"]`).click();
    await page.locator('#etdDateText').fill('09/30/2026');
    await page.locator('#etdDateText').blur();
    await page.locator('#bookingNo').fill(bookingB);
    await page.locator('#vesselSelect').fill(openVessel);
    await page.locator('#chassisSearchInput').fill(openChassis);
    await page.locator('#chassisSearchInput').press('Enter');
    const openCar = page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${openChassis}"]`);
    await expect(openCar).toBeVisible();
    await openCar.check();
    const alreadyShipped = page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${invoicedChassis}"]`);
    if (await alreadyShipped.count()) await alreadyShipped.uncheck();
    await page.locator('#calculateBtn').click();
    await expect(page.locator('#cnfCarsTable')).toContainText(openChassis);
    await expect(page.locator('#cnfCarsTable')).not.toContainText(invoicedChassis);
    await page.locator('#saveCnfBtn').click();
    await dismissSaved(page);

    await openMenu(page, '#anInvoiceBtn', '#invoiceNavHeader');
    await page.locator('#closeSidebar').click();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await page.waitForFunction((name) => {
      const select = document.querySelector('#invoiceClient');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
    }, clientName, { timeout: 30_000 });
    await page.locator('#invoiceClient').selectOption(clientName);
    await page.waitForFunction((vessel) => {
      const select = document.querySelector('#invoiceVessel');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === vessel);
    }, invoicedVessel, { timeout: 30_000 });
    await page.locator('#invoiceVessel').selectOption(invoicedVessel);
    const invoiceLines = page.locator('#invoiceListTableBody');
    await expect(invoiceLines).toContainText(invoicedChassis);
    await expect(invoiceLines).not.toContainText(openChassis);
    await expect(page.locator('#invoiceTotalAmount')).toHaveText('Total Amount: ¥125,000');
    await page.locator('#invoiceNumber').fill(invoiceNumber);
    await page.locator('#invoiceSaveBtn').click();
    await dismissSaved(page);

    await openMenu(page, '#invoiceHistorySidebarBtn', '#invoiceNavHeader');
    await page.locator('#invoiceHistorySearchInput').fill(invoiceNumber);
    const history = page.locator('#invoiceHistoryTableBody');
    await expect(history.locator('tr')).toHaveCount(1);
    await expect(history).toContainText(invoicedChassis);
    await expect(history).toContainText(invoicedVessel);
    await expect(history).not.toContainText(openChassis);
    await expect(history).toContainText('¥125,000');

    await openMenu(page, '#clientTransactionsSidebarBtn');
    await page.locator('#clientSearchInput').fill(clientName);
    await page.locator('#clientListTable').getByText(clientName, { exact: true }).click();
    await expect(page.locator('#currentBalanceValue')).toHaveText('−¥125000');

    await openMenu(page, '#anInvoiceBtn', '#invoiceNavHeader');
    await page.locator('#closeSidebar').click();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await expect(page.getByRole('heading', { name: 'Create Local Customer Invoice' })).toBeVisible();
    await page.waitForFunction((name) => {
      const select = document.querySelector('#invoiceClient');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
    }, clientName, { timeout: 30_000 });
    await page.locator('#invoiceClient').selectOption(clientName);
    await page.waitForFunction(
      ({ open, closed }) => {
        const select = document.querySelector('#invoiceVessel');
        if (!(select instanceof HTMLSelectElement)) return false;
        const values = Array.from(select.options).map((option) => option.value);
        return values.includes(open) && !values.includes(closed);
      },
      { open: openVessel, closed: invoicedVessel },
      { timeout: 30_000 },
    );
    await expect(page.locator('#invoiceListTableBody')).toContainText(openChassis);
    await expect(page.locator('#invoiceListTableBody')).not.toContainText(invoicedChassis);
    await expect(page.locator('#invoiceTotalAmount')).toHaveText('Total Amount: ¥125,000');

    await openPurchase(page, invoicedChassis);
    await expect(page.locator('input[name="editStatusInvoiceConfirmed"][value="TRUE"]')).toBeChecked();
    await openPurchase(page, openChassis);
    await expect(page.locator('input[name="editStatusInvoiceConfirmed"][value="FALSE"]')).toBeChecked();
    await expect(page.locator('input[name="editStatusBookingRequested"][value="TRUE"]')).toBeChecked();
  } finally {
    await request.post(`${apiBase}/invoice-history/batch-delete`, { data: { invoiceNumbers: [invoiceNumber] } });
    await deleteShipping(request, [invoicedChassis, openChassis]);
    for (const id of purchaseIds) await request.delete(`${apiBase}/purchases/${id}`);
    if (stockMapId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${stockMapId}`);
    if (clientId != null) await request.delete(`${apiBase}/clients/${clientId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
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
  await page.waitForFunction((name) => {
    const select = document.querySelector('#consigneeCountry');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
  }, trip.country, { timeout: 30_000 });
  await page.locator('#bookingCountryFabTrigger').click();
  await page.locator(`#bookingCountryFabActions [role="option"][data-value="${trip.country}"]`).click();
  await page.waitForFunction((stock) => {
    const select = document.querySelector('#bookingStockLocations');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === stock);
  }, trip.stockLocation, { timeout: 30_000 });
  await page.locator('#bookingStockLocationsInput').fill(trip.stockLocation);
  await page.locator('#bookingStockLocationsInput').press('Enter');
  await page.waitForFunction((port) => {
    const select = document.querySelector('#polPort');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === port);
  }, trip.pol, { timeout: 30_000 });
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

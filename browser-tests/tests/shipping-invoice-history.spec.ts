import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('shipped vehicle and its invoice stay visible in history, and the invoice posts to that client ledger', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000);
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
  const chassis = `HS${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 20);
  const country = 'Kenya';
  const stockLocation = `YARD${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 24);
  const pol = `PORT${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16);
  const clientName = `Client ${stamp}`;
  const supplierName = `Supplier ${stamp}`;
  const vessel = `Vessel ${stamp}`;
  const bookingNo = `${Date.now()}${Math.floor(Math.random() * 90 + 10)}`;
  const invoiceNumber = `INV${stamp}`;
  const admin = {
    email: `history-${stamp.toLowerCase()}@example.com`,
    name: 'History Admin',
    password: 'Browser!Test1',
  };

  let purchaseId: number | null = null;
  let clientId: number | null = null;
  let stockMapId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();

    const client = await request.post(`${apiBase}/clients`, {
      data: {
        clientNumber: `C${bookingNo}`,
        clientName,
        creditLimit: 100000000,
        currency: 'JPY',
        status: 'ACTIVE',
      },
    });
    expect(client.ok(), await client.text()).toBeTruthy();
    clientId = Number((await client.json()).id);

    const stockMap = await request.post(`${apiBase}/stock-location-map/mappings/add`, {
      data: { stockLocation, pol },
    });
    expect(stockMap.ok(), await stockMap.text()).toBeTruthy();
    stockMapId = Number((await stockMap.json()).data.id);

    const purchase = await request.post(`${apiBase}/purchases`, {
      data: {
        date: '09/30/2026',
        chassis,
        carName: `History Car ${stamp}`,
        clientName,
        auctionHouse: supplierName,
        stockLocation,
        country,
        price: '125000',
        local: false,
        rixoConfirmed: 'TRUE',
      },
    });
    expect(purchase.ok(), await purchase.text()).toBeTruthy();
    purchaseId = Number((await purchase.json()).id);

    await page.goto('/login');
    await page.locator('#si_email').fill(admin.email);
    await page.locator('#si_pass').fill(admin.password);
    await page.locator('#btn_signin').click();
    await expect(page.locator('#purchaseTable')).toContainText(chassis);

    await openMenu(page, '#carBookingBtn', '#shipmentHeader');
    await expect(page.getByRole('heading', { name: 'CREATE SHIPPING SCHEDULE' })).toBeVisible();
    await page.waitForFunction((name) => {
      const select = document.querySelector('#consigneeCountry');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
    }, country);
    await page.locator('#bookingCountryFabTrigger').click();
    await page.locator(`#bookingCountryFabActions [role="option"][data-value="${country}"]`).click();
    await page.waitForFunction((stock) => {
      const select = document.querySelector('#bookingStockLocations');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === stock);
    }, stockLocation);
    await page.locator('#bookingStockLocationsInput').fill(stockLocation);
    await page.locator('#bookingStockLocationsInput').press('Enter');
    await page.waitForFunction((port) => {
      const select = document.querySelector('#polPort');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === port);
    }, pol);
    await page.locator('#bookingPolFabTrigger').click();
    await page.locator(`#bookingPolFabActions [role="option"][data-value="${pol}"]`).click();
    await page.locator('#etdDateText').fill('09/30/2026');
    await page.locator('#etdDateText').blur();
    await page.locator('#bookingNo').fill(bookingNo);
    await page.locator('#vesselSelect').fill(vessel);
    await page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${chassis}"]`).check();
    await page.locator('#calculateBtn').click();
    await expect(page.getByRole('heading', { name: 'C&F Calculation' })).toBeVisible();
    await page.locator('#saveCnfBtn').click();
    await dismissSaved(page);

    await openMenu(page, '#shipmentStatusBtn', '#shipmentHeader');
    await expect(page.getByRole('heading', { name: 'Shipping History' })).toBeVisible();
    await page.locator('#shippingHistorySearchInput').fill(chassis);
    const shippingTable = page.locator('#shippingHistoryTable');
    await expect(shippingTable).toContainText(bookingNo);
    await expect(shippingTable).toContainText(clientName);
    await page.locator('#shippingHistoryColumnFilterBtn').click();
    await page.locator('#shippingHistCol_units').uncheck();
    await page.locator('#shippingHistCol_chassis').check();
    await page.locator('#shippingHistCol_shipmentDate').uncheck();
    await page.locator('#shippingHistCol_vessel').check();
    await page.locator('#applyShippingHistoryColumns').click();
    await expect(shippingTable).toContainText(chassis);
    await expect(shippingTable).toContainText(clientName);
    await expect(shippingTable).toContainText(vessel);

    await openMenu(page, '#anInvoiceBtn', '#invoiceNavHeader');
    await page.locator('#closeSidebar').click();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();
    await expect(page.getByRole('heading', { name: 'Create Local Customer Invoice' })).toBeVisible();
    await page.waitForFunction((name) => {
      const select = document.querySelector('#invoiceClient');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === name);
    }, clientName);
    await page.locator('#invoiceClient').selectOption(clientName);
    await expect(page.locator('#invoiceListTableBody')).toContainText(chassis);
    await expect(page.locator('#invoiceTotalAmount')).toHaveText('Total Amount: ¥125,000');
    await page.locator('#invoiceNumber').fill(invoiceNumber);
    await page.locator('#invoiceSaveBtn').click();
    await dismissSaved(page);

    await openMenu(page, '#invoiceHistorySidebarBtn', '#invoiceNavHeader');
    await expect(page.getByRole('heading', { name: 'Invoice History' })).toBeVisible();
    await page.locator('#invoiceHistorySearchInput').fill(invoiceNumber);
    const invoiceTable = page.locator('#invoiceHistoryTable');
    await expect(invoiceTable).toContainText(invoiceNumber);
    await expect(invoiceTable).toContainText(clientName);
    await expect(invoiceTable).toContainText(vessel);
    await expect(invoiceTable).toContainText(chassis);

    await page.locator(`button[aria-label="Edit invoice"][data-invoice-number="${invoiceNumber}"]`).click();
    await expect(page.getByRole('heading', { name: 'Recreate Local Customer Invoice' })).toBeVisible();
    await expect(page.locator('#invoiceNumber')).toHaveValue(invoiceNumber);
    await expect(page.locator('#invoiceClient')).toHaveValue(clientName);
    await expect(page.locator('#invoiceVessel')).toHaveValue(vessel);
    await expect(page.locator('#invoiceListTableBody')).toContainText(chassis);

    await openMenu(page, '#clientTransactionsSidebarBtn');
    await expect(page.getByRole('heading', { name: 'Client Transactions' })).toBeVisible();
    await page.locator('#clientSearchInput').fill(clientName);
    await expect(page.locator('#clientListTable')).toContainText(clientName);
    await expect(page.locator('#clientListTable')).toContainText('−¥125000');
    await page.locator('#clientListTable').getByText(clientName, { exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Client Details' })).toBeVisible();
    await expect(page.locator('#clientDetailsContent')).toContainText(clientName);
    await expect(page.locator('#currentBalanceValue')).toHaveText('−¥125000');
    await expect(page.locator('#clientEventsTable')).toContainText('Invoice');
    await expect(page.locator('#clientEventsTable')).toContainText(invoiceNumber);
    await expect(page.locator('#clientEventsTable')).toContainText('¥125000');
  } finally {
    await request.post(`${apiBase}/invoice-history/batch-delete`, {
      data: { invoiceNumbers: [invoiceNumber] },
    });
    const history = await request.get(`${apiBase}/shipping-history`);
    if (history.ok()) {
      const rows = await history.json();
      const shippingIds = (Array.isArray(rows) ? rows : [])
        .filter((row) => String(row.chassis || '').includes(chassis))
        .map((row) => Number(row.id))
        .filter((id) => Number.isInteger(id) && id > 0);
      if (shippingIds.length > 0) {
        await request.post(`${apiBase}/shipping-history/delete-batch`, { data: { ids: shippingIds } });
      }
    }
    if (purchaseId != null) await request.delete(`${apiBase}/purchases/${purchaseId}`);
    if (stockMapId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${stockMapId}`);
    if (clientId != null) await request.delete(`${apiBase}/clients/${clientId}`);
  }
});

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

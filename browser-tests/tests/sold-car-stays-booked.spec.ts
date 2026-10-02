import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('an invoiced car stays on its booking and cannot be removed', async ({ page, request }) => {
  test.setTimeout(180_000);
  const stamp = uniqueStamp();
  const chassis = `SD${stamp}`.slice(0, 20);
  const country = 'Kenya';
  const stockLocation = `YARD${stamp}`.slice(0, 24);
  const pol = `PORT${stamp}`.slice(0, 16);
  const clientName = `Client ${stamp}`;
  const invoiceNumber = `INV${stamp}`;
  const bookingNo = `${Date.now()}${Math.floor(Math.random() * 90 + 10)}`;
  const admin = {
    email: `sold-${stamp.toLowerCase()}@example.com`,
    name: 'Sold Admin',
    password: 'Browser!Test1',
  };
  let purchaseId: number | null = null;
  let clientId: number | null = null;
  let stockMapId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    const client = await request.post(`${apiBase}/clients`, {
      data: { clientNumber: `C${bookingNo}`, clientName, creditLimit: 100000000, currency: 'JPY', status: 'ACTIVE' },
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
        carName: `Sold Car ${stamp}`,
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
    purchaseId = Number((await purchase.json()).id);

    await login(page, admin);
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
    await page.locator('#vesselSelect').fill(`Vessel ${stamp}`);
    await page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${chassis}"]`).check();
    await page.locator('#calculateBtn').click();
    await expect(page.getByRole('heading', { name: 'C&F Calculation' })).toBeVisible();
    await page.locator('#saveCnfBtn').click();
    await dismissSaved(page);

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
    await dismissSaved(page);

    await openMenu(page, '#shipmentStatusBtn', '#shipmentHeader');
    await expect(page.getByRole('heading', { name: 'Shipping History' })).toBeVisible();
    await page.locator('#shippingHistorySearchInput').fill(bookingNo);
    await expect(page.locator('#shippingHistoryTable')).toContainText(bookingNo);
    await page.locator('button[aria-label="Edit shipping schedule"]').click();
    await expect(page.getByRole('heading', { name: 'RECREATE SHIPPING SCHEDULE' })).toBeVisible();
    const bookingCars = page.locator('#carSelectionTableBody');
    await expect(bookingCars).toContainText(chassis);
    await expect(bookingCars.getByText('Sold', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Remove car' })).toHaveCount(0);

    await openMenu(page, '#shipmentStatusBtn', '#shipmentHeader');
    await page.locator('#shippingHistorySearchInput').fill(bookingNo);
    await page.locator('#shippingHistoryColumnFilterBtn').click();
    await page.locator('#shippingHistCol_units').uncheck();
    await page.locator('#shippingHistCol_chassis').check();
    await page.locator('#applyShippingHistoryColumns').click();
    await expect(page.locator('#shippingHistoryTable')).toContainText(chassis);

    await openMenu(page, '#purchaseListBtn');
    await expect(page.locator('#purchaseTable')).toContainText(chassis);
    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${chassis}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/edit/${chassis}$`));
    await expect(page.locator('input[name="editStatusInvoiceConfirmed"][value="TRUE"]')).toBeChecked();
    await expect(page.locator('input[name="editStatusBookingRequested"][value="TRUE"]')).toBeChecked();
  } finally {
    await request.post(`${apiBase}/invoice-history/batch-delete`, { data: { invoiceNumbers: [invoiceNumber] } });
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

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
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

async function login(page: Page, admin: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(admin.email);
  await page.locator('#si_pass').fill(admin.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

async function dismissSaved(page: Page) {
  const saved = page.getByRole('dialog', { name: 'Saved' });
  await expect(saved).toBeVisible();
  await saved.getByRole('button', { name: 'OK' }).click();
}

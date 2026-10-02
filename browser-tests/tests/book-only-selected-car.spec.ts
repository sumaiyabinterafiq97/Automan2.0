import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('saving a booking ships only the selected vehicle', async ({ page, request }) => {
  test.setTimeout(180_000);
  const stamp = uniqueStamp();
  const selectedChassis = `SA${stamp}`.slice(0, 20);
  const otherChassis = `SB${stamp}`.slice(0, 20);
  const country = 'Kenya';
  const stockLocation = `YARD${stamp}`.slice(0, 24);
  const pol = `PORT${stamp}`.slice(0, 16);
  const bookingNo = `${Date.now()}11`;
  const clientName = `Client ${stamp}`;
  const admin = {
    email: `select-${stamp.toLowerCase()}@example.com`,
    name: 'Booking Select Admin',
    password: 'Browser!Test1',
  };
  const purchaseIds: number[] = [];
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
    for (const chassis of [selectedChassis, otherChassis]) {
      purchaseIds.push(await createPurchase(request, chassis, clientName, stockLocation, country));
    }

    await login(page, admin);
    await openMenu(page, '#carBookingBtn', '#shipmentHeader');
    await expect(page.getByRole('heading', { name: 'CREATE SHIPPING SCHEDULE' })).toBeVisible();
    await waitForOption(page, '#consigneeCountry', country);
    await page.locator('#bookingCountryFabTrigger').click();
    await page.locator(`#bookingCountryFabActions [role="option"][data-value="${country}"]`).click();
    await waitForOption(page, '#bookingStockLocations', stockLocation);
    await page.locator('#bookingStockLocationsInput').fill(stockLocation);
    await page.locator('#bookingStockLocationsInput').press('Enter');
    await waitForOption(page, '#polPort', pol);
    await page.locator('#bookingPolFabTrigger').click();
    await page.locator(`#bookingPolFabActions [role="option"][data-value="${pol}"]`).click();
    await page.locator('#etdDateText').fill('09/30/2026');
    await page.locator('#etdDateText').blur();
    await page.locator('#bookingNo').fill(bookingNo);
    await page.locator('#vesselSelect').fill(`Vessel ${stamp}`);
    const selected = page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${selectedChassis}"]`);
    const other = page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${otherChassis}"]`);
    await expect(selected).toBeVisible();
    await expect(other).toBeVisible();
    await selected.check();
    await other.uncheck();
    await page.locator('#calculateBtn').click();
    await expect(page.locator('#cnfCarsTable')).toContainText(selectedChassis);
    await expect(page.locator('#cnfCarsTable')).not.toContainText(otherChassis);
    await page.locator('#saveCnfBtn').click();
    await dismissSaved(page);

    await openMenu(page, '#shipmentStatusBtn', '#shipmentHeader');
    await page.locator('#shippingHistorySearchInput').fill(bookingNo);
    await page.locator('#shippingHistoryColumnFilterBtn').click();
    await page.locator('#shippingHistCol_units').uncheck();
    await page.locator('#shippingHistCol_chassis').check();
    await page.locator('#applyShippingHistoryColumns').click();
    const shipping = page.locator('#shippingHistoryTable');
    await expect(shipping).toContainText(selectedChassis);
    await expect(shipping).not.toContainText(otherChassis);

    await openPurchase(page, selectedChassis);
    await expect(page.locator('#editBookingId')).toHaveValue(bookingNo);
    await expect(page.locator('input[name="editStatusBookingRequested"][value="TRUE"]')).toBeChecked();
    await openPurchase(page, otherChassis);
    await expect(page.locator('#editBookingId')).toHaveValue('');
    await expect(page.locator('input[name="editStatusBookingRequested"][value="FALSE"]')).toBeChecked();
  } finally {
    await deleteShipping(request, [selectedChassis, otherChassis]);
    for (const id of purchaseIds) await request.delete(`${apiBase}/purchases/${id}`);
    if (stockMapId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${stockMapId}`);
    if (clientId != null) await request.delete(`${apiBase}/clients/${clientId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
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

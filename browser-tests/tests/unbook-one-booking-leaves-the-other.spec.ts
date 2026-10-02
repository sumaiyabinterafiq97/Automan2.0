import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('removing one booking unbooks only that vehicle and leaves the other booking shipped', async ({ page, request }) => {
  test.setTimeout(180_000);
  const stamp = uniqueStamp();
  const removedChassis = `RA${stamp}`.slice(0, 20);
  const keptChassis = `RB${stamp}`.slice(0, 20);
  const clientName = `Client ${stamp}`;
  const country = 'Kenya';
  const stockLocation = `YARD${stamp}`.slice(0, 24);
  const pol = `PORT${stamp}`.slice(0, 16);
  const removedBooking = `${Date.now()}11`;
  const keptBooking = `${Date.now()}22`;
  const admin = {
    email: `bookings-${stamp.toLowerCase()}@example.com`,
    name: 'Two Booking Admin',
    password: 'Browser!Test1',
  };
  const purchaseIds: number[] = [];
  let clientId: number | null = null;
  let stockMapId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    clientId = await createClient(request, `C${removedBooking}`, clientName);
    const stockMap = await request.post(`${apiBase}/stock-location-map/mappings/add`, {
      data: { stockLocation, pol },
    });
    expect(stockMap.ok(), await stockMap.text()).toBeTruthy();
    stockMapId = Number((await stockMap.json()).data.id);
    for (const chassis of [removedChassis, keptChassis]) {
      purchaseIds.push(await createPurchase(request, chassis, clientName, stockLocation, country));
    }

    await login(page, admin);
    await ship(page, { country, stockLocation, pol, bookingNo: removedBooking, vessel: `VA${stamp}`, chassis: removedChassis });
    await shipSecond(page, {
      country,
      stockLocation,
      pol,
      bookingNo: keptBooking,
      vessel: `VB${stamp}`,
      chassis: keptChassis,
      alreadyShipped: removedChassis,
    });

    await openMenu(page, '#shipmentStatusBtn', '#shipmentHeader');
    await expect(page.getByRole('heading', { name: 'Shipping History' })).toBeVisible();
    await page.locator('#shippingHistorySearchInput').fill(removedBooking);
    await expect(page.locator('#shippingHistoryTable')).toContainText(removedBooking);
    await expect(page.locator('#shippingHistoryTable')).not.toContainText(keptBooking);
    await page.locator('button[aria-label="Edit shipping schedule"]').click();
    await expect(page.getByRole('heading', { name: 'RECREATE SHIPPING SCHEDULE' })).toBeVisible();
    const bookingCars = page.locator('#carSelectionTableBody');
    await expect(bookingCars).toContainText(removedChassis);
    await expect(bookingCars).not.toContainText(keptChassis);
    await page.locator(`.booking-row-remove-btn[data-chassis="${removedChassis}"]`).click();
    await expect(page.getByRole('dialog', { name: 'Remove car' })).toContainText('Are you sure you want to remove this car from the booking?');
    await page.locator('#bookingConfirmOk').click();
    await expect(page.getByRole('heading', { name: 'Shipping History' })).toBeVisible();
    await showChassisColumn(page);
    await page.locator('#shippingHistorySearchInput').fill(removedBooking);
    await expect(page.locator('#shippingHistoryTable')).toContainText('No matches');
    await expect(page.locator('#shippingHistoryTable')).not.toContainText(removedChassis);

    await page.locator('#shippingHistorySearchInput').fill(keptBooking);
    const keptShipping = page.locator('#shippingHistoryTable');
    await expect(keptShipping).toContainText(keptBooking);
    await expect(keptShipping).toContainText(keptChassis);
    await expect(keptShipping).not.toContainText(removedChassis);

    await openPurchase(page, keptChassis);
    await expect(page.locator('input[name="editStatusBookingRequested"][value="TRUE"]')).toBeChecked();
    await openPurchase(page, removedChassis);
    await expect(page.locator('input[name="editStatusBookingRequested"][value="FALSE"]')).toBeChecked();
  } finally {
    await deleteShipping(request, [removedChassis, keptChassis]);
    for (const id of purchaseIds) await request.delete(`${apiBase}/purchases/${id}`);
    if (stockMapId != null) await request.delete(`${apiBase}/stock-location-map/mappings/${stockMapId}`);
    if (clientId != null) await request.delete(`${apiBase}/clients/${clientId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createClient(request: APIRequestContext, clientNumber: string, clientName: string) {
  const created = await request.post(`${apiBase}/clients`, {
    data: { clientNumber, clientName, creditLimit: 100000000, currency: 'JPY', status: 'ACTIVE' },
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
  await expect(page.locator('#cnfCarsTable')).toContainText(trip.chassis);
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

async function showChassisColumn(page: Page) {
  await page.locator('#shippingHistoryColumnFilterBtn').click();
  await page.locator('#shippingHistCol_units').uncheck();
  await page.locator('#shippingHistCol_chassis').check();
  await page.locator('#applyShippingHistoryColumns').click();
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

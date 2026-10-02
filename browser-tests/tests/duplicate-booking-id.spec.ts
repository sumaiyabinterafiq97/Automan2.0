import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('a booking number already used in shipping history cannot start another shipment', async ({ page, request }) => {
  test.setTimeout(180_000);
  const stamp = uniqueStamp();
  const bookedChassis = `BA${stamp}`.slice(0, 20);
  const otherChassis = `BB${stamp}`.slice(0, 20);
  const country = 'Kenya';
  const stockLocation = `YARD${stamp}`.slice(0, 24);
  const pol = `PORT${stamp}`.slice(0, 16);
  const clientName = `Client ${stamp}`;
  const bookingNo = `${Date.now()}${Math.floor(Math.random() * 90 + 10)}`;
  const admin = {
    email: `booking-${stamp.toLowerCase()}@example.com`,
    name: 'Booking Admin',
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
    for (const chassis of [bookedChassis, otherChassis]) {
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
    await book(page, { country, stockLocation, pol, bookingNo, vessel: `Vessel ${stamp}`, chassis: bookedChassis });

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
    await page.locator('#vesselSelect').fill(`Other ${stamp}`);
    await page.locator('#chassisSearchInput').fill(otherChassis);
    await page.locator('#chassisSearchInput').press('Enter');
    const otherCar = page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${otherChassis}"]`);
    await expect(otherCar).toBeVisible();
    await otherCar.check();
    const alreadyBooked = page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${bookedChassis}"]`);
    if (await alreadyBooked.count()) {
      await alreadyBooked.uncheck();
    }
    await page.locator('#calculateBtn').click();

    await expect(page.locator('#message')).toHaveText('Booking ID already exists in shipping history.');
    await expect(page.getByRole('heading', { name: 'CREATE SHIPPING SCHEDULE' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'C&F Calculation' })).toHaveCount(0);

    await openMenu(page, '#shipmentStatusBtn', '#shipmentHeader');
    await page.locator('#shippingHistorySearchInput').fill(bookingNo);
    await page.locator('#shippingHistoryColumnFilterBtn').click();
    await page.locator('#shippingHistCol_units').uncheck();
    await page.locator('#shippingHistCol_chassis').check();
    await page.locator('#applyShippingHistoryColumns').click();
    const shipping = page.locator('#shippingHistoryTable');
    await expect(shipping).toContainText(bookedChassis);
    await expect(shipping).not.toContainText(otherChassis);
  } finally {
    await deleteShipping(request, [bookedChassis, otherChassis]);
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

async function book(
  page: Page,
  trip: { country: string; stockLocation: string; pol: string; bookingNo: string; vessel: string; chassis: string },
) {
  await openMenu(page, '#carBookingBtn', '#shipmentHeader');
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

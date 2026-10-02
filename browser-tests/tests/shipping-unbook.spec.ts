import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('removing one booked car leaves the other car on the same booking', async ({ page, request }) => {
  test.setTimeout(180_000);
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
  const removedChassis = `UA${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 20);
  const keptChassis = `UB${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 20);
  const country = 'Kenya';
  const stockLocation = `YARD${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 24);
  const pol = `PORT${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16);
  const clientName = `Client ${stamp}`;
  const vessel = `Vessel ${stamp}`;
  const bookingNo = `${Date.now()}${Math.floor(Math.random() * 90 + 10)}`;
  const admin = {
    email: `unbook-${stamp.toLowerCase()}@example.com`,
    name: 'Unbook Admin',
    password: 'Browser!Test1',
  };
  const purchaseIds: number[] = [];
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
    for (const chassis of [removedChassis, keptChassis]) {
      const purchase = await request.post(`${apiBase}/purchases`, {
        data: {
          date: '09/30/2026',
          chassis,
          carName: `Unbook ${chassis}`,
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

    await page.goto('/login');
    await page.locator('#si_email').fill(admin.email);
    await page.locator('#si_pass').fill(admin.password);
    await page.locator('#btn_signin').click();
    await expect(page.locator('#purchaseTable')).toContainText(removedChassis);
    await expect(page.locator('#purchaseTable')).toContainText(keptChassis);

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
    await page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${removedChassis}"]`).check();
    await page.locator(`#carSelectionTableBody .car-checkbox[data-chassis="${keptChassis}"]`).check();
    await page.locator('#calculateBtn').click();
    await expect(page.locator('#cnfCarsTable')).toContainText(removedChassis);
    await expect(page.locator('#cnfCarsTable')).toContainText(keptChassis);
    await page.locator('#saveCnfBtn').click();
    await dismissSaved(page);

    await openMenu(page, '#shipmentStatusBtn', '#shipmentHeader');
    await expect(page.getByRole('heading', { name: 'Shipping History' })).toBeVisible();
    await page.locator('#shippingHistorySearchInput').fill(bookingNo);
    await expect(page.locator('#shippingHistoryTable')).toContainText(bookingNo);
    await page.locator('button[aria-label="Edit shipping schedule"]').click();
    await expect(page.getByRole('heading', { name: 'RECREATE SHIPPING SCHEDULE' })).toBeVisible();
    const bookingCars = page.locator('#carSelectionTableBody');
    await expect(bookingCars).toContainText(removedChassis);
    await expect(bookingCars).toContainText(keptChassis);

    await page.locator(`.booking-row-remove-btn[data-chassis="${removedChassis}"]`).click();
    await expect(page.getByRole('dialog', { name: 'Remove car' })).toContainText('Are you sure you want to remove this car from the booking?');
    await page.locator('#bookingConfirmOk').click();
    await expect(bookingCars).not.toContainText(removedChassis);
    await expect(bookingCars).toContainText(keptChassis);

    await openMenu(page, '#shipmentStatusBtn', '#shipmentHeader');
    await page.locator('#shippingHistorySearchInput').fill(bookingNo);
    await page.locator('#shippingHistoryColumnFilterBtn').click();
    await page.locator('#shippingHistCol_units').uncheck();
    await page.locator('#shippingHistCol_chassis').check();
    await page.locator('#applyShippingHistoryColumns').click();
    const shippingTable = page.locator('#shippingHistoryTable');
    await expect(shippingTable).toContainText(keptChassis);
    await expect(shippingTable).not.toContainText(removedChassis);

    await openPurchase(page, keptChassis);
    await expect(page.locator('input[name="editStatusBookingRequested"][value="TRUE"]')).toBeChecked();
    await openPurchase(page, removedChassis);
    await expect(page.locator('input[name="editStatusBookingRequested"][value="FALSE"]')).toBeChecked();
  } finally {
    const history = await request.get(`${apiBase}/shipping-history`);
    if (history.ok()) {
      const rows = await history.json();
      const shippingIds = (Array.isArray(rows) ? rows : [])
        .filter((row) => [removedChassis, keptChassis].some((chassis) => String(row.chassis || '').includes(chassis)))
        .map((row) => Number(row.id))
        .filter((id) => Number.isInteger(id) && id > 0);
      if (shippingIds.length > 0) {
        await request.post(`${apiBase}/shipping-history/delete-batch`, { data: { ids: shippingIds } });
      }
    }
    for (const id of purchaseIds) await request.delete(`${apiBase}/purchases/${id}`);
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

async function openPurchase(page: Page, chassis: string) {
  await openMenu(page, '#purchaseListBtn');
  await expect(page.locator('#purchaseTable')).toContainText(chassis);
  await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${chassis}"]`).click();
  await expect(page).toHaveURL(new RegExp(`/edit/${chassis}$`));
}

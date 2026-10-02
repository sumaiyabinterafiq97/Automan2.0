import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

const admin = {
  email: `integrity-${Date.now().toString(36)}@example.com`,
  name: 'Integrity Admin',
  password: 'Browser!Test1',
};
let adminReady = false;

test('deleting a purchase from the edit screen removes it from the list and search', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const chassisCode = code('DL', stamp);
  const suffix = code('S', stamp);
  const fullChassis = `${chassisCode}-${suffix}`;
  const carName = `Delete Car ${stamp}`;

  try {
    await ensureAdmin(request);
    await prepareChassis(request, chassisCode);
    await login(page);
    await createPurchase(page, chassisCode, suffix, carName);
    await expect(page.locator('#purchaseTable')).toContainText(fullChassis);

    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${fullChassis}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/edit/${fullChassis}$`));
    await expect(page.locator('#deleteBtn')).toBeVisible();

    const confirm = new Promise<string>((resolve) => {
      page.once('dialog', async (dialog) => {
        const message = dialog.message();
        await dialog.accept();
        resolve(message);
      });
    });
    await page.locator('#deleteBtn').click();
    expect(await confirm).toBe('Are you sure you want to delete this purchase?');

    await expect(page).toHaveURL(/\/purchase$/);
    await expect(page.locator('#purchaseTable')).not.toContainText(fullChassis);

    await page.locator('#purchaseSearchInput').fill(fullChassis);
    await page.locator('#purchaseSearchInput').press('Enter');
    await expect(page.locator('#purchaseTable')).toContainText('No matches');
    await expect(page.locator('#purchaseTable')).not.toContainText(fullChassis);
  } finally {
    await deletePurchaseByChassis(request, fullChassis);
  }
});

test('purchase search shows the matching chassis and clear restores both purchases', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const firstCode = code('SA', stamp);
  const secondCode = code('SB', stamp);
  const firstSuffix = code('A', stamp);
  const secondSuffix = code('B', stamp);
  const firstChassis = `${firstCode}-${firstSuffix}`;
  const secondChassis = `${secondCode}-${secondSuffix}`;

  try {
    await ensureAdmin(request);
    await prepareChassis(request, firstCode);
    await prepareChassis(request, secondCode);
    await login(page);
    await createPurchase(page, firstCode, firstSuffix, `Alpha ${stamp}`);
    await createPurchase(page, secondCode, secondSuffix, `Beta ${stamp}`);
    await expect(page.locator('#purchaseTable')).toContainText(firstChassis);
    await expect(page.locator('#purchaseTable')).toContainText(secondChassis);

    await page.locator('#purchaseSearchInput').fill(firstChassis);
    await page.locator('#purchaseSearchInput').press('Enter');
    await expect(page.locator('#purchaseTable')).toContainText(firstChassis);
    await expect(page.locator('#purchaseTable')).not.toContainText(secondChassis);

    await page.locator('#purchaseSearchClearBtn').click();
    await expect(page.locator('#purchaseSearchInput')).toHaveValue('');
    await expect(page.locator('#purchaseTable')).toContainText(firstChassis);
    await expect(page.locator('#purchaseTable')).toContainText(secondChassis);
  } finally {
    await deletePurchaseByChassis(request, firstChassis);
    await deletePurchaseByChassis(request, secondChassis);
  }
});

test('editing the car name persists when the same purchase is reopened', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const chassisCode = code('EN', stamp);
  const suffix = code('S', stamp);
  const fullChassis = `${chassisCode}-${suffix}`;
  const carName = `Edit Car ${stamp}`;
  const renamed = `Renamed ${stamp}`;

  try {
    await ensureAdmin(request);
    await prepareChassis(request, chassisCode);
    await login(page);
    await createPurchase(page, chassisCode, suffix, carName);

    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${fullChassis}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/edit/${fullChassis}$`));
    await expect(page.locator('#editCarNameInput')).toHaveValue(carName);
    await expect(page.locator('#editPurchaseChangeHistoryHost')).not.toContainText('Loading');
    await expect(page.getByRole('button', { name: 'Basic' })).toBeVisible();
    await page.waitForTimeout(2200);

    await page.locator('#editCarNameInput').fill(renamed);
    await page.locator('#editUpdateBtn').click();
    await expect(page.locator('#confirmEditUpdate')).toBeVisible();
    await expect(page.locator('#editConfirmationModal')).toContainText(renamed);
    await page.locator('#confirmEditUpdate').click();

    const saved = page.getByRole('dialog', { name: 'Saved' });
    await expect(saved).toContainText('Purchase updated successfully!');
    await saved.getByRole('button', { name: 'OK' }).click();
    await expect(page.locator('#purchaseTable')).toContainText(renamed);
    await expect(page.locator('#purchaseTable')).not.toContainText(carName);

    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${fullChassis}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/edit/${fullChassis}$`));
    await expect(page.locator('#editCarNameInput')).toHaveValue(renamed);
  } finally {
    await deletePurchaseByChassis(request, fullChassis);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
}

function code(prefix: string, stamp: string) {
  return `${prefix}${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16);
}

async function ensureAdmin(request: APIRequestContext) {
  if (adminReady) return;
  const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
  expect(setup.ok(), await setup.text()).toBeTruthy();
  adminReady = true;
}

async function prepareChassis(request: APIRequestContext, chassisCode: string) {
  const mapping = await request.post(`${apiBase}/car-brand-mapping/mappings`, {
    data: { chassis: chassisCode, carBrand: 'Browser' },
  });
  expect(mapping.ok(), await mapping.text()).toBeTruthy();
}

async function login(page: Page) {
  await page.goto('/login');
  await page.locator('#si_email').fill(admin.email);
  await page.locator('#si_pass').fill(admin.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
  await expect(page.locator('#newBtn')).toBeVisible();
}

async function createPurchase(page: Page, chassisCode: string, suffix: string, carName: string) {
  await page.getByRole('button', { name: 'Open menu' }).click();
  await page.locator('#newBtn').click();
  await expect(page).toHaveURL(/\/add$/);
  await page.waitForFunction((code) => {
    const select = document.querySelector('#chassisCode');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === code);
  }, chassisCode);
  await page.locator('#chassisCodeInput').fill(chassisCode);
  await page.locator('#chassisNumberInput').fill(suffix);
  await page.locator('#carNameInput').fill(carName);
  await page.locator('#addSaveBtn').click();
  await page.waitForFunction(() => {
    const button = document.querySelector('#addSaveBtn');
    if (!(button instanceof HTMLButtonElement)) return true;
    return (button.textContent || '').trim() !== 'Saving...';
  });
  const saved = page.getByRole('dialog', { name: 'Saved' });
  await expect(saved).toContainText('Purchase created successfully!');
  await saved.getByRole('button', { name: 'OK' }).click();
  await expect(page.locator('#purchaseTable')).toContainText(`${chassisCode}-${suffix}`);
}

async function deletePurchaseByChassis(request: APIRequestContext, chassis: string) {
  const duplicate = await request.get(`${apiBase}/purchases/check-duplicate`, {
    params: { chassis },
  });
  if (!duplicate.ok()) return;
  const body = await duplicate.json();
  const id = Number(body.existingPurchaseId);
  if (!Number.isInteger(id) || id <= 0) return;
  const deleted = await request.delete(`${apiBase}/purchases/${id}`);
  if (!deleted.ok()) {
    throw new Error(`Failed to delete purchase ${id}: ${await deleted.text()}`);
  }
}

import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('purchase validation rejects missing and duplicate chassis, then accepts a corrected purchase', async ({
  page,
  request,
}) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
  const chassisCode = `PV${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16);
  const chassisSuffix = `S${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 12);
  const recoveredSuffix = `R${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 12);
  const fullChassis = `${chassisCode}-${chassisSuffix}`;
  const recoveredChassis = `${chassisCode}-${recoveredSuffix}`;
  const carName = `Validation Car ${stamp}`;
  const admin = {
    email: `validation-${stamp.toLowerCase()}@example.com`,
    name: 'Validation Admin',
    password: 'Browser!Test1',
  };

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();

    const mapping = await request.post(`${apiBase}/car-brand-mapping/mappings`, {
      data: { chassis: chassisCode, carBrand: 'Browser' },
    });
    expect(mapping.ok(), await mapping.text()).toBeTruthy();

    await page.goto('/login');
    await page.locator('#si_email').fill(admin.email);
    await page.locator('#si_pass').fill(admin.password);
    await page.locator('#btn_signin').click();
    await expect(page.locator('#purchaseTable')).toBeVisible();

    await openNewPurchase(page, chassisCode);
    await page.locator('#chassisCodeInput').fill(chassisCode);
    await page.locator('#chassisNumberInput').fill('');
    await savePurchase(page);

    const requiredError = page.locator('#errorModal');
    await expect(requiredError).toContainText('Validation Error');
    await expect(requiredError).toContainText('Chassis Number (suffix) is required.');
    await expect(page).toHaveURL(/\/add$/);
    await requiredError.locator('#okErrorModalBtn').click();
    await expect(requiredError).toBeHidden();

    await page.getByRole('button', { name: 'Open menu' }).click();
    await page.locator('#purchaseListBtn').click();
    await expect(page.locator('#purchaseSearchInput')).toHaveValue('');
    await expect(page.locator('#purchaseTable')).toContainText('No matches');
    await expect(page.locator('#purchaseTable')).not.toContainText(chassisCode);

    await openNewPurchase(page, chassisCode);
    await page.locator('#chassisCodeInput').fill(chassisCode);
    await page.locator('#chassisNumberInput').fill(chassisSuffix);
    await page.locator('#carNameInput').fill(carName);
    await savePurchase(page);
    await dismissSaved(page);
    await expect(page.locator('#purchaseTable')).toContainText(fullChassis);

    await openNewPurchase(page, chassisCode);
    await page.locator('#chassisCodeInput').fill(chassisCode);
    await page.locator('#chassisNumberInput').fill(chassisSuffix);
    await page.locator('#carNameInput').fill(carName);
    await savePurchase(page);

    const duplicateError = page.locator('#errorModal');
    await expect(duplicateError).toContainText('Duplicate Purchase');
    await expect(duplicateError).toContainText('the chassis number already exist');
    await expect(page).toHaveURL(/\/add$/);
    await duplicateError.locator('#okErrorModalBtn').click();
    await expect(duplicateError).toBeHidden();

    await page.getByRole('button', { name: 'Open menu' }).click();
    await page.locator('#purchaseListBtn').click();
    await expect(page.locator('#purchaseTable tbody tr', { hasText: fullChassis })).toHaveCount(1);

    await openNewPurchase(page, chassisCode);
    await page.locator('#chassisCodeInput').fill(chassisCode);
    await page.locator('#chassisNumberInput').fill(recoveredSuffix);
    await page.locator('#carNameInput').fill(carName);
    await savePurchase(page);
    await dismissSaved(page);
    await expect(page.locator('#purchaseTable')).toContainText(recoveredChassis);

    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${recoveredChassis}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/edit/${recoveredChassis}$`));
    await expect(page.getByRole('heading', { name: 'Edit Purchase' })).toBeVisible();
    await expect(page.locator('#editChassisCodeInput')).toHaveValue(chassisCode);
    await expect(page.locator('#editChassisNumberInput')).toHaveValue(recoveredSuffix);
  } finally {
    await deletePurchaseByChassis(request, fullChassis);
    await deletePurchaseByChassis(request, recoveredChassis);
    await deletePurchaseByChassis(request, chassisCode);
  }
});

async function openNewPurchase(page: Page, chassisCode: string) {
  await page.getByRole('button', { name: 'Open menu' }).click();
  await page.locator('#newBtn').click();
  await expect(page).toHaveURL(/\/add$/);
  await expect(page.getByRole('heading', { name: 'Add New Purchase' })).toBeVisible();
  await page.waitForFunction((code) => {
    const select = document.querySelector('#chassisCode');
    return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === code);
  }, chassisCode);
}

async function savePurchase(page: Page) {
  await page.locator('#addSaveBtn').click();
  await page.waitForFunction(() => {
    const button = document.querySelector('#addSaveBtn');
    if (!(button instanceof HTMLButtonElement)) return true;
    return (button.textContent || '').trim() !== 'Saving...';
  });
}

async function dismissSaved(page: Page) {
  const saved = page.getByRole('dialog', { name: 'Saved' });
  await expect(saved).toBeVisible();
  await saved.getByRole('button', { name: 'OK' }).click();
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

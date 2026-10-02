import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('new purchase rejects a chassis code that is not in the master list', async ({ page, request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
  const chassisCode = `MC${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16);
  const unknownCode = `XX${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16);
  const suffix = `S${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 12);
  const unknownChassis = `${unknownCode}-${suffix}`;
  const fullChassis = `${chassisCode}-${suffix}`;
  const carName = `Mapped Car ${stamp}`;
  const admin = {
    email: `chassis-${stamp.toLowerCase()}@example.com`,
    name: 'Chassis Admin',
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
    await expect(page.locator('#newBtn')).toBeVisible();

    await page.getByRole('button', { name: 'Open menu' }).click();
    await page.locator('#newBtn').click();
    await expect(page).toHaveURL(/\/add$/);
    await expect(page.getByRole('heading', { name: 'Add New Purchase' })).toBeVisible();
    await page.waitForFunction((code) => {
      const select = document.querySelector('#chassisCode');
      return select instanceof HTMLSelectElement && Array.from(select.options).some((option) => option.value === code);
    }, chassisCode);

    await page.locator('#chassisCodeInput').fill(unknownCode);
    await page.locator('#chassisNumberInput').fill(suffix);
    await page.locator('#carNameInput').fill(carName);
    await savePurchase(page);

    const rejected = page.locator('#errorModal');
    await expect(rejected).toContainText('Invalid Chassis');
    await expect(rejected).toContainText('Please select Chassis Code from the dropdown.');
    await expect(page).toHaveURL(/\/add$/);
    await rejected.locator('#okErrorModalBtn').click();
    await expect(rejected).toBeHidden();

    await page.locator('#chassisCodeInput').fill(chassisCode);
    await page.locator('#chassisNumberInput').fill(suffix);
    await page.locator('#carNameInput').fill(carName);
    await savePurchase(page);
    const saved = page.getByRole('dialog', { name: 'Saved' });
    await expect(saved).toBeVisible();
    await saved.getByRole('button', { name: 'OK' }).click();

    await expect(page.locator('#purchaseTable')).toContainText(fullChassis);
    await expect(page.locator('#purchaseTable')).not.toContainText(unknownChassis);

    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${fullChassis}"]`).click();
    await expect(page).toHaveURL(new RegExp(`/edit/${fullChassis}$`));
    await expect(page.getByRole('heading', { name: 'Edit Purchase' })).toBeVisible();
    await expect(page.locator('#editChassisCodeInput')).toHaveValue(chassisCode);
    await expect(page.locator('#editChassisNumberInput')).toHaveValue(suffix);
  } finally {
    await deletePurchaseByChassis(request, unknownChassis);
    await deletePurchaseByChassis(request, fullChassis);
  }
});

async function savePurchase(page: Page) {
  await page.locator('#addSaveBtn').click();
  await page.waitForFunction(() => {
    const button = document.querySelector('#addSaveBtn');
    if (!(button instanceof HTMLButtonElement)) return true;
    return (button.textContent || '').trim() !== 'Saving...';
  });
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

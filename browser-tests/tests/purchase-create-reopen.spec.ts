import { expect, test } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('login, create a purchase, find it in the list, and reopen it by chassis', async ({ page, request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase();
  const chassisCode = `BW${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 16);
  const chassisSuffix = `S${stamp}`.replace(/[^A-Z0-9]/g, '').slice(0, 12);
  const fullChassis = `${chassisCode}-${chassisSuffix}`;
  const carName = `Browser Car ${stamp}`;
  const admin = {
    email: `browser-${stamp.toLowerCase()}@example.com`,
    name: 'Browser Admin',
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
    await expect(page.locator('#newBtn')).toBeAttached();
    await page.getByRole('button', { name: 'Open menu' }).click();
    await page.locator('#newBtn').click();

    await expect(page).toHaveURL(/\/add$/);
    await expect(page.getByRole('heading', { name: 'Add New Purchase' })).toBeVisible();
    await page.waitForFunction((code) => {
      const select = document.querySelector('#chassisCode');
      if (!(select instanceof HTMLSelectElement)) return false;
      return Array.from(select.options).some((option) => option.value === code);
    }, chassisCode);

    await page.locator('#chassisCodeInput').fill(chassisCode);
    await page.locator('#chassisNumberInput').fill(chassisSuffix);
    const capturedDate = await page.locator('#dateText').inputValue();
    await page.locator('#carNameInput').fill(carName);
    await page.locator('#addSaveBtn').click();

    await page.waitForFunction(() => {
      const button = document.querySelector('#addSaveBtn');
      if (!(button instanceof HTMLButtonElement)) return true;
      return (button.textContent || '').trim() !== 'Saving...';
    });

    const savedDialog = page.getByRole('dialog', { name: 'Saved' });
    await expect(savedDialog).toBeVisible();
    await savedDialog.getByRole('button', { name: 'OK' }).click();

    await expect(page.locator('#purchaseTable')).toContainText(fullChassis);
    await page.locator(`.edit-btn[aria-label="Edit"][data-chassis="${fullChassis}"]`).click();

    await expect(page).toHaveURL(new RegExp(`/edit/${fullChassis}$`));
    await expect(page.getByRole('heading', { name: 'Edit Purchase' })).toBeVisible();
    await expect(page.locator('#editDate')).toHaveValue(capturedDate);
    await expect(page.locator('#editChassisCodeInput')).toHaveValue(chassisCode);
    await expect(page.locator('#editChassisNumberInput')).toHaveValue(chassisSuffix);
    await expect(page.locator('#editCarNameInput')).toHaveValue(carName);
  } finally {
    const duplicate = await request.get(`${apiBase}/purchases/check-duplicate`, {
      params: { chassis: fullChassis },
    });
    if (duplicate.ok()) {
      const duplicateBody = await duplicate.json();
      const id = Number(duplicateBody.existingPurchaseId);
      if (Number.isInteger(id) && id > 0) {
        const deleted = await request.delete(`${apiBase}/purchases/${id}`);
        if (!deleted.ok()) {
          throw new Error(`Failed to delete purchase ${id}: ${await deleted.text()}`);
        }
      }
    }
  }
});

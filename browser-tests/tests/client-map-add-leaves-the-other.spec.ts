import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('adding a client map row leaves the other client unchanged', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const newName = `MapNew${stamp}`;
  const keptName = `MapKeep${stamp}`;
  const admin = {
    email: `clientmap-${stamp.toLowerCase()}@example.com`,
    name: 'Client Map Admin',
    password: 'Browser!Test1',
  };

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    const kept = await request.post(`${apiBase}/client-map/mappings`, {
      data: { clientName: keptName, country: 'Japan', consignee: 'Consignee Keep' },
    });
    expect(kept.ok(), await kept.text()).toBeTruthy();

    await login(page, admin);
    await openMenu(page, '#masterClientMapBtn', '#masterMapHeader');
    await expect(page.getByRole('heading', { name: 'Client Map' })).toBeVisible();
    await expect(page.locator('#clientMapTable')).toContainText(keptName);

    await page.locator('#addClientMapBtn').click();
    await expect(page.getByRole('heading', { name: 'Add New Client Map' })).toBeVisible();
    await page.locator('#clientMapMmClientNameInput').fill(newName);
    await page.locator('#saveClientMapBtn').click();

    await expect(page.locator('#message')).toHaveText('Client map row created');
    await expect(page.locator('#clientMapModal')).toHaveCount(0);
    await expect(page.locator('#clientMapTable')).toContainText(newName);
    await expect(page.locator('#clientMapTable')).toContainText(keptName);

    await page.locator('#clientMapSearchInput').fill(newName);
    await expect(page.locator('#clientMapTable')).toContainText(newName);
    await expect(page.locator('#clientMapTable')).not.toContainText(keptName);

    await page.locator('#clientMapSearchClearBtn').click();
    await page.locator('#clientMapSearchInput').fill(keptName);
    await expect(page.locator('#clientMapTable')).toContainText(keptName);
    await expect(page.locator('#clientMapTable')).toContainText('Japan');
    await expect(page.locator('#clientMapTable')).not.toContainText(newName);
  } finally {
    await deleteClientMap(request, newName);
    await deleteClientMap(request, keptName);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function openMenu(page: Page, buttonId: string, sectionHeaderId: string) {
  await page.getByRole('button', { name: 'Open menu' }).click();
  const header = page.locator(sectionHeaderId);
  if ((await header.getAttribute('aria-expanded')) !== 'true') {
    await header.click();
  }
  await page.locator(buttonId).click();
}

async function login(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(user.email);
  await page.locator('#si_pass').fill(user.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

async function deleteClientMap(request: APIRequestContext, name: string) {
  const listed = await request.get(`${apiBase}/client-map/mappings/page-search`, {
    params: { q: name, field: 'clientName', page: '0', size: '20' },
  });
  if (!listed.ok()) return;
  const body = (await listed.json()) as { content?: Array<{ id?: number; clientName?: string }> };
  for (const row of body.content ?? []) {
    if (row.clientName === name && row.id != null) {
      await request.delete(`${apiBase}/client-map/mappings/${row.id}`);
    }
  }
}

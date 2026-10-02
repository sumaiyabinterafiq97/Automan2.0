import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('deleting one client map leaves the other client', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const removedName = `MapDrop${stamp}`;
  const keptName = `MapKeep${stamp}`;
  const admin = {
    email: `clientmapdel-${stamp.toLowerCase()}@example.com`,
    name: 'Client Map Delete Admin',
    password: 'Browser!Test1',
  };

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    await createClientMap(request, removedName, 'Bangladesh');
    await createClientMap(request, keptName, 'Japan');

    await login(page, admin);
    await openMenu(page, '#masterClientMapBtn', '#masterMapHeader');
    await expect(page.getByRole('heading', { name: 'Client Map' })).toBeVisible();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();

    await page.locator('#clientMapSearchInput').fill(removedName, { timeout: 30_000 });
    await expect(page.locator('#clientMapTable')).toContainText(removedName);
    await expect(page.locator('#clientMapTable')).not.toContainText(keptName);
    await page.locator('#clientMapTable').getByRole('button', { name: 'Edit' }).click({ timeout: 30_000 });
    await expect(page.getByRole('heading', { name: 'Edit Client Map' })).toBeVisible();

    const confirm = new Promise<string>((resolve) => {
      page.once('dialog', async (dialog) => {
        const message = dialog.message();
        await dialog.accept();
        resolve(message);
      });
    });
    await page.locator('#deleteClientMapBtn').click({ timeout: 30_000 });
    expect(await confirm).toBe('Delete this client map row?');

    await expect(page.locator('#message')).toHaveText('Deleted');
    await expect(page.locator('#clientMapModal')).toHaveCount(0);
    await expect(page.locator('#clientMapTable')).toContainText('No rows match your current search/filter');
    await expect(page.locator('#clientMapTable')).not.toContainText(removedName);

    await page.locator('#clientMapSearchClearBtn').click({ timeout: 30_000 });
    await page.locator('#clientMapSearchInput').fill(keptName, { timeout: 30_000 });
    await expect(page.locator('#clientMapTable')).toContainText(keptName);
    await expect(page.locator('#clientMapTable')).toContainText('Japan');
    await expect(page.locator('#clientMapTable')).not.toContainText(removedName);
    await expect(page.locator('#clientMapTable')).not.toContainText('Bangladesh');
  } finally {
    await deleteClientMap(request, removedName);
    await deleteClientMap(request, keptName);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createClientMap(request: APIRequestContext, clientName: string, country: string) {
  const created = await request.post(`${apiBase}/client-map/mappings`, {
    data: { clientName, country, consignee: 'Consignee' },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
}

async function openMenu(page: Page, buttonId: string, sectionHeaderId: string) {
  await page.getByRole('button', { name: 'Open menu' }).click({ timeout: 30_000 });
  const header = page.locator(sectionHeaderId);
  if ((await header.getAttribute('aria-expanded')) !== 'true') {
    await header.click({ timeout: 30_000 });
  }
  await page.locator(buttonId).scrollIntoViewIfNeeded({ timeout: 30_000 });
  await page.locator(buttonId).click({ timeout: 30_000 });
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

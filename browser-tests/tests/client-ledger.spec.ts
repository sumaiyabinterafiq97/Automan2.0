import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('client ledger search opens that client, and a payment updates only that balance', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const paymentDate = '09/30/2026';
  const target = {
    number: `CA${stamp}`,
    name: `Ledger ${stamp}`,
  };
  const other = {
    number: `CB${stamp}`,
    name: `Other ${stamp}`,
  };
  const admin = {
    email: `ledger-${stamp.toLowerCase()}@example.com`,
    name: 'Ledger Admin',
    password: 'Browser!Test1',
  };
  let targetId: number | null = null;
  let otherId: number | null = null;

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    targetId = await createClient(request, target.number, target.name, 0);
    otherId = await createClient(request, other.number, other.name, 80);

    await login(page, admin);
    await page.getByRole('button', { name: 'Open menu' }).click();
    await page.locator('#clientTransactionsSidebarBtn').click();
    await expect(page.getByRole('heading', { name: 'Client Transactions' })).toBeVisible();

    await page.locator('#clientSearchInput').fill(target.number);
    await expect(page.locator('#clientListTable')).toContainText(target.name);
    await expect(page.locator('#clientListTable')).toContainText(`#${target.number}`);
    await expect(page.locator('#clientListTable')).toContainText('¥0');
    await expect(page.locator('#clientListTable')).not.toContainText(other.name);

    await page.locator('#clientListTable').getByText(target.name, { exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Client Details' })).toBeVisible();
    await expect(page.locator('#clientDetailsContent')).toContainText(target.name);
    await expect(page.locator('#clientDetailsContent')).toContainText(target.number);
    await expect(page.locator('#currentBalanceValue')).toHaveText('¥0');

    await page.locator('#addClientTransactionBtn').click();
    await expect(page.getByRole('heading', { name: 'Add Ledger Entry' })).toBeVisible();
    await page.locator('#txDateText').fill(paymentDate);
    await page.locator('#txDateText').blur();
    await page.locator('#txEventType').selectOption('PAYMENT_RECEIVED');
    await page.locator('#txAmount').fill('100000');
    await page.locator('#addTransactionForm').getByRole('button', { name: 'Save Entry' }).click();

    await expect(page.locator('#currentBalanceValue')).toHaveText('+¥100000');
    await expect(page.locator('#clientEventsTable')).toContainText('Payment');
    await expect(page.locator('#clientEventsTable')).toContainText('¥100000');
    await expect(page.locator('#clientDetailsContent')).toContainText(target.name);

    await page.locator('#backToClientsBtn').click();
    await expect(page.getByRole('heading', { name: 'Client Transactions' })).toBeVisible();
    await page.locator('#clientSearchInput').fill(other.number);
    await expect(page.locator('#clientListTable')).toContainText(other.name);
    await expect(page.locator('#clientListTable').getByText('+¥80', { exact: true })).toBeVisible();
    await expect(page.locator('#clientListTable')).not.toContainText(target.name);
    await expect(page.locator('#clientListTable').getByText('+¥100000', { exact: true })).toHaveCount(0);
  } finally {
    if (targetId != null) await request.delete(`${apiBase}/clients/${targetId}`);
    if (otherId != null) await request.delete(`${apiBase}/clients/${otherId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createClient(request: APIRequestContext, clientNumber: string, clientName: string, balance: number) {
  const created = await request.post(`${apiBase}/clients`, {
    data: {
      clientNumber,
      clientName,
      currentBalance: balance,
      creditLimit: 100000000,
      currency: 'JPY',
      status: 'ACTIVE',
    },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const id = Number((await created.json()).id);
  expect(id).toBeGreaterThan(0);
  return id;
}

async function login(page: Page, admin: { email: string; password: string }) {
  await page.goto('/login');
  await page.locator('#si_email').fill(admin.email);
  await page.locator('#si_pass').fill(admin.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
}

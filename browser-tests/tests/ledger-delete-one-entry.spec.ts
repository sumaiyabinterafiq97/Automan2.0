import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('deleting one ledger payment removes only that entry and recalculates the balance', async ({ page, request }) => {
  const stamp = uniqueStamp();
  const target = { number: `CA${stamp}`, name: `Ledger ${stamp}` };
  const other = { number: `CB${stamp}`, name: `Other ${stamp}` };
  const admin = {
    email: `ledgerdel-${stamp.toLowerCase()}@example.com`,
    name: 'Ledger Delete Admin',
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
    await openMenu(page, '#clientTransactionsSidebarBtn');
    await page.locator('#clientSearchInput').fill(target.name);
    await page.locator('#clientListTable').getByText(target.name, { exact: true }).click();
    await expect(page.locator('#currentBalanceValue')).toHaveText('¥0');

    await addPayment(page, '09/30/2026', '5000', 'Keep payment');
    await addPayment(page, '09/30/2026', '100000', 'Drop payment');
    await expect(page.locator('#currentBalanceValue')).toHaveText('+¥105000');
    await expect(page.locator('#clientEventsTable')).toContainText('Keep payment');
    await expect(page.locator('#clientEventsTable')).toContainText('Drop payment');

    await page.locator('#clientEventsTable tr', { hasText: 'Drop payment' }).getByRole('button', { name: 'Delete' }).click();
    const confirm = page.getByRole('dialog', { name: 'Delete ledger entry?' });
    await expect(confirm).toContainText('Delete this ledger entry? Balances will be recalculated.');
    await confirm.getByRole('button', { name: 'Delete', exact: true }).click();

    await expect(page.locator('#currentBalanceValue')).toHaveText('+¥5000');
    await expect(page.locator('#clientEventsTable')).toContainText('Keep payment');
    await expect(page.locator('#clientEventsTable')).toContainText('¥5000');
    await expect(page.locator('#clientEventsTable')).not.toContainText('Drop payment');
    await expect(page.locator('#clientEventsTable')).not.toContainText('¥100000');

    await page.locator('#backToClientsBtn').click();
    await page.locator('#clientSearchInput').fill(other.name);
    await expect(page.locator('#clientListTable').getByText('+¥80', { exact: true })).toBeVisible();
    await expect(page.locator('#clientListTable')).not.toContainText(target.name);
  } finally {
    if (targetId != null) await request.delete(`${apiBase}/clients/${targetId}`);
    if (otherId != null) await request.delete(`${apiBase}/clients/${otherId}`);
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createClient(request: APIRequestContext, clientNumber: string, clientName: string, currentBalance: number) {
  const created = await request.post(`${apiBase}/clients`, {
    data: { clientNumber, clientName, currentBalance, creditLimit: 100000000, currency: 'JPY', status: 'ACTIVE' },
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

async function addPayment(page: Page, date: string, amount: string, description: string) {
  await page.locator('#addClientTransactionBtn').click();
  await expect(page.getByRole('heading', { name: 'Add Ledger Entry' })).toBeVisible();
  await page.locator('#txDateText').fill(date);
  await page.locator('#txDateText').blur();
  await page.locator('#txEventType').selectOption('PAYMENT_RECEIVED');
  await page.locator('#txDescription').fill(description);
  await page.locator('#txAmount').fill(amount);
  await page.locator('#addTransactionForm').getByRole('button', { name: 'Save Entry' }).click();
  await expect(page.locator('#clientEventsTable')).toContainText(description);
}

async function openMenu(page: Page, buttonId: string) {
  await page.getByRole('button', { name: 'Open menu' }).click();
  await page.locator(buttonId).click();
}

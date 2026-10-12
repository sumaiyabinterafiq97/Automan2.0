import { expect, test } from '@playwright/test';
import { openAdminSession, staffAuthHeaders } from '../staff-api';

const apiBase = 'http://127.0.0.1:8083/api';

test('an admin assigns a consignee and links a portal user, and an editor is refused', async ({ page, request }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toLowerCase();
  const admin = {
    email: `party-admin-${stamp}@example.com`,
    name: 'Party Admin',
    password: 'Browser!Test1',
  };
  const editor = {
    email: `party-editor-${stamp}@example.com`,
    name: 'Party Editor',
    password: 'Browser!Test1',
  };
  const clientUser = {
    email: `party-client-${stamp}@example.com`,
    name: 'Party Client',
    password: 'Browser!Test1',
  };
  const auth = await openAdminSession(request, apiBase, admin);

  const createdEditor = await request.post(`${apiBase}/users`, {
    data: { ...editor, role: 'EDITOR' },
    headers: auth,
  });
  expect(createdEditor.ok(), await createdEditor.text()).toBeTruthy();
  const createdClientUser = await request.post(`${apiBase}/users`, {
    data: { ...clientUser, role: 'CLIENT' },
    headers: auth,
  });
  expect(createdClientUser.ok(), await createdClientUser.text()).toBeTruthy();
  const clientUserId = ((await createdClientUser.json()) as { id: number }).id;

  const clientNumber = `P2C${stamp.slice(0, 8)}`.toUpperCase();
  const createdClient = await request.post(`${apiBase}/clients`, {
    data: { clientNumber, clientName: `Party ${stamp}`, currency: 'JPY' },
    headers: auth,
  });
  expect(createdClient.ok(), await createdClient.text()).toBeTruthy();
  const clientId = ((await createdClient.json()) as { id: number }).id;

  const editorAuth = await staffAuthHeaders(request, apiBase, editor);
  const editorDenied = await request.put(`${apiBase}/admin/party-access/clients/${clientId}/consignee`, {
    data: { consigneeId: 1 },
    headers: editorAuth,
  });
  expect(editorDenied.status()).toBe(403);
  expect(await editorDenied.text()).not.toContain('SQL');

  await page.goto('/login');
  await page.locator('#si_email').fill(admin.email);
  await page.locator('#si_pass').fill(admin.password);
  await page.locator('#btn_signin').click();
  await expect(page.locator('#purchaseTable')).toBeVisible();
  await page.getByRole('button', { name: 'Open menu' }).click();
  await expect(page.locator('#partyAccessBtn')).toBeVisible();
  await page.locator('#partyAccessBtn').click();
  await expect(page.locator('h2')).toHaveText('Party access');
  await expect(page.locator('#partyConsigneeName')).toBeVisible();

  page.once('dialog', (dialog) => dialog.accept());
  await page.locator('#partyConsigneeName').fill(`Legal ${stamp}`);
  await page.locator('#partyCreateConsigneeBtn').click();
  const consigneeRow = page.locator('#partyConsigneeList').getByText(`Legal ${stamp}`);
  await expect(consigneeRow).toBeVisible();
  const consigneeText = await page.locator('#partyConsigneeList span', { hasText: `Legal ${stamp}` }).innerText();
  const consigneeId = Number(consigneeText.match(/id (\d+)/)?.[1]);
  expect(consigneeId).toBeGreaterThan(0);

  await page.locator('#partyClientSearch').fill(clientNumber);
  await expect(page.locator('#partyClientList')).toContainText(clientNumber);
  const assignInput = page.locator(`[data-assign-input="${clientId}"]`);
  await assignInput.click();
  await assignInput.pressSequentially(String(consigneeId));
  await expect(assignInput).toHaveValue(String(consigneeId));
  page.once('dialog', (dialog) => dialog.accept());
  await page.locator(`[data-assign-client="${clientId}"]`).click();
  await expect(page.locator('#partyAccessStatus')).toHaveText('Consignee assignment saved.');
  await expect(page.locator('#partyClientList')).toContainText(`id ${consigneeId}`);

  await page.locator('#partyUserSearch').fill(clientUser.email);
  await expect(page.locator('#partyUserList')).toContainText(clientUser.email);
  const linkInput = page.locator(`[data-link-input="${clientUserId}"]`);
  await linkInput.click();
  await linkInput.pressSequentially(String(clientId));
  await expect(linkInput).toHaveValue(String(clientId));
  page.once('dialog', (dialog) => dialog.accept());
  await page.locator(`[data-link-user="${clientUserId}"]`).click();
  await expect(page.locator('#partyAccessStatus')).toHaveText('Link created.');
  await page.locator(`[data-history-user="${clientUserId}"]`).click();
  await expect(page.locator('#partyLinkHistory')).toContainText('ACTIVE');

  page.once('dialog', (dialog) => dialog.accept());
  await page.locator('[data-revoke-link]').click();
  await expect(page.locator('#partyAccessStatus')).toHaveText('Link revoked.');
  await page.locator(`[data-history-user="${clientUserId}"]`).click();
  await expect(page.locator('#partyLinkHistory')).toContainText('REVOKED');
});

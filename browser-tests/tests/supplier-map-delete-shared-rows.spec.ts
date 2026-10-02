import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8083/api';

test('deleting one supplier branch removes its shared rows from both maps and leaves the other supplier', async ({
  page,
  request,
}) => {
  const stamp = uniqueStamp();
  const removed = {
    supplier: `SUPA${stamp}`,
    company: `COA${stamp}`,
    stock: `STKA${stamp}`,
    venue: `VA${stamp}`,
  };
  const kept = {
    supplier: `SUPB${stamp}`,
    company: `COB${stamp}`,
    stock: `STKB${stamp}`,
    venue: `VB${stamp}`,
  };
  const admin = {
    email: `suppliermap-${stamp.toLowerCase()}@example.com`,
    name: 'Supplier Map Delete Admin',
    password: 'Browser!Test1',
  };
  const createdIds: number[] = [];

  try {
    const setup = await request.post(`${apiBase}/auth/setup`, { data: admin });
    expect(setup.ok(), await setup.text()).toBeTruthy();
    createdIds.push(await createMapping(request, removed));
    createdIds.push(await createMapping(request, kept));

    await login(page, admin);
    await openMenu(page, '#masterSupplierMapBtn', '#masterMapHeader');
    await expect(page.getByRole('heading', { name: 'Supplier Map' })).toBeVisible();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();

    const supplierTree = page.locator('#supplierMapTreeRoot');
    await page.locator('#smSupplierSearchInput').fill(removed.supplier, { timeout: 30_000 });
    const removedCard = supplierTree.locator(
      `.rixo-tree-card-wrapper[data-card-level="supplier"][data-path-supplier="${removed.supplier}"]`,
    );
    await expect(removedCard).toBeVisible();
    await expect(supplierTree).not.toContainText(kept.supplier);

    await removedCard.getByRole('button', { name: 'More actions' }).click({ timeout: 30_000 });
    await removedCard.getByRole('menuitem', { name: 'Delete branch' }).click({ timeout: 30_000 });
    const confirm = page.locator('#rixoMappingDeleteConfirmOverlay');
    await expect(confirm.getByRole('heading', { name: 'Delete entire supplier branch?' })).toBeVisible();
    await expect(confirm).toContainText('These rows are shared with Rixo Price Map. Deleting here also removes them there.');
    await confirm.locator('#rixoMappingDeleteConfirmOk').click({ timeout: 30_000 });

    await expect(confirm).toHaveCount(0);
    await expect(removedCard).toHaveCount(0);
    await expect(supplierTree).toContainText(`No suppliers match “${removed.supplier}”`);

    await page.locator('#smSupplierSearchClearBtn').click({ timeout: 30_000 });
    await page.locator('#smSupplierSearchInput').fill(kept.supplier, { timeout: 30_000 });
    const keptCard = supplierTree.locator(
      `.rixo-tree-card-wrapper[data-card-level="supplier"][data-path-supplier="${kept.supplier}"]`,
    );
    await expect(keptCard).toBeVisible();
    await expect(supplierTree.locator(`[data-path-supplier="${removed.supplier}"]`)).toHaveCount(0);

    await openMenu(page, '#masterRixoPriceMapBtn', '#masterMapHeader');
    await expect(page.getByRole('heading', { name: 'Rixo Price Map' })).toBeVisible();
    await expect(page.locator('#sidebarOverlay')).toBeHidden();

    const priceTree = page.locator('#rixoPriceMapTreeRoot');
    await page.locator('#rpmCompanySearchInput').fill(removed.company, { timeout: 30_000 });
    await expect(priceTree).toContainText(`No companies match “${removed.company}”`);
    await expect(priceTree.locator(`[data-value="${removed.company}"]`)).toHaveCount(0);
    await expect(priceTree.locator(`[data-value="${removed.supplier}"]`)).toHaveCount(0);

    await page.locator('#rpmCompanySearchClearBtn').click({ timeout: 30_000 });
    await page.locator('#rpmCompanySearchInput').fill(kept.company, { timeout: 30_000 });
    const keptCompany = priceTree.locator(
      `.rixo-tree-card[data-level="rixo_company"][data-value="${kept.company}"]`,
    );
    await expect(keptCompany).toBeVisible();
    await keptCompany.click({ timeout: 30_000 });
    const keptStock = priceTree.locator(`.rixo-tree-card[data-level="stock"][data-value="${kept.stock}"]`);
    await expect(keptStock).toBeVisible();
    await keptStock.click({ timeout: 30_000 });
    await expect(priceTree.locator(`.rixo-tree-card[data-level="supplier"][data-value="${kept.supplier}"]`)).toBeVisible();
    await expect(priceTree.locator(`[data-value="${removed.supplier}"]`)).toHaveCount(0);
    await expect(priceTree.locator(`[data-value="${removed.company}"]`)).toHaveCount(0);
    createdIds.length = 0;
  } finally {
    for (const id of createdIds) {
      await request.delete(`${apiBase}/rixo-mapping/${id}`);
    }
  }
});

function uniqueStamp() {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

async function createMapping(
  request: APIRequestContext,
  row: { supplier: string; company: string; stock: string; venue: string },
) {
  const created = await request.post(`${apiBase}/rixo-mapping/bulk`, {
    data: {
      rows: [
        {
          rixoCompany: row.company,
          auctionName: row.supplier,
          stockLocation: row.stock,
          venueId: row.venue,
          supportedVehicleType: 'Sedan',
          rixoPrice: '100',
          insertMode: 'FULL',
        },
      ],
    },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const body = (await created.json()) as { data?: Array<{ id?: number }> };
  const id = body.data?.[0]?.id;
  expect(id).toBeTruthy();
  return Number(id);
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

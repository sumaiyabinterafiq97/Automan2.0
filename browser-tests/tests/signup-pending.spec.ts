import { expect, test } from '@playwright/test';

test('signup is accepted as pending and that account cannot log in', async ({ page }) => {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`.toLowerCase();
  const email = `signup-${stamp}@example.com`;
  const name = 'Signup Pending';
  const password = 'Browser!Test1';

  await page.goto('/login');
  await expect(page.locator('#si_email')).toBeVisible();
  await page.locator('#toggleToSignup').click();

  await expect(page).toHaveURL(/\/signup$/);
  await expect(page.locator('#authModalTitle')).toHaveText('Sign Up');
  await expect(page.locator('#su_email')).toBeVisible();
  await expect(page.locator('#su_name')).toBeVisible();
  await expect(page.locator('#su_pass')).toBeVisible();
  await expect(page.locator('#btn_signup')).toBeVisible();

  await page.locator('#su_email').fill(email);
  await page.locator('#su_name').fill(name);
  await page.locator('#su_pass').fill(password);
  await page.locator('#btn_signup').click();

  await expect(page.locator('#signupMessage')).toBeVisible();
  await expect(page.locator('#signupMessage')).toHaveText(
    'Registration submitted. You will receive an email once an admin approves your account.',
  );
  await expect(page).toHaveURL(/\/signup$/);

  await page.locator('#toggleToSignin').click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('#si_email')).toBeVisible();
  await page.locator('#si_email').fill(email);
  await page.locator('#si_pass').fill(password);
  await page.locator('#btn_signin').click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('#signinMessage')).toBeVisible();
  await expect(page.locator('#signinMessage')).toHaveText('Invalid credentials');
  await expect(page.locator('#purchaseTable')).toBeHidden();
});

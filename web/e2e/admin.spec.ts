import { expect, test } from '@playwright/test';
import { ApiSession } from './support/api';
import { ADMIN } from './support/env';
import { fixtures, signIn } from './support/ui';

let createdTitleId: number | null = null;

test.afterAll(async () => {
  if (createdTitleId !== null) {
    const admin = await ApiSession.login(ADMIN.email, ADMIN.password);
    await admin.request('DELETE', `/api/admin/titles/${createdTitleId}`);
  }
});

test('admin creates a title, uploads a video with progress, and sees it become ready', async ({ page, context }) => {
  const name = `E2E Upload ${Date.now()}`;
  await signIn(page, ADMIN);
  await page.goto('/admin');

  await page.getByRole('button', { name: '+ New title' }).click();
  await page.getByLabel('Name').fill(name);
  await page.getByLabel('Synopsis').fill('Created by the admin end-to-end test.');
  await page.getByLabel('Maturity rating').selectOption('G');
  await page.getByRole('button', { name: 'Create title' }).click();
  await expect(page.getByRole('heading', { level: 2, name })).toBeVisible();
  createdTitleId = Number(await page.evaluate(async (title) => {
    const session = JSON.parse(localStorage.getItem('marquee.session')!);
    const titles = await fetch('/api/admin/titles', { headers: { Authorization: `Bearer ${session.accessToken}` } }).then((r) => r.json());
    return titles.find((t: { name: string }) => t.name === title).id;
  }, name));

  // Slow the upload so the progress bar is observable.
  const cdp = await context.newCDPSession(page);
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: 150 * 1024 });

  await page.locator('input[type="file"][accept="video/*"]').setInputFiles(fixtures().adminClip);
  await expect(page.getByRole('progressbar', { name: 'Upload progress' })).toBeVisible();
  await expect(page.getByText(/Uploading \d+%/)).toBeVisible();

  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
  await expect(page.locator('[data-testid^="asset-status-"]').first()).toHaveText(/Ready · 5s/, { timeout: 90_000 });

  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.locator('.title-admin__header').getByText('Published')).toBeVisible();
});

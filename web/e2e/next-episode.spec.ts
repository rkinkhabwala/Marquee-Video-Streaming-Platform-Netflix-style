import { expect, test } from '@playwright/test';
import { SERIES_NAME } from './global-setup';
import { VIEWER } from './support/env';
import { fixtures, pickProfile, resetProgress, signIn, waitForPlayback } from './support/ui';

test('offers the next episode at the end of an episode and plays it', async ({ page }) => {
  const fx = fixtures();
  const [first, second] = fx.episodeAssetIds;
  await resetProgress(VIEWER, 'Viewer', [first, second]);
  await signIn(page, VIEWER);
  await pickProfile(page, 'Viewer');

  await page.goto(`/?title=${fx.seriesId}`);
  const modal = page.getByRole('dialog', { name: SERIES_NAME });
  await modal.getByRole('button', { name: /Pattern 1/ }).click();
  await expect(page).toHaveURL(new RegExp(`/watch/${first}\\?title=${fx.seriesId}$`));
  await waitForPlayback(page);
  await expect(page.locator('.player__title')).toHaveText(`${SERIES_NAME} · S1:E1 Pattern 1`);

  await page.keyboard.press('ArrowRight'); // 12 s episode: jump to the last couple of seconds

  const overlay = page.getByRole('dialog', { name: 'Next episode' });
  await expect(overlay).toBeVisible({ timeout: 20_000 });
  await expect(overlay).toContainText('S1:E2 · Pattern 2');
  await expect(overlay).toContainText(/Next episode in \d+s/);

  await overlay.getByRole('button', { name: /Play now/ }).click();
  await expect(page).toHaveURL(new RegExp(`/watch/${second}\\?title=${fx.seriesId}$`));
  await waitForPlayback(page);
});

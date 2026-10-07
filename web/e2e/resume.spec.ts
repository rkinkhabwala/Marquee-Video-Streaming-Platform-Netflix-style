import { expect, test } from '@playwright/test';
import { MOVIE_NAME } from './global-setup';
import { VIEWER } from './support/env';
import { fixtures, pickProfile, resetProgress, signIn, videoTime, waitForPlayback } from './support/ui';

// Phase 2 acceptance: log in, pick a profile, play a title, close the tab, return, resume within ±10 s.
test('resumes within 10 seconds after the tab is closed', async ({ context }) => {
  const fx = fixtures();
  await resetProgress(VIEWER, 'Viewer', [fx.movieAssetId]);

  let page = await context.newPage();
  await signIn(page, VIEWER);
  await pickProfile(page, 'Viewer');

  await page.goto(`/search?q=${encodeURIComponent(MOVIE_NAME)}`);
  await page.getByRole('button', { name: MOVIE_NAME }).first().click();
  await page.getByRole('dialog').getByRole('button', { name: /Play/ }).click();
  await expect(page).toHaveURL(new RegExp(`/watch/${fx.movieAssetId}\\?title=${fx.movieId}$`));
  await waitForPlayback(page);

  // Skip ahead with the → shortcut (10 s per press), then keep watching a little.
  const beforeSkip = await videoTime(page);
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('ArrowRight');
  await expect.poll(() => videoTime(page)).toBeGreaterThan(beforeSkip + 18);
  await page.waitForTimeout(3000);
  const leftAt = await videoTime(page);

  await page.close({ runBeforeUnload: true });

  page = await context.newPage();
  await page.goto('/');
  const continueWatching = page.getByRole('region', { name: 'Continue Watching' });
  await continueWatching.getByRole('button', { name: MOVIE_NAME }).click();
  await expect(page).toHaveURL(new RegExp(`/watch/${fx.movieAssetId}`));
  await waitForPlayback(page);

  const resumedAt = await videoTime(page);
  test.info().annotations.push({ type: 'resume', description: `left at ${leftAt.toFixed(1)} s, resumed at ${resumedAt.toFixed(1)} s` });
  expect(Math.abs(resumedAt - leftAt)).toBeLessThanOrEqual(10);
});

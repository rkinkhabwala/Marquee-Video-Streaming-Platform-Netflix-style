import { expect, test } from '@playwright/test';
import { VIEWER } from './support/env';
import { fixtures, pickProfile, resetProgress, signIn, waitForPlayback } from './support/ui';

// Phase 2 acceptance: quality switches visibly when the network is throttled.
test.setTimeout(180_000);

test('quality can be picked manually and adaptive quality drops when the network is throttled', async ({ page, context }) => {
  const fx = fixtures();
  await resetProgress(VIEWER, 'Viewer', [fx.movieAssetId]);
  await signIn(page, VIEWER);
  await pickProfile(page, 'Viewer');
  await page.goto(`/watch/${fx.movieAssetId}?title=${fx.movieId}`);
  await waitForPlayback(page);

  const current = page.getByTestId('quality-current');
  const menu = page.getByLabel('Quality');

  await menu.selectOption({ label: '360p' });
  await expect(current).toHaveText('360p');
  await menu.selectOption({ label: '720p' });
  await expect(current).toHaveText('720p');

  // Back to automatic: on an unthrottled local network ABR settles on the top rendition.
  await menu.selectOption({ label: 'Auto' });
  await expect(current).toHaveText('Auto · 720p', { timeout: 30_000 });

  // Throttle to ~700 kbit/s, below the 480p and 720p bitrates (1.5 and 2.9 Mbit/s).
  const cdp = await context.newCDPSession(page);
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 100, downloadThroughput: (700 * 1024) / 8, uploadThroughput: (700 * 1024) / 8 });

  // The label shows the quality on screen, so the drop appears once playback moves past the up to
  // 60 s already buffered at 720p (the player's forward-buffer cap) into the lower-quality segments.
  await expect(current).toHaveText(/^Auto · (480p|360p)$/, { timeout: 120_000 });
  test.info().annotations.push({ type: 'abr', description: `throttled quality: ${await current.textContent()}` });
});

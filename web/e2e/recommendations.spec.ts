import { expect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { ApiSession } from './support/api';
import { ADMIN, RECSYS_API_KEY, RECSYS_CATALOG_URL, RECSYS_URL, VIEWER } from './support/env';
import { fixtures } from './support/ui';

/**
 * Phase 3 acceptance, against the real recommendation system (scripts/recsys.sh up):
 * after watching two titles in one genre, that profile's "Top picks" changes; killing recsys
 * does not break the home page. Skipped when recsys is not running.
 */
const RECSYS_API_CONTAINER = 'marquee-recsys-recommendation-api-1';

const DOCUMENTARIES = [
  ['Coral Kingdom', 'A nature documentary about coral reefs, ocean wildlife and the marine animals living on the reef.'],
  ['Kelp Forest Secrets', 'A nature documentary about kelp forests, sea otters and ocean wildlife along the coast.'],
  ['Deep Ocean Giants', 'A nature documentary about whales, giant squid and wildlife of the deep ocean.'],
  ['Reef Night Shift', 'A nature documentary following nocturnal reef wildlife and ocean predators after dark.'],
] as const;
const COMEDIES = [
  ['Cubicle Pranksters', 'A workplace comedy about office pranks, awkward meetings and silly coworkers.'],
  ['Meeting Mayhem', 'A workplace comedy about a disastrous board meeting and a clueless boss.'],
  ['Coffee Break Chaos', 'A workplace comedy about the office kitchen, gossip and a broken coffee machine.'],
  ['Desk Duel', 'A workplace comedy about two rival coworkers fighting over the window desk.'],
] as const;

interface Fixture {
  id: number;
  name: string;
  assetId: number;
}
type HomeRow = { title: string; items: { id: number; name: string }[] };

async function recsysUp(): Promise<boolean> {
  try {
    return (await fetch(`${RECSYS_URL}/actuator/health`)).ok;
  } catch {
    return false;
  }
}

async function ensureTitles(admin: ApiSession, genreName: string, specs: readonly (readonly [string, string])[]): Promise<Fixture[]> {
  const genres = await admin.request<{ id: number; name: string }[]>('GET', '/api/admin/genres');
  const genreId = genres.find((g) => g.name === genreName)!.id;
  const existing = await admin.request<{ id: number; name: string }[]>('GET', '/api/admin/titles');
  const result: Fixture[] = [];
  for (const [name, synopsis] of specs) {
    let title = existing.find((t) => t.name === name);
    title ??= await admin.request<{ id: number; name: string }>('POST', '/api/admin/titles', {
      type: 'MOVIE', name, synopsis, releaseYear: 2026, maturityRating: 'G', genreIds: [genreId],
    });
    const ready = (await admin.request<{ assetId: number; status: string }[]>('GET', `/api/admin/titles/${title.id}/assets`)).find((a) => a.status === 'READY');
    const assetId = ready?.assetId ?? (await admin.ingest(title.id, fixtures().adminClip));
    await admin.request('POST', `/api/admin/titles/${title.id}/publish`);
    result.push({ id: title.id, name, assetId });
  }
  return result;
}

async function waitForRecsysCatalog(items: Fixture[]) {
  await expect
    .poll(async () => {
      const found = await Promise.all(items.map((item) =>
        fetch(`${RECSYS_CATALOG_URL}/v1/catalog/items/mq-t-${item.id}`, { headers: { 'X-Api-Key': RECSYS_API_KEY } }).then((r) => r.ok)));
      return found.every(Boolean);
    }, { timeout: 30_000, message: 'fixtures reach the recsys catalog' })
    .toBe(true);
}

const topPicks = (rows: HomeRow[]) => rows.find((row) => row.title.startsWith('Top picks for'))!;

test.describe('recommendations', () => {
  test.setTimeout(300_000);

  test.beforeEach(async () => {
    test.skip(!(await recsysUp()), 'recsys is not running (scripts/recsys.sh up)');
  });

  test('top picks follow two watched titles in one genre, and the home page survives a recsys outage', async ({ page }) => {
    const admin = await ApiSession.login(ADMIN.email, ADMIN.password);
    const documentaries = await ensureTitles(admin, 'Documentary', DOCUMENTARIES);
    const comedies = await ensureTitles(admin, 'Comedy', COMEDIES);
    await waitForRecsysCatalog([...documentaries, ...comedies]);
    // Embeddings are computed asynchronously after the catalog write.
    await page.waitForTimeout(5000);

    const viewer = await ApiSession.login(VIEWER.email, VIEWER.password);
    const profileName = `Recs ${Date.now() % 100000}`;
    const profile = await viewer.request<{ id: number }>('POST', '/api/profiles', { name: profileName, isKids: false });
    const home = () => viewer.request<HomeRow[]>('GET', '/api/home', undefined, profile.id);

    try {
      const before = topPicks(await home());
      expect(before.title).toBe(`Top picks for ${profileName}`);

      // Watch two documentaries to the end (the same API calls the player makes).
      for (const doc of documentaries.slice(0, 2)) {
        await viewer.request('GET', `/api/playback/${doc.assetId}`, undefined, profile.id);
        await viewer.request('PUT', `/api/profiles/${profile.id}/progress/${doc.assetId}`, { positionSeconds: 5 }, profile.id);
      }

      const unwatchedDocs = new Set(documentaries.slice(2).map((d) => d.id));
      let after = before;
      await expect
        .poll(async () => {
          after = topPicks(await home());
          return after.items.slice(0, 2).every((item) => unwatchedDocs.has(item.id));
        }, { timeout: 60_000, intervals: [2000], message: 'top picks lead with the unwatched documentaries' })
        .toBe(true);

      expect(after.items.map((i) => i.id)).not.toEqual(before.items.map((i) => i.id));
      test.info().annotations.push(
        { type: 'top picks before', description: before.items.map((i) => i.name).join(', ') || '(empty)' },
        { type: 'top picks after', description: after.items.map((i) => i.name).join(', ') },
      );

      // The same rows in the web app.
      await page.goto('/login');
      const session = await page.request.post('/api/auth/login', { data: VIEWER }).then((r) => r.json());
      await page.evaluate(([s, p]) => {
        localStorage.setItem('marquee.session', s);
        localStorage.setItem('marquee.profile', p);
      }, [JSON.stringify(session), JSON.stringify({ id: profile.id, name: profileName, isKids: false })]);
      await page.goto('/');
      const picksRow = page.getByRole('region', { name: `Top picks for ${profileName}` });
      await expect(picksRow.getByRole('button', { name: documentaries[2].name })).toBeVisible();
      await expect(page.getByRole('region', { name: `Because you watched ${documentaries[1].name}` })).toBeVisible();

      // Outage: stop the recommendation API. Home must still render, with Top picks from Trending.
      execFileSync('docker', ['stop', RECSYS_API_CONTAINER]);
      try {
        const started = Date.now();
        const degraded = await home();
        const elapsed = Date.now() - started;
        expect(topPicks(degraded).items.length).toBeGreaterThan(0);
        expect(degraded.map((row) => row.title)).toContain('Trending');
        test.info().annotations.push({ type: 'home during outage', description: `${elapsed} ms, top picks: ${topPicks(degraded).items.map((i) => i.name).join(', ')}` });

        await page.reload();
        await expect(page.getByRole('region', { name: `Top picks for ${profileName}` })).toBeVisible();
        await expect(page.getByRole('region', { name: 'Trending' })).toBeVisible();
      } finally {
        execFileSync('docker', ['start', RECSYS_API_CONTAINER]);
        await expect.poll(recsysUp, { timeout: 120_000, intervals: [2000] }).toBe(true);
      }
    } finally {
      await viewer.request('DELETE', `/api/profiles/${profile.id}`);
    }
  });
});

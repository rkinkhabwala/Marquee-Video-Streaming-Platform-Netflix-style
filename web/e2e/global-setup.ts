import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ApiSession } from './support/api';
import { ADMIN, CACHE_DIR } from './support/env';

export const MOVIE_NAME = 'E2E Adaptive Movie';
const MOVIE_SECONDS = 150;
export const SERIES_NAME = 'E2E Test Series';
export const FIXTURES_FILE = resolve(CACHE_DIR, 'fixtures.json');

export interface Fixtures {
  movieId: number;
  movieAssetId: number;
  seriesId: number;
  episodeAssetIds: number[];
  adminClip: string;
}

/**
 * Makes sure the catalog has the titles the tests need, generating clips with ffmpeg and ingesting
 * them through the real pipeline. Existing READY fixtures from earlier runs are reused.
 */
export default async function globalSetup() {
  mkdirSync(CACHE_DIR, { recursive: true });
  const admin = await ApiSession.login(ADMIN.email, ADMIN.password);

  // 720p with film-grain noise so renditions have realistic bitrates, and longer than the player's
  // 60 s forward buffer so segments keep loading after the network is throttled (visible ABR switching).
  const movieClip = clip(`movie-720p-${MOVIE_SECONDS}s.mp4`, ['-f', 'lavfi', '-i', `testsrc2=size=1280x720:rate=30:duration=${MOVIE_SECONDS}`, '-f', 'lavfi', '-i', `sine=frequency=330:duration=${MOVIE_SECONDS}`, '-vf', 'noise=alls=25:allf=t', '-b:v', '4M']);
  const episodeClip = clip('episode-12s.mp4', ['-f', 'lavfi', '-i', 'testsrc2=size=854x480:rate=24:duration=12', '-f', 'lavfi', '-i', 'sine=frequency=550:duration=12']);
  const adminClip = clip('admin-5s.mp4', ['-f', 'lavfi', '-i', 'testsrc=size=640x360:rate=24:duration=5', '-f', 'lavfi', '-i', 'sine=frequency=880:duration=5']);

  const titles = await admin.request<{ id: number; name: string; published: boolean; seasons: { id: number; episodes: { videoAssetId: number | null }[] }[] }[]>('GET', '/api/admin/titles');
  const readyAssets = async (titleId: number, minSeconds = 0) =>
    (await admin.request<{ assetId: number; status: string; durationSeconds: number | null }[]>('GET', `/api/admin/titles/${titleId}/assets`))
      .filter((a) => a.status === 'READY' && (a.durationSeconds ?? 0) >= minSeconds)
      .map((a) => a.assetId);

  // Movie
  let movie = titles.find((t) => t.name === MOVIE_NAME);
  if (!movie) {
    movie = await admin.request('POST', '/api/admin/titles', { type: 'MOVIE', name: MOVIE_NAME, synopsis: 'Generated test pattern used by the end-to-end tests.', releaseYear: 2026, maturityRating: 'PG' });
  }
  let movieAssetId = (await readyAssets(movie!.id, MOVIE_SECONDS))[0];
  movieAssetId ??= await admin.ingest(movie!.id, movieClip);
  if (!movie!.published) await admin.request('POST', `/api/admin/titles/${movie!.id}/publish`);

  // Series with two episodes in season 1
  let series = titles.find((t) => t.name === SERIES_NAME);
  if (!series) {
    series = await admin.request('POST', '/api/admin/titles', { type: 'SERIES', name: SERIES_NAME, synopsis: 'Two short generated episodes.', releaseYear: 2026, maturityRating: 'TV-PG' });
  }
  const detail = await admin.request<typeof titles[number]>('GET', `/api/admin/titles/${series!.id}`);
  let episodeAssetIds = detail.seasons[0]?.episodes.map((e) => e.videoAssetId).filter((id): id is number => id !== null) ?? [];
  if (episodeAssetIds.length < 2) {
    const season = detail.seasons[0] ?? (await admin.request<{ id: number }>('POST', `/api/admin/titles/${series!.id}/seasons`, { seasonNumber: 1, name: 'Season 1' }));
    episodeAssetIds = [];
    for (const number of [1, 2]) {
      const assetId = await admin.ingest(series!.id, episodeClip);
      await admin.request('POST', `/api/admin/seasons/${season.id}/episodes`, { episodeNumber: number, name: `Pattern ${number}`, videoAssetId: assetId });
      episodeAssetIds.push(assetId);
    }
  }
  if (!series!.published) await admin.request('POST', `/api/admin/titles/${series!.id}/publish`);

  const fixtures: Fixtures = { movieId: movie!.id, movieAssetId, seriesId: series!.id, episodeAssetIds, adminClip };
  writeFileSync(FIXTURES_FILE, JSON.stringify(fixtures, null, 2));
}

function clip(name: string, args: string[]): string {
  const file = resolve(CACHE_DIR, name);
  if (!existsSync(file)) {
    execFileSync('ffmpeg', ['-y', '-v', 'error', ...args, '-c:v', 'libx264', '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-shortest', file]);
  }
  return file;
}

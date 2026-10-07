import { expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { FIXTURES_FILE, type Fixtures } from '../global-setup';
import { ApiSession } from './api';

export const fixtures = (): Fixtures => JSON.parse(readFileSync(FIXTURES_FILE, 'utf8')) as Fixtures;

export async function signIn(page: Page, user: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Password').fill(user.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/profiles$/);
}

export async function pickProfile(page: Page, name: string) {
  await page.getByRole('button', { name, exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
}

export const videoTime = (page: Page) => page.locator('video').evaluate((video: HTMLVideoElement) => video.currentTime);

/** Waits until the video is actually playing and its clock is advancing. */
export async function waitForPlayback(page: Page) {
  await expect
    .poll(() => page.locator('video').evaluate((video: HTMLVideoElement) => !video.paused && video.readyState >= 3), { timeout: 30_000 })
    .toBe(true);
  const start = await videoTime(page);
  await expect.poll(() => videoTime(page), { timeout: 15_000 }).toBeGreaterThan(start + 0.5);
}

/** Puts the user's profile back to "not started" for the given assets so tests are repeatable. */
export async function resetProgress(user: { email: string; password: string }, profileName: string, assetIds: number[]) {
  const session = await ApiSession.login(user.email, user.password);
  const profiles = await session.request<{ id: number; name: string }[]>('GET', '/api/profiles');
  const profile = profiles.find((p) => p.name === profileName);
  if (!profile) throw new Error(`No profile named ${profileName}`);
  for (const assetId of assetIds) {
    await session.request('PUT', `/api/profiles/${profile.id}/progress/${assetId}`, { positionSeconds: 0 });
  }
}

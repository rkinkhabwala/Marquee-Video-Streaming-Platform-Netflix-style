import { getProfile, getSession, setSession, type Session } from '../auth/session';
import type { AuthResponse } from './types';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** Send X-Profile-Id for the active profile (default true). */
  profileScoped?: boolean;
  /** Lets the request outlive the page, e.g. a final progress save on tab close. */
  keepalive?: boolean;
  signal?: AbortSignal;
}

let refreshInFlight: Promise<Session> | null = null;

/**
 * Refresh tokens rotate and only the latest is valid, so concurrent refreshes (several requests or
 * tabs) must collapse into one. Within a tab a shared promise does that; across tabs a Web Lock
 * serializes them, and a tab that waited reuses the tokens the other tab stored.
 */
export function refreshSession(staleAccessToken: string): Promise<Session> {
  refreshInFlight ??= runExclusive(async () => {
    const current = getSession();
    if (!current) throw new ApiError(401, 'Signed out');
    if (current.accessToken !== staleAccessToken) return current;

    const response = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken: current.refreshToken }),
    });
    if (!response.ok) {
      setSession(null);
      throw new ApiError(401, 'Session expired');
    }
    const next = (await response.json()) as AuthResponse;
    setSession(next);
    return next;
  }).finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

function runExclusive<T>(task: () => Promise<T>): Promise<T> {
  return typeof navigator !== 'undefined' && navigator.locks
    ? navigator.locks.request('marquee-token-refresh', task)
    : task();
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, profileScoped = true, keepalive, signal } = options;

  const send = (session: Session | null) => {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (session) headers.Authorization = `Bearer ${session.accessToken}`;
    const profile = profileScoped ? getProfile() : null;
    if (profile) headers['X-Profile-Id'] = String(profile.id);
    return fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      keepalive,
      signal,
    });
  };

  const session = getSession();
  let response = await send(session);
  if (response.status === 401 && session) {
    response = await send(await refreshSession(session.accessToken));
  }

  if (!response.ok) {
    if (response.status === 401) setSession(null);
    throw new ApiError(response.status, await errorMessage(response));
  }
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: string; title?: string };
    return problem.detail ?? problem.title ?? `Request failed (${response.status})`;
  } catch {
    return `Request failed (${response.status})`;
  }
}

/** Public URL for an artwork object key (served by the API's /media endpoint). */
export const mediaUrl = (key: string | null) => (key ? `/media/${key}` : null);

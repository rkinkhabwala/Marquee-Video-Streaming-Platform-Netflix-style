import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getSession, setProfile, setSession } from '../auth/session';
import { api, ApiError } from './client';

const session = { accessToken: 'old-access', refreshToken: 'refresh-1', userId: 1, email: 'v@x', role: 'USER' as const };
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

describe('api client', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    localStorage.clear();
    setSession(session);
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('sends the bearer token and the active profile header', async () => {
    setProfile({ id: 7, name: 'Viewer', isKids: false });
    fetchMock.mockResolvedValue(json(200, { ok: true }));

    await api('/api/home');

    const headers = fetchMock.mock.calls[0][1].headers;
    expect(headers.Authorization).toBe('Bearer old-access');
    expect(headers['X-Profile-Id']).toBe('7');
  });

  it('omits the profile header for account-level calls', async () => {
    setProfile({ id: 7, name: 'Viewer', isKids: false });
    fetchMock.mockResolvedValue(json(200, []));

    await api('/api/profiles', { profileScoped: false });

    expect(fetchMock.mock.calls[0][1].headers['X-Profile-Id']).toBeUndefined();
  });

  it('refreshes once for concurrent 401s and retries each request with the new token', async () => {
    let refreshCalls = 0;
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url === '/api/auth/refresh') {
        refreshCalls++;
        return json(200, { ...session, accessToken: 'new-access', refreshToken: 'refresh-2' });
      }
      const auth = (init.headers as Record<string, string>).Authorization;
      return auth === 'Bearer new-access' ? json(200, { url }) : json(401, { detail: 'expired' });
    });

    const results = await Promise.all([api<{ url: string }>('/api/a'), api<{ url: string }>('/api/b')]);

    expect(results.map((r) => r.url)).toEqual(['/api/a', '/api/b']);
    expect(refreshCalls).toBe(1);
    expect(getSession()?.refreshToken).toBe('refresh-2');
  });

  it('reuses tokens another tab already refreshed instead of spending the stale refresh token', async () => {
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url === '/api/auth/refresh') throw new Error('should not refresh');
      const auth = (init.headers as Record<string, string>).Authorization;
      if (auth === 'Bearer old-access') {
        // Simulate another tab rotating the tokens while this request was in flight.
        setSession({ ...session, accessToken: 'other-tab-access', refreshToken: 'refresh-9' });
        return json(401, {});
      }
      return json(200, { auth });
    });

    await expect(api('/api/home')).resolves.toEqual({ auth: 'Bearer other-tab-access' });
  });

  it('signs out when the refresh token is rejected', async () => {
    fetchMock.mockImplementation(async (url: string) => (url === '/api/auth/refresh' ? json(401, {}) : json(401, {})));

    await expect(api('/api/home')).rejects.toMatchObject({ status: 401 });
    expect(getSession()).toBeNull();
  });

  it('surfaces the problem detail as the error message', async () => {
    fetchMock.mockResolvedValue(json(403, { title: 'Forbidden', status: 403, detail: 'Title is not available for kids profiles' }));

    const error = await api('/api/titles/1').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(403);
    expect((error as ApiError).message).toBe('Title is not available for kids profiles');
  });

  it('returns undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(api('/api/my-list/1', { method: 'PUT' })).resolves.toBeUndefined();
  });
});

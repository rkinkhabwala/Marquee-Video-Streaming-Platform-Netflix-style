import { readFileSync } from 'node:fs';
import { API_URL } from './env';

/** Minimal API client for test setup and cleanup (Node side, talks to the API directly). */
export class ApiSession {
  private constructor(private readonly token: string) {}

  static async login(email: string, password: string): Promise<ApiSession> {
    const response = await fetch(`${API_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    if (!response.ok) throw new Error(`Login as ${email} failed (${response.status}). Is the Compose stack running with seed data?`);
    return new ApiSession(((await response.json()) as { accessToken: string }).accessToken);
  }

  async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const response = await fetch(`${API_URL}${path}`, {
      method,
      headers: { Authorization: `Bearer ${this.token}`, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) throw new Error(`${method} ${path} -> ${response.status}: ${await response.text()}`);
    return (response.status === 204 ? undefined : await response.json()) as T;
  }

  /** Uploads a local file as a new asset of the title and waits until it is READY. */
  async ingest(titleId: number, file: string, timeoutMs = 240_000): Promise<number> {
    const asset = await this.request<{ assetId: number; uploadUrl: string; contentType: string }>('POST', '/api/admin/assets', { titleId });
    const upload = await fetch(asset.uploadUrl, { method: 'PUT', headers: { 'Content-Type': asset.contentType }, body: readFileSync(file) });
    if (!upload.ok) throw new Error(`Upload failed (${upload.status})`);
    await this.request('POST', `/api/admin/assets/${asset.assetId}/complete`);

    const deadline = Date.now() + timeoutMs;
    for (;;) {
      const state = await this.request<{ status: string; errorMessage: string | null }>('GET', `/api/admin/assets/${asset.assetId}`);
      if (state.status === 'READY') return asset.assetId;
      if (state.status === 'FAILED') throw new Error(`Transcode failed: ${state.errorMessage}`);
      if (Date.now() > deadline) throw new Error(`Asset ${asset.assetId} not READY after ${timeoutMs} ms`);
      await new Promise((r) => setTimeout(r, 2000));
    }
  }
}

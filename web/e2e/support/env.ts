import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

/** Values from the repo's .env (if any), overridden by the process environment. */
function loadEnv(): Record<string, string> {
  const file = resolve(root, '.env');
  const values: Record<string, string> = {};
  if (existsSync(file)) {
    for (const line of readFileSync(file, 'utf8').split('\n')) {
      const match = /^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/.exec(line);
      if (match) values[match[1]] = match[2].replace(/^['"]|['"]$/g, '');
    }
  }
  return { ...values, ...(process.env as Record<string, string>) };
}

const env = loadEnv();

// Defaults match docker-compose.yml when no .env is present.
export const API_URL = env.API_URL ?? `http://localhost:${env.API_PORT ?? '8080'}`;
export const ADMIN = { email: env.SEED_ADMIN_EMAIL ?? 'admin@marquee.local', password: env.SEED_ADMIN_PASSWORD ?? 'admin_local_pass' };
export const VIEWER = { email: env.SEED_USER_EMAIL ?? 'viewer@marquee.local', password: env.SEED_USER_PASSWORD ?? 'viewer_local_pass' };
export const CACHE_DIR = resolve(root, 'web/e2e/.cache');

// Marquee's recsys instance (scripts/recsys.sh); see recsys/recsys.env.
export const RECSYS_URL = env.RECSYS_URL ?? 'http://localhost:18080';
export const RECSYS_CATALOG_URL = 'http://localhost:18082';
export const RECSYS_API_KEY = env.RECSYS_API_KEY ?? 'marquee-local-recsys-key';

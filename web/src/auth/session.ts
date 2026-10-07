import type { AuthResponse, Profile } from '../api/types';

// Tokens and the active profile live in localStorage so a reopened tab resumes the session.
const SESSION_KEY = 'marquee.session';
const PROFILE_KEY = 'marquee.profile';

export type Session = AuthResponse;
export type ActiveProfile = Pick<Profile, 'id' | 'name' | 'isKids'>;

type Listener = () => void;
const listeners = new Set<Listener>();

function read<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function write(key: string, value: unknown) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage can be unavailable (private mode); the session then lasts for this page only.
  }
  listeners.forEach((listener) => listener());
}

export const getSession = () => read<Session>(SESSION_KEY);
export const getProfile = () => read<ActiveProfile>(PROFILE_KEY);

export function setSession(session: Session | null) {
  write(SESSION_KEY, session);
  if (session === null) write(PROFILE_KEY, null);
}

export function setProfile(profile: ActiveProfile | null) {
  write(PROFILE_KEY, profile);
}

/** Notifies on changes from this tab and from other tabs (via the storage event). */
export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === SESSION_KEY || event.key === PROFILE_KEY) listener();
  };
  window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('storage', onStorage);
  };
}

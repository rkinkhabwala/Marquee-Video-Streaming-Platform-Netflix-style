import { useSyncExternalStore } from 'react';
import { getProfile, getSession, subscribe } from './session';

// Snapshots must be referentially stable between changes, so cache the parsed values by raw string.
function cached<T>(read: () => T) {
  let lastJson = '';
  let last: T = read();
  return () => {
    const value = read();
    const json = JSON.stringify(value);
    if (json !== lastJson) {
      lastJson = json;
      last = value;
    }
    return last;
  };
}

const sessionSnapshot = cached(getSession);
const profileSnapshot = cached(getProfile);

export const useSession = () => useSyncExternalStore(subscribe, sessionSnapshot);
export const useActiveProfile = () => useSyncExternalStore(subscribe, profileSnapshot);

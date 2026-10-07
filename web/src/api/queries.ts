import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as endpoints from './endpoints';
import { getProfile } from '../auth/session';

// Viewer data depends on the active profile, so its id is part of every viewer query key.
const profileKey = () => getProfile()?.id ?? 'none';

export const queryKeys = {
  profiles: ['profiles'] as const,
  home: () => ['home', profileKey()] as const,
  title: (id: number) => ['title', profileKey(), id] as const,
  search: (q: string) => ['search', profileKey(), q] as const,
  adminTitles: ['admin', 'titles'] as const,
  adminTitle: (id: number) => ['admin', 'title', id] as const,
  adminAssets: (titleId: number) => ['admin', 'assets', titleId] as const,
  genres: ['admin', 'genres'] as const,
};

export const useProfiles = () => useQuery({ queryKey: queryKeys.profiles, queryFn: endpoints.listProfiles });

export const useHome = () => useQuery({ queryKey: queryKeys.home(), queryFn: endpoints.getHome });

export const useTitle = (id: number | null) =>
  useQuery({ queryKey: queryKeys.title(id ?? 0), queryFn: () => endpoints.getTitle(id!), enabled: id !== null });

export const useSearch = (q: string) =>
  useQuery({ queryKey: queryKeys.search(q), queryFn: () => endpoints.search(q), enabled: q.trim().length > 0, placeholderData: (previous) => previous });

/** My List and ratings change the title detail and the home rows. */
function useInvalidateViewerData() {
  const client = useQueryClient();
  return (titleId: number) =>
    Promise.all([
      client.invalidateQueries({ queryKey: ['title', profileKey(), titleId] }),
      client.invalidateQueries({ queryKey: ['home'] }),
      client.invalidateQueries({ queryKey: ['search'] }),
    ]);
}

export function useMyListToggle() {
  const invalidate = useInvalidateViewerData();
  return useMutation({
    mutationFn: ({ titleId, inList }: { titleId: number; inList: boolean }) => endpoints.setInMyList(titleId, inList),
    onSuccess: (_data, { titleId }) => invalidate(titleId),
  });
}

export function useRate() {
  const invalidate = useInvalidateViewerData();
  return useMutation({
    mutationFn: ({ titleId, value }: { titleId: number; value: 1 | -1 }) => endpoints.rateTitle(titleId, value),
    onSuccess: (_data, { titleId }) => invalidate(titleId),
  });
}

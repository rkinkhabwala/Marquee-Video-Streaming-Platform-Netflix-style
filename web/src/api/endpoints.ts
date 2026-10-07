import { api } from './client';
import type {
  AdminSeason,
  AdminTitle,
  AssetState,
  AssetUpload,
  AuthResponse,
  Genre,
  HomeRow,
  NextEpisode,
  Page,
  Playback,
  PresignedUpload,
  Profile,
  TitleCard,
  TitleDetail,
  TitleType,
} from './types';

export const login = (email: string, password: string) =>
  api<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password }, profileScoped: false });

export const logout = () => api<void>('/api/auth/logout', { method: 'POST', profileScoped: false });

export const listProfiles = () => api<Profile[]>('/api/profiles', { profileScoped: false });

export const createProfile = (name: string, isKids: boolean) =>
  api<Profile>('/api/profiles', { method: 'POST', body: { name, isKids }, profileScoped: false });

export const getHome = () => api<HomeRow[]>('/api/home');

export const getTitle = (id: number) => api<TitleDetail>(`/api/titles/${id}`);

export const search = (q: string, page = 0) =>
  api<Page<TitleCard>>(`/api/search?${new URLSearchParams({ q, page: String(page), size: '40' })}`);

export const getNextEpisode = (titleId: number) => api<NextEpisode>(`/api/series/${titleId}/next-episode`);

export const setInMyList = (titleId: number, inList: boolean) =>
  api<void>(`/api/my-list/${titleId}`, { method: inList ? 'PUT' : 'DELETE' });

export const rateTitle = (titleId: number, value: 1 | -1) =>
  api<void>(`/api/ratings/${titleId}`, { method: 'PUT', body: { value } });

export const getPlayback = (assetId: number) => api<Playback>(`/api/playback/${assetId}`);

export const saveProgress = (profileId: number, assetId: number, positionSeconds: number, keepalive = false) =>
  api<void>(`/api/profiles/${profileId}/progress/${assetId}`, {
    method: 'PUT',
    body: { positionSeconds: Math.max(0, Math.floor(positionSeconds)) },
    keepalive,
  });

// Admin

export const adminListTitles = () => api<AdminTitle[]>('/api/admin/titles', { profileScoped: false });

export const adminGetTitle = (id: number) => api<AdminTitle>(`/api/admin/titles/${id}`, { profileScoped: false });

export interface TitleInput {
  type: TitleType;
  name: string;
  synopsis?: string;
  releaseYear?: number;
  maturityRating?: string;
  genreIds?: number[];
}

export const adminCreateTitle = (input: TitleInput) =>
  api<AdminTitle>('/api/admin/titles', { method: 'POST', body: input, profileScoped: false });

export const adminUpdateTitle = (id: number, changes: Partial<TitleInput> & { posterKey?: string; backdropKey?: string; published?: boolean }) =>
  api<AdminTitle>(`/api/admin/titles/${id}`, { method: 'PUT', body: changes, profileScoped: false });

export const adminPublishTitle = (id: number) =>
  api<AdminTitle>(`/api/admin/titles/${id}/publish`, { method: 'POST', profileScoped: false });

export const adminListGenres = () => api<Genre[]>('/api/admin/genres', { profileScoped: false });

export const adminCreateSeason = (titleId: number, seasonNumber: number, name: string) =>
  api<AdminSeason>(`/api/admin/titles/${titleId}/seasons`, { method: 'POST', body: { seasonNumber, name }, profileScoped: false });

export const adminCreateEpisode = (seasonId: number, episodeNumber: number, name: string, videoAssetId?: number) =>
  api<unknown>(`/api/admin/seasons/${seasonId}/episodes`, {
    method: 'POST',
    body: { episodeNumber, name, videoAssetId },
    profileScoped: false,
  });

export const adminUpdateEpisode = (episodeId: number, videoAssetId: number) =>
  api<unknown>(`/api/admin/episodes/${episodeId}`, { method: 'PUT', body: { videoAssetId }, profileScoped: false });

export const adminListAssets = (titleId: number) => api<AssetState[]>(`/api/admin/titles/${titleId}/assets`, { profileScoped: false });

export const adminCreateAsset = (titleId: number) =>
  api<AssetUpload>('/api/admin/assets', { method: 'POST', body: { titleId }, profileScoped: false });

export const adminCompleteAsset = (assetId: number) =>
  api<AssetState>(`/api/admin/assets/${assetId}/complete`, { method: 'POST', profileScoped: false });

export const adminPresignImage = (titleId: number, kind: 'poster' | 'backdrop', fileName: string) =>
  api<PresignedUpload>(`/api/admin/titles/${titleId}/images`, { method: 'POST', body: { kind, fileName }, profileScoped: false });

/**
 * PUTs a file straight to object storage through a presigned URL. Uses XHR because fetch has no
 * upload progress events.
 */
export function uploadToPresignedUrl(url: string, file: Blob, contentType: string, onProgress: (fraction: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Content-Type', contentType);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total);
    };
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Upload failed (${xhr.status})`)));
    xhr.onerror = () => reject(new Error('Upload failed (network error)'));
    xhr.send(file);
  });
}

// Mirrors the API's JSON contracts (Java records in marquee-api).

export type Role = 'USER' | 'ADMIN';
export type TitleType = 'MOVIE' | 'SERIES';
export type AssetStatus = 'UPLOADED' | 'TRANSCODING' | 'READY' | 'FAILED';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
  email: string;
  role: Role;
}

export interface Profile {
  id: number;
  userId: number;
  name: string;
  avatarKey: string | null;
  isKids: boolean;
}

export interface TitleCard {
  id: number;
  name: string;
  synopsis: string | null;
  type: TitleType;
  maturityRating: string | null;
  posterKey: string | null;
  backdropKey: string | null;
  resumeAt: number | null;
  resumeAssetId: number | null;
  inMyList: boolean;
}

export interface HomeRow {
  title: string;
  items: TitleCard[];
}

export interface EpisodeDetail {
  id: number;
  episodeNumber: number;
  name: string;
  synopsis: string | null;
  videoAssetId: number | null;
  durationSeconds: number | null;
  playable: boolean;
  resumeAt: number | null;
}

export interface SeasonDetail {
  id: number;
  seasonNumber: number;
  name: string;
  episodes: EpisodeDetail[];
}

export interface TitleDetail {
  id: number;
  type: TitleType;
  name: string;
  synopsis: string | null;
  releaseYear: number | null;
  maturityRating: string | null;
  posterKey: string | null;
  backdropKey: string | null;
  genres: string[];
  inMyList: boolean;
  rating: 1 | -1 | null;
  videoAssetId: number | null;
  durationSeconds: number | null;
  resumeAt: number | null;
  seasons: SeasonDetail[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Playback {
  manifestUrl: string;
  resumeAt: number;
  durationSeconds: number;
}

export interface NextEpisode {
  episodeId: number;
  seasonId: number;
  seasonNumber: number;
  episodeNumber: number;
  name: string;
  videoAssetId: number | null;
  titleId: number;
}

// Admin

export interface Genre {
  id: number;
  name: string;
}

export interface AdminEpisode {
  id: number;
  episodeNumber: number;
  name: string;
  synopsis: string | null;
  videoAssetId: number | null;
}

export interface AdminSeason {
  id: number;
  seasonNumber: number;
  name: string;
  episodes: AdminEpisode[];
}

export interface AdminTitle {
  id: number;
  type: TitleType;
  name: string;
  synopsis: string | null;
  releaseYear: number | null;
  maturityRating: string | null;
  posterKey: string | null;
  backdropKey: string | null;
  published: boolean;
  genres: string[];
  seasons: AdminSeason[];
}

export interface AssetUpload {
  assetId: number;
  titleId: number;
  sourceKey: string;
  uploadUrl: string;
  contentType: string;
  status: AssetStatus;
}

export interface AssetAttempt {
  attempt: number;
  status: string;
  startedAt: string | null;
  finishedAt: string | null;
  logTail: string | null;
}

export interface AssetState {
  assetId: number;
  titleId: number;
  status: AssetStatus;
  sourceKey: string;
  masterPlaylistKey: string | null;
  durationSeconds: number | null;
  errorMessage: string | null;
  attempts: AssetAttempt[];
}

export interface PresignedUpload {
  objectKey: string;
  uploadUrl: string;
  contentType: string;
}

export const MATURITY_RATINGS = ['G', 'PG', 'PG-13', 'R', 'NC-17', 'TV-Y', 'TV-Y7', 'TV-G', 'TV-PG', 'TV-14', 'TV-MA'] as const;

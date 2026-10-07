import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import {
  adminCreateEpisode,
  adminCreateSeason,
  adminCreateTitle,
  adminGetTitle,
  adminListAssets,
  adminListGenres,
  adminListTitles,
  adminPresignImage,
  adminPublishTitle,
  adminUpdateEpisode,
  adminUpdateTitle,
  uploadToPresignedUrl,
} from '../api/endpoints';
import { queryKeys } from '../api/queries';
import { MATURITY_RATINGS, type AdminSeason, type AdminTitle, type AssetState, type TitleType } from '../api/types';
import { StatusBadge } from '../admin/StatusBadge';
import { VideoUploader } from '../admin/VideoUploader';
import { Artwork } from '../components/Artwork';

const POLL_MS = 3000;

export function AdminPage() {
  const titles = useQuery({ queryKey: queryKeys.adminTitles, queryFn: adminListTitles });
  const [selected, setSelected] = useState<number | 'new' | null>(null);

  return (
    <main className="admin">
      <aside className="admin__list">
        <div className="admin__list-header">
          <h1>Titles</h1>
          <button type="button" className="button button--primary" onClick={() => setSelected('new')}>
            + New title
          </button>
        </div>
        {titles.isPending && <p className="muted">Loading…</p>}
        <ul>
          {titles.data?.map((title) => (
            <li key={title.id}>
              <button type="button" className={`admin__item ${selected === title.id ? 'admin__item--active' : ''}`} onClick={() => setSelected(title.id)}>
                <span>{title.name}</span>
                <span className="muted">
                  {title.type === 'MOVIE' ? 'Movie' : 'Series'} · {title.published ? 'Published' : 'Draft'}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </aside>
      <section className="admin__detail">
        {selected === 'new' && <CreateTitleForm onCreated={(id) => setSelected(id)} />}
        {typeof selected === 'number' && <TitleAdmin key={selected} titleId={selected} />}
        {selected === null && <p className="muted">Select a title or create a new one.</p>}
      </section>
    </main>
  );
}

function CreateTitleForm({ onCreated }: { onCreated: (id: number) => void }) {
  const queryClient = useQueryClient();
  const genres = useQuery({ queryKey: queryKeys.genres, queryFn: adminListGenres });
  const [type, setType] = useState<TitleType>('MOVIE');
  const [name, setName] = useState('');
  const [synopsis, setSynopsis] = useState('');
  const [releaseYear, setReleaseYear] = useState('');
  const [maturityRating, setMaturityRating] = useState('PG');
  const [genreIds, setGenreIds] = useState<number[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await adminCreateTitle({
        type,
        name: name.trim(),
        synopsis: synopsis.trim() || undefined,
        releaseYear: releaseYear ? Number(releaseYear) : undefined,
        maturityRating,
        genreIds,
      });
      await queryClient.invalidateQueries({ queryKey: queryKeys.adminTitles });
      onCreated(created.id);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not create the title.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="admin-form" onSubmit={submit}>
      <h2>New title</h2>
      <label>
        Type
        <select value={type} onChange={(e) => setType(e.target.value as TitleType)}>
          <option value="MOVIE">Movie</option>
          <option value="SERIES">Series</option>
        </select>
      </label>
      <label>
        Name
        <input required value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <label>
        Synopsis
        <textarea rows={3} value={synopsis} onChange={(e) => setSynopsis(e.target.value)} />
      </label>
      <div className="admin-form__row">
        <label>
          Release year
          <input type="number" min={1888} max={2100} value={releaseYear} onChange={(e) => setReleaseYear(e.target.value)} />
        </label>
        <label>
          Maturity rating
          <select value={maturityRating} onChange={(e) => setMaturityRating(e.target.value)}>
            {MATURITY_RATINGS.map((rating) => (
              <option key={rating}>{rating}</option>
            ))}
          </select>
        </label>
      </div>
      <fieldset>
        <legend>Genres</legend>
        <div className="admin-form__genres">
          {genres.data?.map((genre) => (
            <label key={genre.id} className="checkbox">
              <input
                type="checkbox"
                checked={genreIds.includes(genre.id)}
                onChange={(e) => setGenreIds((ids) => (e.target.checked ? [...ids, genre.id] : ids.filter((id) => id !== genre.id)))}
              />
              {genre.name}
            </label>
          ))}
        </div>
      </fieldset>
      {error && <p className="form-error">{error}</p>}
      <button type="submit" className="button button--primary" disabled={busy}>
        Create title
      </button>
    </form>
  );
}

function TitleAdmin({ titleId }: { titleId: number }) {
  const queryClient = useQueryClient();
  const title = useQuery({ queryKey: queryKeys.adminTitle(titleId), queryFn: () => adminGetTitle(titleId) });
  const assets = useQuery({
    queryKey: queryKeys.adminAssets(titleId),
    queryFn: () => adminListAssets(titleId),
    // Poll while anything is still being processed.
    refetchInterval: (query) => (query.state.data?.some((a) => a.status === 'UPLOADED' || a.status === 'TRANSCODING') ? POLL_MS : false),
  });
  const [error, setError] = useState<string | null>(null);

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.adminTitle(titleId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.adminAssets(titleId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.adminTitles }),
    ]);

  const publish = async () => {
    setError(null);
    try {
      await adminPublishTitle(titleId);
      await refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not publish.');
    }
  };

  if (title.isPending) return <p className="muted">Loading…</p>;
  if (!title.data) return <p className="form-error">Could not load the title.</p>;
  const t = title.data;
  const assetById = new Map((assets.data ?? []).map((a) => [a.assetId, a]));

  return (
    <div className="title-admin">
      <header className="title-admin__header">
        <div>
          <h2>{t.name}</h2>
          <p className="muted">
            {[t.type === 'MOVIE' ? 'Movie' : 'Series', t.releaseYear, t.maturityRating, t.genres.join(', ')].filter(Boolean).join(' · ')}
          </p>
        </div>
        {t.published ? (
          <span className="badge badge--ready">Published</span>
        ) : (
          <button type="button" className="button button--primary" onClick={publish}>
            Publish
          </button>
        )}
      </header>
      {error && <p className="form-error">{error}</p>}

      <ArtworkEditor title={t} onSaved={refresh} />

      {t.type === 'MOVIE' ? (
        <section className="admin-section">
          <h3>Video</h3>
          <VideoUploader titleId={titleId} onUploaded={refresh} />
          <AssetTable assets={assets.data ?? []} />
        </section>
      ) : (
        <SeasonsEditor title={t} assetById={assetById} onChanged={refresh} />
      )}
    </div>
  );
}

function AssetTable({ assets }: { assets: AssetState[] }) {
  if (assets.length === 0) return <p className="muted">No uploads yet.</p>;
  return (
    <table className="admin-table">
      <thead>
        <tr>
          <th>Asset</th>
          <th>Status</th>
          <th>Attempts</th>
          <th>Error</th>
        </tr>
      </thead>
      <tbody>
        {assets.map((asset) => (
          <tr key={asset.assetId}>
            <td>#{asset.assetId}</td>
            <td>
              <StatusBadge asset={asset} />
            </td>
            <td>{asset.attempts.length}</td>
            <td className="admin-table__error">{asset.errorMessage}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ArtworkEditor({ title, onSaved }: { title: AdminTitle; onSaved: () => Promise<unknown> }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const upload = async (kind: 'poster' | 'backdrop', file: File) => {
    setBusy(kind);
    setError(null);
    try {
      const presigned = await adminPresignImage(title.id, kind, file.name);
      await uploadToPresignedUrl(presigned.uploadUrl, file, presigned.contentType, () => {});
      await adminUpdateTitle(title.id, kind === 'poster' ? { posterKey: presigned.objectKey } : { backdropKey: presigned.objectKey });
      await onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setBusy(null);
    }
  };

  return (
    <section className="admin-section">
      <h3>Artwork</h3>
      <div className="artwork-editor">
        {(['poster', 'backdrop'] as const).map((kind) => (
          <div key={kind} className={`artwork-editor__slot artwork-editor__slot--${kind}`}>
            <Artwork name={title.name} imageKey={kind === 'poster' ? title.posterKey : title.backdropKey} kind={kind} />
            <label className="button button--secondary">
              {busy === kind ? 'Uploading…' : `Upload ${kind}`}
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                hidden
                disabled={busy !== null}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  event.target.value = '';
                  if (file) void upload(kind, file);
                }}
              />
            </label>
          </div>
        ))}
      </div>
      {error && <p className="form-error">{error}</p>}
    </section>
  );
}

function SeasonsEditor({ title, assetById, onChanged }: { title: AdminTitle; assetById: Map<number, AssetState>; onChanged: () => Promise<unknown> }) {
  const [error, setError] = useState<string | null>(null);
  const nextSeasonNumber = Math.max(0, ...title.seasons.map((s) => s.seasonNumber)) + 1;

  const addSeason = async () => {
    setError(null);
    try {
      await adminCreateSeason(title.id, nextSeasonNumber, `Season ${nextSeasonNumber}`);
      await onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not add the season.');
    }
  };

  return (
    <section className="admin-section">
      <div className="admin-section__header">
        <h3>Seasons</h3>
        <button type="button" className="button button--secondary" onClick={addSeason}>
          + Season {nextSeasonNumber}
        </button>
      </div>
      {error && <p className="form-error">{error}</p>}
      {title.seasons.length === 0 && <p className="muted">Add a season, then its episodes.</p>}
      {title.seasons.map((season) => (
        <SeasonEditor key={season.id} titleId={title.id} season={season} assetById={assetById} onChanged={onChanged} />
      ))}
    </section>
  );
}

function SeasonEditor({ titleId, season, assetById, onChanged }: { titleId: number; season: AdminSeason; assetById: Map<number, AssetState>; onChanged: () => Promise<unknown> }) {
  const nextEpisodeNumber = Math.max(0, ...season.episodes.map((e) => e.episodeNumber)) + 1;
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const addEpisode = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    try {
      await adminCreateEpisode(season.id, nextEpisodeNumber, name.trim() || `Episode ${nextEpisodeNumber}`);
      setName('');
      await onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not add the episode.');
    }
  };

  return (
    <div className="season-editor">
      <h4>{season.name}</h4>
      <table className="admin-table">
        <tbody>
          {season.episodes.map((episode) => (
            <tr key={episode.id}>
              <td>E{episode.episodeNumber}</td>
              <td>{episode.name}</td>
              <td>
                {episode.videoAssetId !== null ? (
                  <StatusBadge asset={assetById.get(episode.videoAssetId)} />
                ) : (
                  <VideoUploader
                    titleId={titleId}
                    label="Attach video"
                    onUploaded={async (assetId) => {
                      await adminUpdateEpisode(episode.id, assetId);
                      await onChanged();
                    }}
                  />
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <form className="inline-form" onSubmit={addEpisode}>
        <input aria-label={`Episode ${nextEpisodeNumber} name`} placeholder={`Episode ${nextEpisodeNumber} name`} value={name} onChange={(e) => setName(e.target.value)} />
        <button type="submit" className="button button--secondary">
          + Episode {nextEpisodeNumber}
        </button>
      </form>
      {error && <p className="form-error">{error}</p>}
    </div>
  );
}

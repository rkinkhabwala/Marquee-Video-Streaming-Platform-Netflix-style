import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useMyListToggle, useRate, useTitle } from '../api/queries';
import type { EpisodeDetail, TitleDetail } from '../api/types';
import { Artwork } from './Artwork';
import { formatTime } from './TitleCard';
import { usePlayTitle, watchPath } from './usePlayTitle';

interface Props {
  titleId: number;
  onClose: () => void;
}

export function TitleModal({ titleId, onClose }: Props) {
  const { data: title, error, isPending } = useTitle(titleId);
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const element = dialog.current;
    if (element && !element.open) element.showModal();
  }, []);

  return (
    <dialog
      ref={dialog}
      className="modal"
      aria-label={title?.name ?? 'Title details'}
      onClose={onClose}
      onClick={(event) => {
        // A click on the backdrop (the dialog element itself) closes it.
        if (event.target === dialog.current) dialog.current?.close();
      }}
    >
      <button type="button" className="modal__close" onClick={() => dialog.current?.close()} aria-label="Close">
        ✕
      </button>
      {isPending && <p className="modal__status">Loading…</p>}
      {error && <p className="modal__status">{error instanceof ApiError && error.status === 403 ? 'This title is not available on this profile.' : 'Could not load this title.'}</p>}
      {title && <TitleDetails title={title} />}
    </dialog>
  );
}

function TitleDetails({ title }: { title: TitleDetail }) {
  const { play, error: playError } = usePlayTitle();
  const myList = useMyListToggle();
  const rate = useRate();
  const resumable = title.type === 'MOVIE' && title.resumeAt !== null && title.videoAssetId !== null;
  const canPlay = title.type === 'MOVIE' ? title.videoAssetId !== null : title.seasons.some((s) => s.episodes.some((e) => e.playable));

  return (
    <article className="detail">
      <div className="detail__hero">
        <Artwork name={title.name} imageKey={title.backdropKey ?? title.posterKey} kind="backdrop" />
        <div className="detail__hero-shade" />
        <h2 className="detail__title">{title.name}</h2>
      </div>
      <div className="detail__body">
        <div className="detail__actions">
          <button type="button" className="button button--primary" disabled={!canPlay} onClick={() => play(title.id, resumable ? title.videoAssetId : null)}>
            ▶ {resumable ? `Resume ${formatTime(title.resumeAt!)}` : 'Play'}
          </button>
          <button
            type="button"
            className="icon-button"
            aria-pressed={title.inMyList}
            title={title.inMyList ? 'Remove from My List' : 'Add to My List'}
            disabled={myList.isPending}
            onClick={() => myList.mutate({ titleId: title.id, inList: !title.inMyList })}
          >
            {title.inMyList ? '✓' : '+'}
          </button>
          <button type="button" className="icon-button" aria-pressed={title.rating === 1} title="I like this" disabled={rate.isPending} onClick={() => rate.mutate({ titleId: title.id, value: 1 })}>
            👍
          </button>
          <button type="button" className="icon-button" aria-pressed={title.rating === -1} title="Not for me" disabled={rate.isPending} onClick={() => rate.mutate({ titleId: title.id, value: -1 })}>
            👎
          </button>
        </div>
        {!canPlay && <p className="muted">Not available to play yet.</p>}
        {playError && <p className="form-error">{playError}</p>}
        <p className="detail__meta">
          {[title.releaseYear, title.maturityRating, title.durationSeconds ? formatTime(title.durationSeconds) : null, title.type === 'SERIES' ? `${title.seasons.length} season${title.seasons.length === 1 ? '' : 's'}` : null]
            .filter(Boolean)
            .join(' · ')}
        </p>
        {title.synopsis && <p className="detail__synopsis">{title.synopsis}</p>}
        {title.genres.length > 0 && <p className="detail__genres">Genres: {title.genres.join(', ')}</p>}
        {title.type === 'SERIES' && <Episodes title={title} />}
      </div>
    </article>
  );
}

function Episodes({ title }: { title: TitleDetail }) {
  const navigate = useNavigate();
  const [seasonId, setSeasonId] = useState(title.seasons[0]?.id ?? null);
  const season = title.seasons.find((s) => s.id === seasonId) ?? title.seasons[0];
  if (!season) return <p className="muted">No episodes yet.</p>;

  const playEpisode = (episode: EpisodeDetail) => {
    if (episode.videoAssetId !== null) navigate(watchPath(episode.videoAssetId, title.id));
  };

  return (
    <section className="episodes" aria-label="Episodes">
      <div className="episodes__header">
        <h3>Episodes</h3>
        {title.seasons.length > 1 && (
          <select value={season.id} onChange={(event) => setSeasonId(Number(event.target.value))} aria-label="Season">
            {title.seasons.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name || `Season ${s.seasonNumber}`}
              </option>
            ))}
          </select>
        )}
      </div>
      <ol className="episodes__list">
        {season.episodes.map((episode) => (
          <li key={episode.id}>
            <button type="button" className="episode" disabled={!episode.playable} onClick={() => playEpisode(episode)}>
              <span className="episode__number">{episode.episodeNumber}</span>
              <span className="episode__text">
                <span className="episode__name">{episode.name}</span>
                {episode.synopsis && <span className="episode__synopsis">{episode.synopsis}</span>}
                {!episode.playable && <span className="muted">Coming soon</span>}
              </span>
              <span className="episode__duration">
                {episode.resumeAt !== null && episode.durationSeconds ? (
                  <progress max={episode.durationSeconds} value={episode.resumeAt} aria-label="Watched" />
                ) : episode.durationSeconds ? (
                  formatTime(episode.durationSeconds)
                ) : null}
              </span>
            </button>
          </li>
        ))}
      </ol>
    </section>
  );
}

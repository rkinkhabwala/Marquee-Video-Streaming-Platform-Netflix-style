import type { TitleCard } from '../api/types';
import { Artwork } from './Artwork';

interface Props {
  card: TitleCard;
  onPlay: () => void;
  onMoreInfo: () => void;
  playError: string | null;
}

export function HeroBanner({ card, onPlay, onMoreInfo, playError }: Props) {
  return (
    <section className="hero" aria-label="Featured title">
      <Artwork name={card.name} imageKey={card.backdropKey ?? card.posterKey} kind="backdrop" className="hero__art" />
      <div className="hero__shade" />
      <div className="hero__content">
        <h1 className="hero__title">{card.name}</h1>
        {card.synopsis && <p className="hero__synopsis">{card.synopsis}</p>}
        <div className="hero__actions">
          <button type="button" className="button button--primary" onClick={onPlay}>
            ▶ Play
          </button>
          <button type="button" className="button button--secondary" onClick={onMoreInfo}>
            ⓘ More info
          </button>
        </div>
        {playError && <p className="form-error">{playError}</p>}
      </div>
    </section>
  );
}

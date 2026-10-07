import type { TitleCard as Card } from '../api/types';
import { Artwork } from './Artwork';

interface Props {
  card: Card;
  /** Continue Watching cards play straight away instead of opening the detail modal. */
  onSelect: (card: Card) => void;
  showProgress?: boolean;
}

export function TitleCard({ card, onSelect, showProgress }: Props) {
  return (
    <button type="button" className="title-card" onClick={() => onSelect(card)} aria-label={card.name}>
      <Artwork name={card.name} imageKey={card.posterKey} kind="poster" />
      <span className="title-card__name">{card.name}</span>
      {showProgress && card.resumeAt !== null && (
        <span className="title-card__progress" aria-label={`Resume at ${formatTime(card.resumeAt)}`}>
          {formatTime(card.resumeAt)}
        </span>
      )}
    </button>
  );
}

export function formatTime(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = String(seconds % 60).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`;
}

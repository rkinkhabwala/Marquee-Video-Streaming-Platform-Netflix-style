import { useRef } from 'react';
import type { HomeRow, TitleCard as Card } from '../api/types';
import { TitleCard } from './TitleCard';

interface Props {
  row: HomeRow;
  onSelect: (card: Card) => void;
  showProgress?: boolean;
}

export function Row({ row, onSelect, showProgress }: Props) {
  const track = useRef<HTMLDivElement>(null);
  const scroll = (direction: 1 | -1) => {
    const element = track.current;
    if (element) element.scrollBy({ left: direction * element.clientWidth * 0.9, behavior: 'smooth' });
  };

  return (
    <section className="row" aria-label={row.title}>
      <h2 className="row__title">{row.title}</h2>
      <div className="row__viewport">
        <button type="button" className="row__arrow row__arrow--left" onClick={() => scroll(-1)} aria-label={`Scroll ${row.title} left`}>
          ‹
        </button>
        <div className="row__track" ref={track}>
          {row.items.map((card) => (
            <TitleCard key={card.id} card={card} onSelect={onSelect} showProgress={showProgress} />
          ))}
        </div>
        <button type="button" className="row__arrow row__arrow--right" onClick={() => scroll(1)} aria-label={`Scroll ${row.title} right`}>
          ›
        </button>
      </div>
    </section>
  );
}

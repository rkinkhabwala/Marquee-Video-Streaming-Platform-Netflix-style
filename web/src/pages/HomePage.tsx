import { useNavigate } from 'react-router-dom';
import { useHome } from '../api/queries';
import type { TitleCard } from '../api/types';
import { HeroBanner } from '../components/HeroBanner';
import { Row } from '../components/Row';
import { usePlayTitle, watchPath } from '../components/usePlayTitle';
import { useTitleModal } from '../components/useTitleModal';

const CONTINUE_WATCHING = 'Continue Watching';

export function HomePage() {
  const { data: rows, isPending, error } = useHome();
  const modal = useTitleModal();
  const navigate = useNavigate();
  const { play, error: playError } = usePlayTitle();

  if (isPending) return <p className="page-status">Loading…</p>;
  if (error) return <p className="page-status">Could not load the home page.</p>;

  const visibleRows = rows.filter((row) => row.items.length > 0);
  const featured = pickFeatured(rows);
  if (!featured) {
    return <p className="page-status">Nothing to watch yet. An admin can add titles from the Admin page.</p>;
  }

  const select = (card: TitleCard) => modal.open(card.id);
  const resume = (card: TitleCard) =>
    card.resumeAssetId !== null ? navigate(watchPath(card.resumeAssetId, card.id)) : modal.open(card.id);

  return (
    <main className="home">
      <HeroBanner card={featured} onPlay={() => play(featured.id, featured.resumeAssetId)} onMoreInfo={() => modal.open(featured.id)} playError={playError} />
      <div className="rows">
        {visibleRows.map((row) =>
          row.title === CONTINUE_WATCHING ? (
            <Row key={row.title} row={row} onSelect={resume} showProgress />
          ) : (
            <Row key={row.title} row={row} onSelect={select} />
          ),
        )}
      </div>
    </main>
  );
}

/** Feature the top trending title, else the newest release, else anything on the page. */
function pickFeatured(rows: { title: string; items: TitleCard[] }[]): TitleCard | undefined {
  const byTitle = (name: string) => rows.find((row) => row.title === name)?.items[0];
  return byTitle('Trending') ?? byTitle('New Releases') ?? rows.find((row) => row.items.length > 0)?.items[0];
}

import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useSearch } from '../api/queries';
import { TitleCard } from '../components/TitleCard';
import { useTitleModal } from '../components/useTitleModal';

const DEBOUNCE_MS = 300;

export function SearchPage() {
  const [params, setParams] = useSearchParams();
  const urlQuery = params.get('q') ?? '';
  const [input, setInput] = useState(urlQuery);
  const modal = useTitleModal();

  // Mirror the input into ?q= after a pause, so results are linkable and Back works.
  useEffect(() => {
    const timer = setTimeout(() => {
      if (input.trim() === urlQuery) return;
      setParams((current) => {
        const next = new URLSearchParams(current);
        if (input.trim()) next.set('q', input.trim());
        else next.delete('q');
        return next;
      }, { replace: true });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [input, urlQuery, setParams]);

  const { data, isFetching, error } = useSearch(urlQuery);

  return (
    <main className="search-page">
      <input
        className="search-input"
        type="search"
        placeholder="Titles, people, genres"
        aria-label="Search"
        autoFocus
        value={input}
        onChange={(event) => setInput(event.target.value)}
      />
      {error && <p className="form-error">Search failed.</p>}
      {urlQuery && data && (
        <p className="muted" aria-live="polite">
          {data.totalElements === 0 ? `No results for “${urlQuery}”.` : `${data.totalElements} result${data.totalElements === 1 ? '' : 's'} for “${urlQuery}”`}
          {isFetching && ' …'}
        </p>
      )}
      <div className="results-grid">
        {data?.content.map((card) => (
          <TitleCard key={card.id} card={card} onSelect={() => modal.open(card.id)} />
        ))}
      </div>
    </main>
  );
}

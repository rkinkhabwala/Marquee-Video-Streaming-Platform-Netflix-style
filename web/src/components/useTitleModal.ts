import { useSearchParams } from 'react-router-dom';

/** The title detail modal is driven by the ?title= query param, so it is linkable and closes with Back. */
export function useTitleModal() {
  const [params, setParams] = useSearchParams();
  const raw = params.get('title');
  const titleId = raw && /^\d+$/.test(raw) ? Number(raw) : null;

  const open = (id: number) =>
    setParams((current) => {
      const next = new URLSearchParams(current);
      next.set('title', String(id));
      return next;
    });

  const close = () =>
    setParams((current) => {
      const next = new URLSearchParams(current);
      next.delete('title');
      return next;
    });

  return { titleId, open, close };
}

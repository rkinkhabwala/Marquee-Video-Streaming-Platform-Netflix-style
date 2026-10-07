import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getNextEpisode, getPlayback } from '../api/endpoints';
import { useTitle } from '../api/queries';
import type { NextEpisode, TitleDetail } from '../api/types';
import { useActiveProfile } from '../auth/useSession';
import { watchPath } from '../components/usePlayTitle';
import { VideoPlayer } from '../player/VideoPlayer';

export function WatchPage() {
  const params = useParams();
  const [search] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const profile = useActiveProfile()!;
  const assetId = Number(params.assetId);
  const titleParam = search.get('title');
  const titleId = titleParam && /^\d+$/.test(titleParam) ? Number(titleParam) : null;

  // Always fetch fresh: the token is short-lived and resumeAt changes as you watch.
  const playback = useQuery({
    queryKey: ['playback', profile.id, assetId],
    queryFn: () => getPlayback(assetId),
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  });
  const { data: title } = useTitle(titleId);

  const back = () => (window.history.length > 1 ? navigate(-1) : navigate('/'));
  const refreshViewerData = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['home'] });
    void queryClient.invalidateQueries({ queryKey: ['title'] });
  }, [queryClient]);
  const playNext = useCallback(
    (next: NextEpisode) => {
      if (next.videoAssetId !== null) navigate(watchPath(next.videoAssetId, next.titleId), { replace: true });
    },
    [navigate],
  );

  if (playback.error) {
    const status = playback.error instanceof ApiError ? playback.error.status : 0;
    return (
      <main className="page-status">
        <p>{status === 403 ? 'This title is not available on this profile.' : status === 404 ? 'This video is not available.' : 'Could not start playback.'}</p>
        <button type="button" className="button button--secondary" onClick={back}>
          Back
        </button>
      </main>
    );
  }
  if (!playback.data) return <div className="player player--loading" aria-label="Loading" />;

  return (
    <VideoPlayer
      key={assetId}
      src={playback.data.manifestUrl}
      startAt={playback.data.resumeAt}
      title={playerTitle(title, assetId)}
      profileId={profile.id}
      assetId={assetId}
      onBack={back}
      onFinalSave={refreshViewerData}
      resolveNext={title?.type === 'SERIES' && titleId !== null ? () => getNextEpisode(titleId).catch(() => null) : undefined}
      onPlayNext={playNext}
    />
  );
}

function playerTitle(title: TitleDetail | undefined, assetId: number): string {
  if (!title) return '';
  for (const season of title.seasons) {
    const episode = season.episodes.find((e) => e.videoAssetId === assetId);
    if (episode) return `${title.name} · S${season.seasonNumber}:E${episode.episodeNumber} ${episode.name}`;
  }
  return title.name;
}

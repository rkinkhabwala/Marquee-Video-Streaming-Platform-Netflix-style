import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { ApiError } from '../api/client';
import { getNextEpisode, getTitle } from '../api/endpoints';

export const watchPath = (assetId: number, titleId: number) => `/watch/${assetId}?title=${titleId}`;

/**
 * Resolves which asset to play for a title (resume target, the movie's asset, or the series' next
 * episode) and opens the player.
 */
export function usePlayTitle() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  const play = async (titleId: number, resumeAssetId?: number | null) => {
    setError(null);
    try {
      let assetId = resumeAssetId ?? null;
      if (assetId === null) {
        const detail = await getTitle(titleId);
        assetId = detail.type === 'MOVIE' ? detail.videoAssetId : (await getNextEpisode(titleId)).videoAssetId;
      }
      if (assetId === null) {
        setError('This title is not available to play yet.');
        return;
      }
      navigate(watchPath(assetId, titleId));
    } catch (e) {
      setError(e instanceof ApiError && e.status === 404 ? 'This title is not available to play yet.' : 'Could not start playback.');
    }
  };

  return { play, error };
}

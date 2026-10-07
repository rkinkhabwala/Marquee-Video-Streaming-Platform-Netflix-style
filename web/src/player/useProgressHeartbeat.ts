import { useCallback, useEffect, useRef } from 'react';
import { saveProgress } from '../api/endpoints';

export const HEARTBEAT_MS = 10_000;

interface Options {
  profileId: number;
  assetId: number;
  /** Called after the final save when the player goes away (e.g. to refresh Continue Watching). */
  onFinalSave?: () => void;
}

/**
 * Saves the playback position every 10 s while playing, on pause, seek and end, and once more with
 * keepalive when the tab is hidden or closed. Nothing is saved until playback has actually started,
 * so closing the player before the resume seek lands cannot overwrite the resume point with 0.
 * Returns a flush function that saves the current position now.
 */
export function useProgressHeartbeat(video: HTMLVideoElement | null, { profileId, assetId, onFinalSave }: Options) {
  const flushRef = useRef<() => Promise<void>>(async () => {});
  const onFinalSaveRef = useRef(onFinalSave);
  onFinalSaveRef.current = onFinalSave;

  useEffect(() => {
    if (!video) return;
    let started = false;
    let lastSaved = -1;

    const save = (keepalive = false): Promise<void> => {
      if (!started) return Promise.resolve();
      const position = Math.floor(video.currentTime);
      if (!Number.isFinite(position) || position === lastSaved) return Promise.resolve();
      lastSaved = position;
      return saveProgress(profileId, assetId, position, keepalive).catch(() => {
        lastSaved = -1; // retry on the next tick
      });
    };
    flushRef.current = () => save();

    const onPlaying = () => {
      started = true;
    };
    const onCheckpoint = () => void save();
    const onHidden = () => {
      if (document.visibilityState === 'hidden') void save(true);
    };
    const onPageHide = () => void save(true);
    const timer = window.setInterval(() => {
      if (!video.paused) void save();
    }, HEARTBEAT_MS);

    video.addEventListener('playing', onPlaying);
    video.addEventListener('pause', onCheckpoint);
    video.addEventListener('seeked', onCheckpoint);
    video.addEventListener('ended', onCheckpoint);
    document.addEventListener('visibilitychange', onHidden);
    window.addEventListener('pagehide', onPageHide);

    return () => {
      window.clearInterval(timer);
      video.removeEventListener('playing', onPlaying);
      video.removeEventListener('pause', onCheckpoint);
      video.removeEventListener('seeked', onCheckpoint);
      video.removeEventListener('ended', onCheckpoint);
      document.removeEventListener('visibilitychange', onHidden);
      window.removeEventListener('pagehide', onPageHide);
      void save(true).finally(() => onFinalSaveRef.current?.());
    };
  }, [video, profileId, assetId]);

  return useCallback(() => flushRef.current(), []);
}

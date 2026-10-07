import Hls from 'hls.js';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { NextEpisode } from '../api/types';
import { formatTime } from '../components/TitleCard';
import { handlePlayerKey } from './hotkeys';
import { AUTO_LEVEL, levelLabel, sortLevels, type QualityLevel } from './quality';
import { useProgressHeartbeat } from './useProgressHeartbeat';

const IDLE_HIDE_MS = 3000;
const NEXT_EPISODE_COUNTDOWN = 10;
/** Offer the next episode this close to the end (only for episodes long enough to have credits). */
const NEXT_EPISODE_LEAD_SECONDS = 10;
const MIN_DURATION_FOR_LEAD = 30;

interface Props {
  src: string;
  startAt: number;
  title: string;
  profileId: number;
  assetId: number;
  onBack: () => void;
  onFinalSave?: () => void;
  /** Present for series: resolves the episode after this one (null when there is none). */
  resolveNext?: () => Promise<NextEpisode | null>;
  onPlayNext?: (next: NextEpisode) => void;
}

export function VideoPlayer({ src, startAt, title, profileId, assetId, onBack, onFinalSave, resolveNext, onPlayNext }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [video, setVideo] = useState<HTMLVideoElement | null>(null);
  const hlsRef = useRef<Hls | null>(null);

  const [playing, setPlaying] = useState(false);
  const [buffering, setBuffering] = useState(true);
  const [currentTime, setCurrentTime] = useState(startAt);
  const [duration, setDuration] = useState(0);
  const [muted, setMuted] = useState(false);
  const [volume, setVolume] = useState(1);
  const [levels, setLevels] = useState<QualityLevel[]>([]);
  const [currentLevel, setCurrentLevel] = useState<number>(-1);
  const [selectedLevel, setSelectedLevel] = useState<number>(AUTO_LEVEL);
  const [error, setError] = useState<string | null>(null);
  const [needsClick, setNeedsClick] = useState(false);
  const [idle, setIdle] = useState(false);
  const [next, setNext] = useState<NextEpisode | null>(null);
  const [countdown, setCountdown] = useState(NEXT_EPISODE_COUNTDOWN);
  const nextRequested = useRef(false);

  const flushProgress = useProgressHeartbeat(video, { profileId, assetId, onFinalSave });

  // Attach the stream: hls.js where MSE is available, native HLS (Safari) otherwise.
  useEffect(() => {
    if (!video) return;
    const tryPlay = () => video.play().catch(() => setNeedsClick(true));

    if (Hls.isSupported()) {
      const hls = new Hls({
        startPosition: startAt > 0 ? startAt : -1,
        // hls.js would otherwise buffer up to maxBufferSize / bitrate (minutes at 720p), so quality
        // changes would take minutes to show and a closed tab would waste the over-fetched video.
        maxBufferLength: 30,
        maxMaxBufferLength: 60,
      });
      hlsRef.current = hls;
      hls.on(Hls.Events.MANIFEST_PARSED, (_event, data) => {
        setLevels(data.levels.map((level, index) => ({ index, height: level.height, bitrate: level.bitrate })));
        tryPlay();
      });
      hls.on(Hls.Events.LEVEL_SWITCHED, (_event, data) => setCurrentLevel(data.level));
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) return;
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) hls.startLoad();
        else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError();
        else setError('Playback failed.');
      });
      hls.loadSource(src);
      hls.attachMedia(video);
      return () => {
        hls.destroy();
        hlsRef.current = null;
      };
    }

    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      const onLoaded = () => {
        if (startAt > 0) video.currentTime = startAt;
        tryPlay();
      };
      video.addEventListener('loadedmetadata', onLoaded, { once: true });
      video.src = src;
      return () => {
        video.removeEventListener('loadedmetadata', onLoaded);
        video.removeAttribute('src');
        video.load();
      };
    }

    setError('This browser cannot play HLS video.');
  }, [video, src, startAt]);

  const togglePlay = useCallback(() => {
    if (!video) return;
    if (video.paused) void video.play().then(() => setNeedsClick(false)).catch(() => setNeedsClick(true));
    else video.pause();
  }, [video]);

  const seekBy = useCallback(
    (seconds: number) => {
      if (!video) return;
      const end = Number.isFinite(video.duration) ? video.duration : Infinity;
      video.currentTime = Math.min(Math.max(0, video.currentTime + seconds), end);
    },
    [video],
  );

  const toggleFullscreen = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;
    if (document.fullscreenElement) void document.exitFullscreen();
    else void container.requestFullscreen?.();
  }, []);

  // Keyboard shortcuts; capture phase so the video element's own key handling never runs twice.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (handlePlayerKey(event, { togglePlay, seekBy, toggleFullscreen })) {
        event.preventDefault();
        event.stopPropagation();
        poke();
      }
    };
    window.addEventListener('keydown', onKey, { capture: true });
    return () => window.removeEventListener('keydown', onKey, { capture: true });
  });

  // Hide controls after a few seconds without input while playing.
  const idleTimer = useRef<number | undefined>(undefined);
  const poke = () => {
    setIdle(false);
    window.clearTimeout(idleTimer.current);
    idleTimer.current = window.setTimeout(() => setIdle(true), IDLE_HIDE_MS);
  };
  useEffect(() => () => window.clearTimeout(idleTimer.current), []);

  const offerNextEpisode = async () => {
    if (!resolveNext || nextRequested.current) return;
    nextRequested.current = true;
    await flushProgress(); // the API picks the episode after the most recently watched one
    const candidate = await resolveNext().catch(() => null);
    if (candidate?.videoAssetId != null && candidate.videoAssetId !== assetId) {
      setCountdown(NEXT_EPISODE_COUNTDOWN);
      setNext(candidate);
    }
  };

  useEffect(() => {
    if (!next) return;
    if (countdown <= 0) {
      onPlayNext?.(next);
      return;
    }
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [next, countdown, onPlayNext]);

  const onTimeUpdate = () => {
    if (!video) return;
    setCurrentTime(video.currentTime);
    const remaining = video.duration - video.currentTime;
    if (video.duration >= MIN_DURATION_FOR_LEAD && remaining <= NEXT_EPISODE_LEAD_SECONDS) void offerNextEpisode();
  };

  const chooseLevel = (level: number) => {
    setSelectedLevel(level);
    if (hlsRef.current) hlsRef.current.currentLevel = level; // -1 = automatic
  };

  const shown = levels.find((level) => level.index === currentLevel);
  const qualityText = selectedLevel === AUTO_LEVEL ? `Auto · ${levelLabel(shown)}` : levelLabel(shown);

  return (
    <div
      ref={containerRef}
      className={`player ${idle && playing ? 'player--idle' : ''}`}
      onMouseMove={poke}
      onTouchStart={poke}
      data-testid="player"
    >
      <video
        ref={setVideo}
        className="player__video"
        playsInline
        onClick={togglePlay}
        onDoubleClick={toggleFullscreen}
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onWaiting={() => setBuffering(true)}
        onPlaying={() => setBuffering(false)}
        onCanPlay={() => setBuffering(false)}
        onTimeUpdate={onTimeUpdate}
        onDurationChange={() => video && setDuration(video.duration)}
        onVolumeChange={() => {
          if (!video) return;
          setMuted(video.muted);
          setVolume(video.volume);
        }}
        onEnded={() => void offerNextEpisode()}
      />

      <div className="player__top">
        <button type="button" className="player__back" onClick={onBack} aria-label="Back">
          ←
        </button>
        <span className="player__title">{title}</span>
      </div>

      {buffering && !error && <div className="player__spinner" aria-label="Loading" />}
      {error && <p className="player__error">{error}</p>}
      {needsClick && !playing && (
        <button type="button" className="player__big-play" onClick={togglePlay} aria-label="Play">
          ▶
        </button>
      )}

      {next && (
        <div className="next-episode" role="dialog" aria-label="Next episode">
          <p className="muted">Next episode in {countdown}s</p>
          <p className="next-episode__name">
            S{next.seasonNumber}:E{next.episodeNumber} · {next.name}
          </p>
          <div className="next-episode__actions">
            <button type="button" className="button button--primary" onClick={() => onPlayNext?.(next)}>
              ▶ Play now
            </button>
            <button type="button" className="button button--secondary" onClick={() => setNext(null)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      <div className="player__controls">
        <input
          className="player__seek"
          type="range"
          min={0}
          max={duration || 0}
          step={0.1}
          value={Math.min(currentTime, duration || 0)}
          onChange={(event) => {
            if (video) video.currentTime = Number(event.target.value);
          }}
          aria-label="Seek"
        />
        <div className="player__bar">
          <button type="button" onClick={togglePlay} aria-label={playing ? 'Pause' : 'Play'}>
            {playing ? '❚❚' : '▶'}
          </button>
          <button type="button" onClick={() => seekBy(-10)} aria-label="Back 10 seconds">
            ↺10
          </button>
          <button type="button" onClick={() => seekBy(10)} aria-label="Forward 10 seconds">
            10↻
          </button>
          <button type="button" onClick={() => video && (video.muted = !video.muted)} aria-label={muted ? 'Unmute' : 'Mute'}>
            {muted || volume === 0 ? '🔇' : '🔊'}
          </button>
          <input
            className="player__volume"
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={muted ? 0 : volume}
            onChange={(event) => {
              if (!video) return;
              video.volume = Number(event.target.value);
              video.muted = video.volume === 0;
            }}
            aria-label="Volume"
          />
          <span className="player__time">
            {formatTime(currentTime)} / {formatTime(duration)}
          </span>
          <span className="player__spacer" />
          {levels.length > 0 && (
            <label className="player__quality">
              <span data-testid="quality-current">{qualityText}</span>
              <select value={selectedLevel} onChange={(event) => chooseLevel(Number(event.target.value))} aria-label="Quality">
                <option value={AUTO_LEVEL}>Auto</option>
                {sortLevels(levels).map((level) => (
                  <option key={level.index} value={level.index}>
                    {levelLabel(level)}
                  </option>
                ))}
              </select>
            </label>
          )}
          <button type="button" onClick={toggleFullscreen} aria-label="Fullscreen">
            ⛶
          </button>
        </div>
      </div>
    </div>
  );
}

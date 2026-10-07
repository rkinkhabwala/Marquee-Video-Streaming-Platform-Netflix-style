import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest';
import * as endpoints from '../api/endpoints';
import { HEARTBEAT_MS, useProgressHeartbeat } from './useProgressHeartbeat';

function fakeVideo() {
  const video = document.createElement('video');
  let time = 0;
  let paused = true;
  Object.defineProperty(video, 'currentTime', { get: () => time, set: (t: number) => (time = t) });
  Object.defineProperty(video, 'paused', { get: () => paused });
  return {
    video,
    play(at: number) {
      time = at;
      paused = false;
      video.dispatchEvent(new Event('playing'));
    },
    advance(seconds: number) {
      time += seconds;
    },
    pause() {
      paused = true;
      video.dispatchEvent(new Event('pause'));
    },
  };
}

describe('useProgressHeartbeat', () => {
  let save: MockInstance<typeof endpoints.saveProgress>;

  beforeEach(() => {
    vi.useFakeTimers();
    save = vi.spyOn(endpoints, 'saveProgress').mockResolvedValue(undefined);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('does not save before playback starts, so the resume point is never overwritten with 0', () => {
    const { video } = fakeVideo();
    const { unmount } = renderHook(() => useProgressHeartbeat(video, { profileId: 1, assetId: 9 }));

    act(() => vi.advanceTimersByTime(HEARTBEAT_MS * 3));
    unmount();

    expect(save).not.toHaveBeenCalled();
  });

  it('saves every 10 s while playing and on pause', () => {
    const player = fakeVideo();
    renderHook(() => useProgressHeartbeat(player.video, { profileId: 1, assetId: 9 }));

    act(() => player.play(120));
    player.advance(10);
    act(() => vi.advanceTimersByTime(HEARTBEAT_MS));
    player.advance(10);
    act(() => vi.advanceTimersByTime(HEARTBEAT_MS));
    player.advance(3.7);
    act(() => player.pause());

    expect(save.mock.calls.map((call) => call[2])).toEqual([130, 140, 143]);
    expect(save).toHaveBeenCalledWith(1, 9, 130, false);
  });

  it('skips duplicate positions while paused', () => {
    const player = fakeVideo();
    renderHook(() => useProgressHeartbeat(player.video, { profileId: 1, assetId: 9 }));

    act(() => player.play(50));
    act(() => player.pause());
    act(() => vi.advanceTimersByTime(HEARTBEAT_MS * 2));
    act(() => player.video.dispatchEvent(new Event('seeked')));

    expect(save).toHaveBeenCalledTimes(1);
  });

  it('saves with keepalive when the tab is closed and on unmount', async () => {
    const player = fakeVideo();
    const onFinalSave = vi.fn();
    const { unmount } = renderHook(() => useProgressHeartbeat(player.video, { profileId: 1, assetId: 9, onFinalSave }));

    act(() => player.play(30));
    player.advance(5);
    act(() => window.dispatchEvent(new Event('pagehide')));
    player.advance(2);
    unmount();
    await vi.runAllTimersAsync();

    expect(save.mock.calls).toEqual([
      [1, 9, 35, true],
      [1, 9, 37, true],
    ]);
    expect(onFinalSave).toHaveBeenCalledTimes(1);
  });
});

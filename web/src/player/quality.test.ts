import { describe, expect, it } from 'vitest';
import { levelLabel, sortLevels } from './quality';

describe('quality levels', () => {
  it('lists the highest quality first and labels by height', () => {
    const levels = [
      { index: 0, height: 360, bitrate: 896000 },
      { index: 1, height: 720, bitrate: 2928000 },
      { index: 2, height: 480, bitrate: 1496000 },
    ];
    expect(sortLevels(levels).map(levelLabel)).toEqual(['720p', '480p', '360p']);
    expect(levelLabel(undefined)).toBe('—');
  });
});

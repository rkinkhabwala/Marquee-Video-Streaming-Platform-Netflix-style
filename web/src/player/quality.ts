export interface QualityLevel {
  index: number;
  height: number;
  bitrate: number;
}

export const AUTO_LEVEL = -1;

export const levelLabel = (level: QualityLevel | undefined) => (level ? `${level.height}p` : '—');

/** Highest quality first, as shown in the menu. */
export const sortLevels = (levels: QualityLevel[]) => [...levels].sort((a, b) => b.height - a.height || b.bitrate - a.bitrate);

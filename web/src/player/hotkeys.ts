export const SEEK_STEP_SECONDS = 10;

export interface PlayerControls {
  togglePlay: () => void;
  seekBy: (seconds: number) => void;
  toggleFullscreen: () => void;
}

/** Maps a key to a player action. Returns true when the key was handled. */
export function handlePlayerKey(event: Pick<KeyboardEvent, 'key' | 'target' | 'ctrlKey' | 'metaKey' | 'altKey'>, controls: PlayerControls): boolean {
  if (event.ctrlKey || event.metaKey || event.altKey) return false;
  const target = event.target as HTMLElement | null;
  if (target && ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName) && (target as HTMLInputElement).type !== 'range') return false;

  switch (event.key) {
    case ' ':
    case 'Spacebar':
      controls.togglePlay();
      return true;
    case 'ArrowLeft':
      controls.seekBy(-SEEK_STEP_SECONDS);
      return true;
    case 'ArrowRight':
      controls.seekBy(SEEK_STEP_SECONDS);
      return true;
    case 'f':
    case 'F':
      controls.toggleFullscreen();
      return true;
    default:
      return false;
  }
}

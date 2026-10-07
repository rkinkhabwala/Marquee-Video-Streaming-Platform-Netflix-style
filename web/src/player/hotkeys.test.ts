import { describe, expect, it, vi } from 'vitest';
import { handlePlayerKey } from './hotkeys';

const controls = () => ({ togglePlay: vi.fn(), seekBy: vi.fn(), toggleFullscreen: vi.fn() });
const key = (k: string, target: Partial<HTMLElement & { type: string }> = { tagName: 'DIV' }) =>
  ({ key: k, target: target as unknown as EventTarget, ctrlKey: false, metaKey: false, altKey: false });

describe('handlePlayerKey', () => {
  it('maps space, arrows and F to player actions', () => {
    const c = controls();
    expect(handlePlayerKey(key(' '), c)).toBe(true);
    expect(handlePlayerKey(key('ArrowLeft'), c)).toBe(true);
    expect(handlePlayerKey(key('ArrowRight'), c)).toBe(true);
    expect(handlePlayerKey(key('f'), c)).toBe(true);
    expect(handlePlayerKey(key('F'), c)).toBe(true);

    expect(c.togglePlay).toHaveBeenCalledTimes(1);
    expect(c.seekBy.mock.calls).toEqual([[-10], [10]]);
    expect(c.toggleFullscreen).toHaveBeenCalledTimes(2);
  });

  it('ignores other keys, modified keys and typing in form fields', () => {
    const c = controls();
    expect(handlePlayerKey(key('a'), c)).toBe(false);
    expect(handlePlayerKey({ ...key('f'), metaKey: true }, c)).toBe(false);
    expect(handlePlayerKey(key(' ', { tagName: 'INPUT', type: 'text' }), c)).toBe(false);
    expect(handlePlayerKey(key(' ', { tagName: 'SELECT' }), c)).toBe(false);
    expect(c.togglePlay).not.toHaveBeenCalled();
  });

  it('still handles keys while a range slider (seek bar) has focus', () => {
    const c = controls();
    expect(handlePlayerKey(key('ArrowRight', { tagName: 'INPUT', type: 'range' }), c)).toBe(true);
    expect(c.seekBy).toHaveBeenCalledWith(10);
  });
});

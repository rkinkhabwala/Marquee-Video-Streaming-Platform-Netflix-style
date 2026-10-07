import type { AssetState } from '../api/types';

export function StatusBadge({ asset }: { asset: AssetState | undefined }) {
  if (!asset) return <span className="badge">No video</span>;
  const attempt = asset.attempts.at(-1);
  const text =
    asset.status === 'TRANSCODING' && attempt ? `Transcoding (attempt ${attempt.attempt})` : asset.status === 'READY' && asset.durationSeconds ? `Ready · ${asset.durationSeconds}s` : asset.status.charAt(0) + asset.status.slice(1).toLowerCase();
  return (
    <span className={`badge badge--${asset.status.toLowerCase()}`} title={asset.errorMessage ?? undefined} data-testid={`asset-status-${asset.assetId}`}>
      {text}
    </span>
  );
}

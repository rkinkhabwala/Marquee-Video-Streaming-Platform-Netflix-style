import { useState } from 'react';
import { mediaUrl } from '../api/client';

interface Props {
  name: string;
  imageKey: string | null;
  kind: 'poster' | 'backdrop';
  className?: string;
}

/** Title artwork, falling back to a generated gradient with the title name when there is no image. */
export function Artwork({ name, imageKey, kind, className = '' }: Props) {
  const [failed, setFailed] = useState(false);
  const url = mediaUrl(imageKey);

  if (url && !failed) {
    return <img className={`artwork artwork--${kind} ${className}`} src={url} alt={name} loading="lazy" onError={() => setFailed(true)} />;
  }
  return (
    <div className={`artwork artwork--${kind} artwork--fallback ${className}`} style={{ background: gradientFor(name) }} role="img" aria-label={name}>
      <span>{name}</span>
    </div>
  );
}

export function gradientFor(seed: string): string {
  let hash = 0;
  for (const char of seed) hash = (hash * 31 + char.charCodeAt(0)) | 0;
  const hue = Math.abs(hash) % 360;
  return `linear-gradient(135deg, hsl(${hue} 55% 32%), hsl(${(hue + 50) % 360} 60% 14%))`;
}

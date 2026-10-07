import { useState } from 'react';
import { uploadVideo, type UploadStage } from './uploadVideo';

interface Props {
  titleId: number;
  label?: string;
  /** Called with the new asset id once the transcode is queued. */
  onUploaded: (assetId: number) => void | Promise<unknown>;
}

const STAGE_TEXT: Record<UploadStage, string> = {
  creating: 'Preparing upload…',
  uploading: 'Uploading',
  queueing: 'Queueing transcode…',
};

export function VideoUploader({ titleId, label = 'Upload video', onUploaded }: Props) {
  const [stage, setStage] = useState<UploadStage | null>(null);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const start = async (file: File) => {
    setError(null);
    setProgress(0);
    try {
      const assetId = await uploadVideo(titleId, file, setStage, setProgress);
      await onUploaded(assetId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setStage(null);
    }
  };

  return (
    <div className="uploader">
      {stage === null ? (
        <label className="button button--secondary uploader__pick">
          {label}
          <input
            type="file"
            accept="video/*"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = '';
              if (file) void start(file);
            }}
          />
        </label>
      ) : (
        <div className="uploader__progress" aria-live="polite">
          <span>
            {STAGE_TEXT[stage]}
            {stage === 'uploading' && ` ${Math.round(progress * 100)}%`}
          </span>
          <progress max={1} value={stage === 'uploading' ? progress : undefined} aria-label="Upload progress" />
        </div>
      )}
      {error && <p className="form-error">{error}</p>}
    </div>
  );
}

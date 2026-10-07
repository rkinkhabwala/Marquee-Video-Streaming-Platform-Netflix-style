import { adminCompleteAsset, adminCreateAsset, uploadToPresignedUrl } from '../api/endpoints';

export type UploadStage = 'creating' | 'uploading' | 'queueing';

/** Creates an asset, uploads the file straight to object storage, then queues the transcode. */
export async function uploadVideo(titleId: number, file: File, onStage: (stage: UploadStage) => void, onProgress: (fraction: number) => void): Promise<number> {
  onStage('creating');
  const asset = await adminCreateAsset(titleId);
  onStage('uploading');
  await uploadToPresignedUrl(asset.uploadUrl, file, asset.contentType, onProgress);
  onStage('queueing');
  await adminCompleteAsset(asset.assetId);
  return asset.assetId;
}

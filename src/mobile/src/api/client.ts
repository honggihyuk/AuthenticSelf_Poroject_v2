/**
 * Thin REST client for the Spring backend.
 *
 * UC-01-photo-upload only needs ONE endpoint, `POST /api/v1/spaces/photo`.
 * We use XMLHttpRequest instead of fetch because fetch in React Native has
 * no upload-progress hook — and FR-7 requires a determinate progress bar.
 *
 * Task-4 adds two JSON endpoints consumed by the polling and style-selection
 * screens: `GET /api/v1/spaces/{roomId}` and
 * `PUT /api/v1/spaces/{roomId}/preferred-style`.
 */

import type { PreferredStyle, Style } from '../types/style';

export type UploadSuccess = {
  roomId: string;
  uploadUrl: string;
  status: 'PENDING_ANALYSIS';
};

export type UploadErrorBody = {
  errorCode: string;
  message: string;
  correlationId: string;
};

export class UploadFailedError extends Error {
  constructor(public httpStatus: number, public body: UploadErrorBody | null) {
    super(body?.errorCode ?? `HTTP ${httpStatus}`);
  }
}

export class ApiError extends Error {
  constructor(public httpStatus: number, public body: UploadErrorBody | null) {
    super(body?.errorCode ?? `HTTP ${httpStatus}`);
  }
}

export type UploadPhotoArgs = {
  baseUrl: string;
  userId: string;
  uri: string;          // local file uri from expo-image-picker
  fileName: string;
  mimeType: string;
  onProgress?: (pct: number) => void;
};

export type SpaceState = {
  roomId: string;
  status: 'PENDING_ANALYSIS' | 'ANALYZED' | 'FAILED';
  dimensions: string | null;
  mainColor: string | null;
  style: Style | null;
  styleConfidence: number | null;
  preferredStyle: PreferredStyle | null;
  analysisDate: string | null;
  uploadedAt: string | null;
};

/**
 * Upload a photo to POST /api/v1/spaces/photo.
 *
 * Rejects with an {@link UploadFailedError} on any non-2xx response, carrying
 * the parsed {errorCode, message, correlationId} envelope when available.
 */
export function uploadPhoto(args: UploadPhotoArgs): Promise<UploadSuccess> {
  const { baseUrl, userId, uri, fileName, mimeType, onProgress } = args;

  return new Promise<UploadSuccess>((resolve, reject) => {
    const form = new FormData();
    // React Native's FormData accepts this shape for a local file URI.
    form.append('file', { uri, name: fileName, type: mimeType } as unknown as Blob);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${baseUrl}/api/v1/spaces/photo`);
    xhr.setRequestHeader('X-User-Id', userId);

    if (xhr.upload && onProgress) {
      xhr.upload.onprogress = (ev: ProgressEvent) => {
        if (ev.lengthComputable && ev.total > 0) {
          onProgress(Math.round((ev.loaded / ev.total) * 100));
        }
      };
    }

    xhr.onload = () => {
      const status = xhr.status;
      let parsed: unknown = null;
      try {
        parsed = xhr.responseText ? JSON.parse(xhr.responseText) : null;
      } catch {
        parsed = null;
      }
      if (status >= 200 && status < 300) {
        resolve(parsed as UploadSuccess);
      } else {
        reject(new UploadFailedError(status, parsed as UploadErrorBody | null));
      }
    };

    xhr.onerror = () => reject(new UploadFailedError(0, null));
    xhr.send(form);
  });
}

// ---------------------------------------------------------------------------
// Task-4 FR-21 — /api/v1/spaces JSON endpoints
// ---------------------------------------------------------------------------

type JsonRequestOpts = {
  baseUrl: string;
  userId: string;
  roomId: string;
};

/**
 * GET /api/v1/spaces/{roomId} — polling endpoint for `AnalyzingScreen`.
 *
 * Rejects with {@link ApiError} on any non-2xx response, carrying the
 * parsed envelope when available.
 */
export async function getSpace(opts: JsonRequestOpts): Promise<SpaceState> {
  const { baseUrl, userId, roomId } = opts;
  const url = `${baseUrl}/api/v1/spaces/${encodeURIComponent(roomId)}`;
  const res = await fetch(url, {
    method: 'GET',
    headers: { 'X-User-Id': userId, Accept: 'application/json' },
  });
  return parseJsonOrThrow<SpaceState>(res);
}

/**
 * PUT /api/v1/spaces/{roomId}/preferred-style — set user's chosen style.
 */
export async function setPreferredStyle(
  opts: JsonRequestOpts & { preferredStyle: PreferredStyle },
): Promise<SpaceState> {
  const { baseUrl, userId, roomId, preferredStyle } = opts;
  const url = `${baseUrl}/api/v1/spaces/${encodeURIComponent(roomId)}/preferred-style`;
  const res = await fetch(url, {
    method: 'PUT',
    headers: {
      'X-User-Id': userId,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ preferredStyle }),
  });
  return parseJsonOrThrow<SpaceState>(res);
}

async function parseJsonOrThrow<T>(res: Response): Promise<T> {
  const text = await res.text();
  let parsed: unknown = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = null;
  }
  if (res.ok) {
    return parsed as T;
  }
  throw new ApiError(res.status, parsed as UploadErrorBody | null);
}

/**
 * Object detection API client. Calls Backend's
 * `GET /api/v1/spaces/{roomId}/objects` which proxies the AI service's
 * `/analyze/objects` YOLO endpoint.
 */

import { bearerHeader } from './client';

export type DetectedObject = {
  label: string;
  bbox: [number, number, number, number];   // x1, y1, x2, y2 in absolute pixels
  confidence: number;
};

export type ObjectsResponse = {
  roomId: string;
  status: 'OK';
  imageWidth: number;
  imageHeight: number;
  objects: DetectedObject[];
  processingMs: number;
};

type GetObjectsArgs = {
  baseUrl: string;
  userId: string;
  roomId: string;
};

export async function getRoomObjects(opts: GetObjectsArgs): Promise<ObjectsResponse> {
  const { baseUrl, userId, roomId } = opts;
  const url = `${baseUrl}/api/v1/spaces/${encodeURIComponent(roomId)}/objects`;
  const res = await fetch(url, {
    method: 'GET',
    headers: { 'X-User-Id': userId, ...bearerHeader(), Accept: 'application/json' },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`getRoomObjects failed: ${res.status} ${body.slice(0, 200)}`);
  }
  return (await res.json()) as ObjectsResponse;
}

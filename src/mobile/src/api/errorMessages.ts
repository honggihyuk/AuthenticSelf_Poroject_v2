/**
 * Mapping from backend `errorCode` strings (see backend UploadErrorCode /
 * spec FR-6) to Korean user-facing messages.
 *
 * The RN `UploadScreen` imports this module and surfaces the matched
 * message when a 4xx/5xx comes back; the "다시 시도" (retry) button
 * returns the user to the picker.
 *
 * AC-19: every error code in FR-6 MUST be a key here with a non-empty
 * Korean value. Keep this table in lock-step with the backend enum.
 */
export type UploadErrorCode =
  | 'EMPTY_FILE'
  | 'MISSING_USER_HEADER'
  | 'UNKNOWN_USER'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'FILE_TOO_LARGE'
  | 'IMAGE_UNREADABLE'
  | 'RESOLUTION_TOO_LOW'
  | 'STORAGE_PERSIST_FAILED';

export const errorMessages: Record<UploadErrorCode, string> = {
  EMPTY_FILE:
    '업로드된 파일이 비어 있습니다. 다시 시도해주세요.',
  MISSING_USER_HEADER:
    '사용자 인증 정보가 없습니다. 앱을 다시 시작해주세요.',
  UNKNOWN_USER:
    '등록되지 않은 사용자입니다. 관리자에게 문의해주세요.',
  UNSUPPORTED_MEDIA_TYPE:
    '지원하지 않는 이미지 형식입니다. JPG, PNG, WEBP만 업로드할 수 있습니다.',
  FILE_TOO_LARGE:
    '파일 용량이 너무 큽니다. 10MB 이하의 사진을 업로드해주세요.',
  IMAGE_UNREADABLE:
    '이미지를 읽을 수 없습니다. 다른 사진으로 다시 시도해주세요.',
  RESOLUTION_TOO_LOW:
    '업로드한 사진의 해상도가 너무 낮습니다. 640x480 이상 이미지를 올려주세요.',
  STORAGE_PERSIST_FAILED:
    '일시적인 오류로 사진 저장에 실패했습니다. 잠시 후 다시 시도해주세요.',
};

/** Fallback when the backend returns an unexpected (or missing) code. */
export const DEFAULT_ERROR_MESSAGE =
  '알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';

/** Safe lookup helper used by UI components. */
export function messageForCode(code: string | undefined | null): string {
  if (!code) return DEFAULT_ERROR_MESSAGE;
  return (errorMessages as Record<string, string>)[code] ?? DEFAULT_ERROR_MESSAGE;
}

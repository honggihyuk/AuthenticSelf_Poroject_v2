import { ApiError, type UploadErrorBody } from './client';

export type Role = 'USER' | 'ADMIN';

export type LoginResponse = {
  token:  string;
  userId: string;
  role:   Role;
  name:   string;
};

export type LoginArgs = {
  baseUrl:  string;
  username: string;
  password: string;
};

/**
 * POST /api/v1/auth/login. Throws {@link ApiError} on non-2xx so screens
 * can branch on `body.errorCode` (INVALID_CREDENTIALS / MISSING_FIELDS).
 */
export async function login(args: LoginArgs): Promise<LoginResponse> {
  const res = await fetch(`${args.baseUrl}/api/v1/auth/login`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username: args.username, password: args.password }),
  });
  const text = await res.text();
  let parsed: unknown = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = null;
  }
  if (res.ok) return parsed as LoginResponse;
  throw new ApiError(res.status, parsed as UploadErrorBody | null);
}

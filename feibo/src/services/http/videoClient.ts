import API_CONFIG from '@/config/api';
import { authenticatedRawRequest } from '@/services/http/authenticatedRawRequest';
import { AppRequestError } from '@/services/http/errors';
import { parseApiJson } from '@/services/http/parsers';

export interface VideoApiResponse<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

interface VideoRequestConfig {
  idempotent?: boolean;
  idempotencyKey?: string;
}

export async function videoRequest<T>(
  url: string,
  options: RequestInit = {},
  config: VideoRequestConfig = {},
): Promise<VideoApiResponse<T>> {
  const headers = new Headers(options.headers);

  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await authenticatedRawRequest(`${API_CONFIG.API_BASE_URL}${url}`, {
    ...options,
    headers,
  }, {
    idempotent: config.idempotent,
    idempotencyKey: config.idempotencyKey,
  });
  const responseText = await response.text();
  const data = responseText
    ? parseApiJson<VideoApiResponse<T>>(responseText)
    : (undefined as VideoApiResponse<T>);

  if (!response.ok) {
    throw new AppRequestError(`HTTP error! status: ${response.status}`, {
      status: response.status,
    });
  }

  if (data.code !== 200) {
    throw new AppRequestError(data.msg || '请求失败', {
      status: response.status,
      code: data.code,
    });
  }

  return data;
}

export async function videoBlobRequest(url: string, options: RequestInit = {}) {
  const response = await authenticatedRawRequest(`${API_CONFIG.API_BASE_URL}${url}`, {
    ...options,
  });

  if (!response.ok) {
    throw new AppRequestError(`HTTP error! status: ${response.status}`, {
      status: response.status,
    });
  }

  return response.blob();
}

export async function videoRawRequest(url: string, options: RequestInit = {}) {
  return authenticatedRawRequest(`${API_CONFIG.API_BASE_URL}${url}`, {
    ...options,
  });
}

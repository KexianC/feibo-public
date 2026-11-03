import { API_CONFIG } from '@/config/api';
import { authenticatedRawRequest } from '@/services/http/authenticatedRawRequest';

export const TTS_MAX_SEGMENT_LENGTH = 500;
const TTS_REQUEST_TIMEOUT_MS = 120_000;
const TTS_MAX_AUDIO_BYTES = 16 * 1024 * 1024;

export interface SynthesizeTtsSegmentOptions {
  signal?: AbortSignal;
  sessionId?: string;
  requestId?: string;
}

export const createTtsSessionId = (): string => crypto.randomUUID();

const validateRequestIdentity = (value: string | undefined): void => {
  if (value !== undefined && !/^[A-Za-z0-9_-]{1,128}$/.test(value)) {
    throw new Error('语音请求标识无效');
  }
};

/** Cancellation owns its short timeout and must survive aborting the synthesis fetch. */
export const cancelTtsSession = async (sessionId: string): Promise<void> => {
  validateRequestIdentity(sessionId);
  const response = await authenticatedRawRequest(
    `${API_CONFIG.BASE_URL}/api/v1/ai/tts/cancel`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId }),
      cache: 'no-store',
      keepalive: true,
      timeoutMs: 5_000,
    },
    { idempotent: true, idempotencyKey: `${sessionId}-cancel` },
  );
  if (!response.ok) throw new Error('语音停止请求未确认');
};

interface TtsErrorPayload {
  info?: unknown;
  message?: unknown;
}

const readTtsErrorMessage = async (response: Response): Promise<string> => {
  const contentType = response.headers.get('content-type')?.toLowerCase() || '';
  if (contentType.includes('json')) {
    try {
      const payload = await response.clone().json() as TtsErrorPayload;
      if (typeof payload.info === 'string' && payload.info.trim()) {
        return payload.info.trim();
      }
      if (typeof payload.message === 'string' && payload.message.trim()) {
        return payload.message.trim();
      }
    } catch {
      // 无法解析错误体时使用不包含敏感正文的通用提示。
    }
  }

  return `语音合成请求失败 (${response.status})`;
};

const throwIfAborted = (signal: AbortSignal): void => {
  if (!signal.aborted) return;
  if (signal.reason instanceof Error) throw signal.reason;
  throw new DOMException('语音合成已取消', 'AbortError');
};

const readAudioBody = async (response: Response, signal: AbortSignal): Promise<Blob> => {
  const declaredLength = Number(response.headers.get('content-length'));
  if (Number.isFinite(declaredLength) && declaredLength > TTS_MAX_AUDIO_BYTES) {
    throw new Error('语音合成接口返回的音频过大');
  }

  if (!response.body) {
    throwIfAborted(signal);
    const blob = await response.blob();
    throwIfAborted(signal);
    return blob;
  }

  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  const cancelReader = () => {
    void reader.cancel(signal.reason).catch(() => undefined);
  };
  signal.addEventListener('abort', cancelReader, { once: true });

  try {
    while (true) {
      throwIfAborted(signal);
      const { done, value } = await reader.read();
      throwIfAborted(signal);
      if (done) break;
      if (!value) continue;

      totalBytes += value.byteLength;
      if (totalBytes > TTS_MAX_AUDIO_BYTES) {
        await reader.cancel('audio-too-large').catch(() => undefined);
        throw new Error('语音合成接口返回的音频过大');
      }
      chunks.push(value);
    }
  } finally {
    signal.removeEventListener('abort', cancelReader);
  }

  return new Blob(chunks, {
    type: response.headers.get('content-type') || 'audio/mpeg',
  });
};

/**
 * 经登录态后端代理合成单段语音。阿里云凭据只保留在服务端。
 */
export const synthesizeTtsSegment = async (
  text: string,
  options: SynthesizeTtsSegmentOptions = {},
): Promise<Blob> => {
  const normalizedText = text.trim();
  if (!normalizedText) {
    throw new Error('语音合成文本不能为空');
  }
  if (normalizedText.length > TTS_MAX_SEGMENT_LENGTH) {
    throw new RangeError(`单段语音合成文本不能超过 ${TTS_MAX_SEGMENT_LENGTH} 字`);
  }
  validateRequestIdentity(options.sessionId);
  validateRequestIdentity(options.requestId);

  const lifecycleController = new AbortController();
  const forwardAbort = () => lifecycleController.abort(options.signal?.reason);
  if (options.signal?.aborted) {
    forwardAbort();
  } else {
    options.signal?.addEventListener('abort', forwardAbort, { once: true });
  }
  const timeoutId = window.setTimeout(() => {
    lifecycleController.abort(new Error('语音合成请求超时'));
  }, TTS_REQUEST_TIMEOUT_MS);

  try {
    const response = await authenticatedRawRequest(
      `${API_CONFIG.BASE_URL}/api/v1/ai/tts`,
      {
        method: 'POST',
        headers: {
          Accept: 'audio/mpeg, audio/*, application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          text: normalizedText,
          sessionId: options.sessionId,
          requestId: options.requestId,
        }),
        cache: 'no-store',
        signal: lifecycleController.signal,
        timeoutMs: TTS_REQUEST_TIMEOUT_MS,
      },
      {
        idempotent: true,
        idempotencyKey: options.requestId,
      },
    );

    if (!response.ok) {
      throw new Error(await readTtsErrorMessage(response));
    }

    const contentType = response.headers.get('content-type')?.toLowerCase() || '';
    if (contentType && !contentType.startsWith('audio/') && !contentType.includes('octet-stream')) {
      throw new Error('语音合成接口未返回音频');
    }

    const audioBlob = await readAudioBody(response, lifecycleController.signal);
    if (audioBlob.size === 0) {
      throw new Error('语音合成接口返回了空音频');
    }
    if (audioBlob.size > TTS_MAX_AUDIO_BYTES) {
      throw new Error('语音合成接口返回的音频过大');
    }

    return audioBlob;
  } catch (error) {
    if (lifecycleController.signal.aborted) {
      throwIfAborted(lifecycleController.signal);
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
    options.signal?.removeEventListener('abort', forwardAbort);
  }
};

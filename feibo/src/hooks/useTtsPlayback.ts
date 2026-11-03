import { useCallback, useEffect, useRef, useState } from 'react';
import { cancelTtsSession, createTtsSessionId, synthesizeTtsSegment, TTS_MAX_SEGMENT_LENGTH } from '@/services/ttsApi';
import { StreamingTtsChunker } from '@/services/streamingTts';
import { StreamingTtsMarkdown } from '@/services/streamingTtsMarkdown';
import { TtsPrefetchQueue, type TtsPrefetchTask } from '@/services/ttsPrefetchQueue';

export type TtsPlaybackStatus = 'idle' | 'waiting-text' | 'synthesizing' | 'playing' | 'paused' | 'error';

export interface TtsPlaybackState {
  status: TtsPlaybackStatus;
  currentSegment: number;
  totalSegments: number;
  error: string | null;
}

const INITIAL_PLAYBACK_STATE: TtsPlaybackState = {
  status: 'idle',
  currentSegment: 0,
  totalSegments: 0,
  error: null,
};

// 极短静音 WAV，仅在用户点击开关时激活同一个 HTMLAudioElement，
// 以兼容需要瞬时用户手势授权有声播放的浏览器。
const SILENT_AUDIO_DATA_URL = 'data:audio/wav;base64,UklGRigAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQQAAACAgICA';
const AUDIO_PLAYER_POOL_SIZE = 2;
const TEXT_FLUSH_DELAY_MS = 1_200;

/** In-memory timing/counts only. Never includes answer text, audio, or user identity. */
export interface TtsPlaybackMetrics {
  firstAudioMs: number | null;
  lastGapMs: number | null;
  maxGapMs: number;
  gapP95Ms: number;
  completedSegments: number;
  synthesisRequests: number;
  errors: number;
  retries: number;
  cancelledRequests: number;
  discardedPrefetchSegments: number;
  cancellationFailures: number;
}

const newPlaybackMetrics = (): TtsPlaybackMetrics => ({
  firstAudioMs: null,
  lastGapMs: null,
  maxGapMs: 0,
  gapP95Ms: 0,
  completedSegments: 0,
  synthesisRequests: 0,
  errors: 0,
  retries: 0,
  cancelledRequests: 0,
  discardedPrefetchSegments: 0,
  cancellationFailures: 0,
});

const endsWithNaturalPause = (value: string) => /[。！？!?；;：:，,、]$/.test(value);

/**
 * 将模型回复转换为适合朗读的纯文本，同时明确排除思考过程和 Markdown 标记。
 */
const normalizeSpeechText = (source: string): string => {
  if (!source) return '';

  const lines = source
    .split(/\r?\n/)
    .map(line => line.replace(/\s+/g, ' ').trim())
    .filter(line => line && !/^[，|\s:-]*-{3,}[，|\s:-]*$/.test(line));

  return lines.reduce((result, line) => {
    if (!result) return line;
    return `${result}${endsWithNaturalPause(result) ? ' ' : '。'}${line}`;
  }, '').replace(/\s+([，。！？；：])/g, '$1').trim();
};

export const toTtsPlainText = (source: string): string => {
  const cleaner = new StreamingTtsMarkdown();
  return normalizeSpeechText(cleaner.push(source) + cleaner.finish());
};

const findSafeSliceEnd = (text: string, maximumLength: number): number => {
  let end = Math.min(maximumLength, text.length);
  if (
    end < text.length
    && end > 0
    && /[\uD800-\uDBFF]/.test(text.charAt(end - 1))
    && /[\uDC00-\uDFFF]/.test(text.charAt(end))
  ) {
    end -= 1;
  }
  return end;
};

const findNaturalSplit = (text: string, maximumLength: number): number => {
  const safeEnd = findSafeSliceEnd(text, maximumLength);
  const minimumNaturalSplit = Math.floor(maximumLength * 0.5);
  const strongPauses = new Set(['。', '！', '？', '!', '?', '；', ';']);
  const softPauses = new Set(['，', ',', '、', '：', ':', ' ']);

  for (let index = safeEnd - 1; index >= minimumNaturalSplit; index -= 1) {
    if (strongPauses.has(text.charAt(index))) return index + 1;
  }
  for (let index = safeEnd - 1; index >= minimumNaturalSplit; index -= 1) {
    if (softPauses.has(text.charAt(index))) return index + 1;
  }

  return safeEnd;
};

/**
 * 按自然停顿拆分长回复，并保证每次后端请求不超过接口单段上限。
 */
const splitPlainTtsText = (
  source: string,
  maximumLength = TTS_MAX_SEGMENT_LENGTH,
): string[] => {
  if (!Number.isInteger(maximumLength) || maximumLength <= 0) {
    throw new RangeError('语音分段长度必须是正整数');
  }

  let remaining = normalizeSpeechText(source);
  const segments: string[] = [];

  while (remaining.length > maximumLength) {
    const splitAt = findNaturalSplit(remaining, maximumLength);
    const segment = remaining.slice(0, splitAt).trim();
    if (segment) segments.push(segment);
    remaining = remaining.slice(splitAt).trim();
  }

  if (remaining) segments.push(remaining);
  return segments;
};

export const splitTtsText = (source: string, maximumLength = TTS_MAX_SEGMENT_LENGTH): string[] => (
  splitPlainTtsText(toTtsPlainText(source), maximumLength)
);

const createAbortError = () => new DOMException('语音播报已取消', 'AbortError');

const isAbortError = (error: unknown): boolean => (
  error instanceof DOMException && error.name === 'AbortError'
);

interface TtsStreamContext {
  id: number;
  sessionId: string;
  markdown: StreamingTtsMarkdown;
  chunker: StreamingTtsChunker;
  pipeline: TtsPrefetchQueue<string, PreparedTtsAudio>;
  controller: AbortController;
  draining: boolean;
  finished: boolean;
  paused: boolean;
  failed: boolean;
  startedSegments: number;
  totalSegments: number;
  currentTask: TtsPrefetchTask<string, PreparedTtsAudio> | null;
  activePlayback: { prepared: PreparedTtsAudio; resume: () => void } | null;
  wakeAfterPause: (() => void) | null;
  flushTimer: number | null;
  startedAt: number;
  pausedAt: number | null;
  lastEndedAt: number | null;
  metrics: TtsPlaybackMetrics;
  inFlightRequests: Set<string>;
  recentGapsMs: number[];
}

interface PreparedTtsAudio {
  audio: HTMLAudioElement;
  objectUrl: string;
}

export const useTtsPlayback = () => {
  const [playbackState, setPlaybackState] = useState<TtsPlaybackState>(INITIAL_PLAYBACK_STATE);
  const [metrics, setMetrics] = useState<TtsPlaybackMetrics>(newPlaybackMetrics());
  const [playbackRate, setPlaybackRateState] = useState(1);
  const playbackRateRef = useRef(1);
  const audioPoolRef = useRef<HTMLAudioElement[]>([]);
  const reservedAudioRef = useRef<Set<HTMLAudioElement>>(new Set());
  const objectUrlsRef = useRef<Map<HTMLAudioElement, string>>(new Map());
  const abortControllerRef = useRef<AbortController | null>(null);
  const streamContextRef = useRef<TtsStreamContext | null>(null);
  const runIdRef = useRef(0);
  const mountedRef = useRef(true);

  const isCurrentContext = useCallback((context: TtsStreamContext) => (
    !context.controller.signal.aborted
    && runIdRef.current === context.id
    && streamContextRef.current === context
  ), []);

  const updatePlayback = useCallback((
    context: TtsStreamContext,
    status: TtsPlaybackStatus,
    error: string | null = null,
  ) => {
    if (!mountedRef.current || !isCurrentContext(context)) return;
    setPlaybackState({
      status,
      currentSegment: context.startedSegments,
      totalSegments: context.totalSegments,
      error,
    });
  }, [isCurrentContext]);

  const publishMetrics = useCallback((context: TtsStreamContext) => {
    if (mountedRef.current && isCurrentContext(context)) setMetrics({ ...context.metrics });
  }, [isCurrentContext]);

  const ensureAudioPool = useCallback((): HTMLAudioElement[] => {
    while (audioPoolRef.current.length < AUDIO_PLAYER_POOL_SIZE) {
      const audio = new Audio();
      audio.preload = 'auto';
      audioPoolRef.current.push(audio);
    }
    return audioPoolRef.current;
  }, []);

  const resetAudioElement = useCallback((audio: HTMLAudioElement) => {
    audio.onended = null;
    audio.onerror = null;
    audio.pause();
    audio.removeAttribute('src');
    try {
      audio.load();
    } catch {
      // 某些浏览器在音频尚未加载时不允许重置，忽略即可。
    }
  }, []);

  const releasePreparedAudio = useCallback((prepared: PreparedTtsAudio) => {
    const currentObjectUrl = objectUrlsRef.current.get(prepared.audio);
    if (currentObjectUrl !== prepared.objectUrl) return;

    resetAudioElement(prepared.audio);
    URL.revokeObjectURL(prepared.objectUrl);
    objectUrlsRef.current.delete(prepared.audio);
    reservedAudioRef.current.delete(prepared.audio);
  }, [resetAudioElement]);

  const releaseAudio = useCallback(() => {
    for (const audio of audioPoolRef.current) {
      const objectUrl = objectUrlsRef.current.get(audio);
      resetAudioElement(audio);
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    }
    objectUrlsRef.current.clear();
    reservedAudioRef.current.clear();
  }, [resetAudioElement]);

  const stop = useCallback(() => {
    runIdRef.current += 1;
    const stopRunId = runIdRef.current;
    const context = streamContextRef.current;
    if (context) {
      if (context.flushTimer !== null) window.clearTimeout(context.flushTimer);
      context.flushTimer = null;
      context.metrics.cancelledRequests += context.inFlightRequests.size;
      context.metrics.discardedPrefetchSegments += Math.max(
        0, objectUrlsRef.current.size - (context.activePlayback ? 1 : 0),
      );
      if (mountedRef.current) setMetrics({ ...context.metrics });
      // This separate request is deliberately not linked to the synthesis abort signal.
      void cancelTtsSession(context.sessionId).catch(() => {
        context.metrics.cancellationFailures += 1;
        if (mountedRef.current && runIdRef.current === stopRunId) {
          setMetrics({ ...context.metrics });
        }
      });
      context.wakeAfterPause?.();
      context.wakeAfterPause = null;
    }
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    streamContextRef.current = null;
    releaseAudio();
    if (mountedRef.current) {
      setPlaybackState(INITIAL_PLAYBACK_STATE);
    }
  }, [releaseAudio]);

  const setPlaybackRate = useCallback((rate: number) => {
    if (!Number.isFinite(rate)) return;
    const normalizedRate = Math.min(1.5, Math.max(0.75, rate));
    playbackRateRef.current = normalizedRate;
    for (const audio of audioPoolRef.current) audio.playbackRate = normalizedRate;
    if (mountedRef.current) setPlaybackRateState(normalizedRate);
  }, []);

  const unlock = useCallback(() => {
    const unlockRunId = runIdRef.current;
    for (const audio of ensureAudioPool()) {
      if (reservedAudioRef.current.has(audio)) continue;
      audio.src = SILENT_AUDIO_DATA_URL;

      void audio.play().then(() => {
        if (
          runIdRef.current === unlockRunId
          && !reservedAudioRef.current.has(audio)
          && !objectUrlsRef.current.has(audio)
        ) {
          resetAudioElement(audio);
        }
      }).catch(() => {
        if (
          runIdRef.current === unlockRunId
          && !reservedAudioRef.current.has(audio)
          && !objectUrlsRef.current.has(audio)
          && mountedRef.current
        ) {
          setPlaybackState({
            status: 'error',
            currentSegment: 0,
            totalSegments: 0,
            error: '浏览器阻止了自动播放，请再次点击语音对话开关',
          });
        }
      });
    }
  }, [ensureAudioPool, resetAudioElement]);

  const prepareAudioBlob = useCallback((
    blob: Blob,
    signal: AbortSignal,
  ): PreparedTtsAudio => {
    if (signal.aborted) throw createAbortError();

    const audio = ensureAudioPool().find(candidate => (
      !reservedAudioRef.current.has(candidate)
    ));
    if (!audio) throw new Error('语音播放器预加载队列繁忙');

    const objectUrl = URL.createObjectURL(blob);
    const prepared = { audio, objectUrl };
    reservedAudioRef.current.add(audio);
    objectUrlsRef.current.set(audio, objectUrl);
    audio.preload = 'auto';

    try {
      audio.src = objectUrl;
      audio.load();
      // 后台预加载只是优化，不是播放前提。部分移动浏览器直到 play()
      // 才开始加载，因此轮到这一句时必须直接允许播放，不能等待 canplay。
      return prepared;
    } catch (error) {
      releasePreparedAudio(prepared);
      throw error;
    }
  }, [ensureAudioPool, releasePreparedAudio]);

  const playPreparedAudio = useCallback((
    prepared: PreparedTtsAudio,
    context: TtsStreamContext,
  ): Promise<void> => (
    new Promise((resolve, reject) => {
      const signal = context.controller.signal;
      if (signal.aborted) {
        releasePreparedAudio(prepared);
        reject(createAbortError());
        return;
      }
      if (objectUrlsRef.current.get(prepared.audio) !== prepared.objectUrl) {
        reject(new Error('预加载的语音音频已失效'));
        return;
      }
      if (prepared.audio.error) {
        releasePreparedAudio(prepared);
        reject(new Error('语音音频预加载失败'));
        return;
      }

      let settled = false;
      let started = false;
      const settle = (error?: unknown) => {
        if (settled) return;
        settled = true;
        signal.removeEventListener('abort', handleAbort);
        prepared.audio.removeEventListener('playing', handlePlaying);
        prepared.audio.removeEventListener('waiting', handleWaiting);
        prepared.audio.onended = null;
        prepared.audio.onerror = null;
        if (context.activePlayback?.prepared === prepared) context.activePlayback = null;
        releasePreparedAudio(prepared);
        if (error) reject(error);
        else resolve();
      };

      const handleAbort = () => settle(createAbortError());
      const handlePlaying = () => {
        if (settled || context.paused || !isCurrentContext(context)) return;
        if (!started) {
          started = true;
          const now = Date.now();
          if (context.metrics.firstAudioMs === null) {
            context.metrics.firstAudioMs = Math.max(0, now - context.startedAt);
          }
          if (context.lastEndedAt !== null) {
            const gap = Math.max(0, now - context.lastEndedAt);
            context.metrics.lastGapMs = gap;
            context.metrics.maxGapMs = Math.max(context.metrics.maxGapMs, gap);
            context.recentGapsMs.push(gap);
            if (context.recentGapsMs.length > 100) context.recentGapsMs.shift();
            const sortedGaps = [...context.recentGapsMs].sort((left, right) => left - right);
            context.metrics.gapP95Ms = sortedGaps[Math.ceil(sortedGaps.length * 0.95) - 1];
          }
          publishMetrics(context);
        }
        updatePlayback(context, 'playing');
      };
      const handleWaiting = () => {
        if (!context.paused) updatePlayback(context, 'synthesizing');
      };
      const begin = () => {
        if (settled || context.paused || !isCurrentContext(context)) return;
        prepared.audio.playbackRate = playbackRateRef.current;
        try {
          void prepared.audio.play().then(handlePlaying).catch(error => {
            // pause() may reject a still-pending play() promise. Resume reuses this
            // same audio and position; a user pause must not discard the sentence.
            if (context.paused && isAbortError(error) && !signal.aborted) return;
            settle(error);
          });
        } catch (error) {
          settle(error);
        }
      };
      signal.addEventListener('abort', handleAbort, { once: true });
      prepared.audio.addEventListener('playing', handlePlaying);
      prepared.audio.addEventListener('waiting', handleWaiting);
      prepared.audio.onended = () => settle();
      prepared.audio.onerror = () => settle(new Error('语音音频播放失败'));
      context.activePlayback = { prepared, resume: begin };
      begin();
    })
  ), [isCurrentContext, publishMetrics, releasePreparedAudio, updatePlayback]);

  const drainStream = useCallback((context: TtsStreamContext): void => {
    if (context.draining || context.paused || context.failed || !isCurrentContext(context)) return;
    context.draining = true;

    void (async () => {
      try {
        while (isCurrentContext(context)) {
          if (context.paused) {
            await new Promise<void>(resolve => { context.wakeAfterPause = resolve; });
            context.wakeAfterPause = null;
            continue;
          }
          const task = context.currentTask ?? context.pipeline.take();
          if (!task) {
            if (context.finished) {
              streamContextRef.current = null;
              if (abortControllerRef.current === context.controller) {
                abortControllerRef.current = null;
              }
              if (mountedRef.current) setPlaybackState(INITIAL_PLAYBACK_STATE);
            } else {
              updatePlayback(context, 'waiting-text');
            }
            return;
          }

          context.currentTask = task;
          context.startedSegments = task.index;
          updatePlayback(context, 'synthesizing');

          const synthesisResult = await task.result;
          if (!isCurrentContext(context)) throw createAbortError();
          if (synthesisResult.ok === false) throw synthesisResult.error;
          if (context.paused) {
            await new Promise<void>(resolve => { context.wakeAfterPause = resolve; });
            context.wakeAfterPause = null;
          }
          if (!isCurrentContext(context)) throw createAbortError();
          await playPreparedAudio(synthesisResult.value, context);
          context.currentTask = null;
          context.lastEndedAt = Date.now();
          context.metrics.completedSegments += 1;
          publishMetrics(context);
        }
      } catch (error) {
        if (
          context.controller.signal.aborted
          || runIdRef.current !== context.id
          || streamContextRef.current !== context
        ) {
          return;
        }

        // Keep the failed logical sentence and the following queue. Only an
        // explicit user retry may issue another request (with the same ID).
        context.failed = true;
        context.metrics.errors += 1;
        publishMetrics(context);
        updatePlayback(context, 'error', error instanceof Error ? error.message : '语音播报失败，请点击重试');
      } finally {
        context.draining = false;
      }
    })();
  }, [isCurrentContext, playPreparedAudio, publishMetrics, updatePlayback]);

  const startStream = useCallback((): number => {
    stop();
    const id = runIdRef.current + 1;
    runIdRef.current = id;
    const controller = new AbortController();
    const sessionId = createTtsSessionId();
    const pipeline = new TtsPrefetchQueue<string, PreparedTtsAudio>(async (segment, index) => {
      if (controller.signal.aborted) throw createAbortError();
      const requestId = `${sessionId}-${index}`;
      context.inFlightRequests.add(requestId);
      context.metrics.synthesisRequests += 1;
      publishMetrics(context);
      try {
        const audioBlob = await synthesizeTtsSegment(segment, {
          signal: controller.signal, sessionId, requestId,
        });
        return prepareAudioBlob(audioBlob, controller.signal);
      } finally {
        context.inFlightRequests.delete(requestId);
      }
    });
    const context: TtsStreamContext = {
      id,
      sessionId,
      markdown: new StreamingTtsMarkdown(),
      chunker: new StreamingTtsChunker(),
      pipeline,
      controller,
      draining: false,
      finished: false,
      paused: false,
      failed: false,
      startedSegments: 0,
      totalSegments: 0,
      currentTask: null,
      activePlayback: null,
      wakeAfterPause: null,
      flushTimer: null,
      startedAt: Date.now(),
      pausedAt: null,
      lastEndedAt: null,
      metrics: newPlaybackMetrics(),
      inFlightRequests: new Set(),
      recentGapsMs: [],
    };
    streamContextRef.current = context;
    abortControllerRef.current = controller;
    updatePlayback(context, 'waiting-text');
    publishMetrics(context);
    return id;
  }, [prepareAudioBlob, publishMetrics, stop, updatePlayback]);

  const addSegments = useCallback((context: TtsStreamContext, rawSegments: string[]) => {
    const segments = rawSegments.flatMap(segment => splitPlainTtsText(segment));
    if (segments.length === 0) return;
    context.pipeline.enqueueMany(segments);
    context.totalSegments += segments.length;
    if (mountedRef.current) {
      setPlaybackState(previous => (
        previous.status === 'idle'
          ? previous
          : { ...previous, totalSegments: context.totalSegments }
      ));
    }
    drainStream(context);
  }, [drainStream]);

  const scheduleTextFlush = useCallback((context: TtsStreamContext) => {
    if (context.flushTimer !== null || context.finished || !context.chunker.hasPendingText) return;
    context.flushTimer = window.setTimeout(() => {
      context.flushTimer = null;
      if (!isCurrentContext(context) || context.finished) return;
      addSegments(context, context.chunker.flushPending());
    }, TEXT_FLUSH_DELAY_MS);
  }, [addSegments, isCurrentContext]);

  const enqueueStream = useCallback((streamId: number, delta: string): void => {
    const context = streamContextRef.current;
    if (!context || context.id !== streamId || context.finished || context.controller.signal.aborted) {
      return;
    }
    const speakable = context.markdown.push(delta);
    addSegments(context, context.chunker.push(speakable));
    scheduleTextFlush(context);
  }, [addSegments, scheduleTextFlush]);

  const finishStream = useCallback((streamId: number): void => {
    const context = streamContextRef.current;
    if (!context || context.id !== streamId || context.finished || context.controller.signal.aborted) {
      return;
    }
    context.finished = true;
    if (context.flushTimer !== null) window.clearTimeout(context.flushTimer);
    context.flushTimer = null;
    addSegments(context, context.chunker.push(context.markdown.finish()));
    addSegments(context, context.chunker.finish());
    drainStream(context);
  }, [addSegments, drainStream]);

  const pause = useCallback(() => {
    const context = streamContextRef.current;
    if (!context || context.paused || context.failed || !isCurrentContext(context)) return;
    context.paused = true;
    context.pausedAt = Date.now();
    context.activePlayback?.prepared.audio.pause();
    updatePlayback(context, 'paused');
  }, [isCurrentContext, updatePlayback]);

  const retry = useCallback(() => {
    const context = streamContextRef.current;
    if (!context || !context.failed || !context.currentTask || !isCurrentContext(context)) return;
    context.failed = false;
    context.paused = false;
    context.pausedAt = null;
    unlock();
    context.metrics.retries += 1;
    context.currentTask = context.pipeline.retry(context.currentTask);
    publishMetrics(context);
    updatePlayback(context, 'synthesizing');
    drainStream(context);
  }, [drainStream, isCurrentContext, publishMetrics, unlock, updatePlayback]);

  const resume = useCallback(() => {
    const context = streamContextRef.current;
    if (!context || !isCurrentContext(context)) return;
    if (context.failed) { retry(); return; }
    if (!context.paused) return;
    const pausedDuration = context.pausedAt === null ? 0 : Math.max(0, Date.now() - context.pausedAt);
    context.startedAt += pausedDuration;
    if (context.lastEndedAt !== null) context.lastEndedAt += pausedDuration;
    context.pausedAt = null;
    context.paused = false;
    updatePlayback(context, context.currentTask ? 'synthesizing' : 'waiting-text');
    context.wakeAfterPause?.();
    context.wakeAfterPause = null;
    context.activePlayback?.resume();
    drainStream(context);
  }, [drainStream, isCurrentContext, retry, updatePlayback]);

  const speak = useCallback((source: string): void => {
    const streamId = startStream();
    unlock();
    enqueueStream(streamId, source);
    finishStream(streamId);
  }, [enqueueStream, finishStream, startStream, unlock]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      stop();
      audioPoolRef.current = [];
    };
  }, [stop]);

  return {
    ...playbackState,
    speak,
    startStream,
    enqueueStream,
    finishStream,
    stop,
    unlock,
    pause,
    resume,
    retry,
    playbackRate,
    setPlaybackRate,
    canPause: playbackState.status === 'playing' || playbackState.status === 'synthesizing' || playbackState.status === 'waiting-text',
    canResume: playbackState.status === 'paused' || playbackState.status === 'error',
    canRetry: playbackState.status === 'error',
    metrics,
  };
};

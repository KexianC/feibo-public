import { useCallback, useEffect, useRef, useState } from 'react';
import { useWebSocket } from './useWebSocket';
import { WebSocketState, type WebSocketMessage } from '../services/websocketService';
import { hasVoiceActivity, packPcm16Frames, resampleTo16kPcm } from '../services/asrAudio';

export type VoiceInputPhase = 'idle' | 'starting' | 'recording' | 'finishing';
export const VOICE_INPUT_SILENCE_TIMEOUT_MS = 30_000;
export const VOICE_INPUT_MAX_DURATION_MS = 5 * 60_000;
const FINISH_TIMEOUT_MS = 5_000;
const VOICE_ACTIVITY_RMS_THRESHOLD = 0.005;

interface Options {
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
}

interface RecordingSession {
  id: string;
  prefix: string;
  finalText: string;
  started: boolean;
  accepting: boolean;
  startedAt: number;
  lastActivityAt: number;
}

/** Each microphone run owns one server session; late messages never own a new draft. */
export function useVoiceInput(options: Options) {
  const optionsRef = useRef(options);
  optionsRef.current = options;
  const { sendMessage, sendBinary, subscribe, onStateChange, isConnected, connect } = useWebSocket();
  const [phase, setPhase] = useState<VoiceInputPhase>('idle');
  const [notice, setNotice] = useState('');
  const [remainingSeconds, setRemainingSeconds] = useState(300);
  const [connected, setConnected] = useState(isConnected());
  const sessionRef = useRef<RecordingSession | null>(null);
  const phaseRef = useRef<VoiceInputPhase>('idle');
  const mountedRef = useRef(true);
  const streamRef = useRef<MediaStream | null>(null);
  const contextRef = useRef<AudioContext | null>(null);
  const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const remainderRef = useRef<Int16Array>(new Int16Array(0));
  const captureTimerRef = useRef<number | null>(null);
  const acknowledgementTimerRef = useRef<number | null>(null);

  const updatePhase = useCallback((next: VoiceInputPhase) => {
    phaseRef.current = next;
    if (mountedRef.current) setPhase(next);
  }, []);
  const clearAcknowledgement = useCallback(() => {
    if (acknowledgementTimerRef.current !== null) window.clearTimeout(acknowledgementTimerRef.current);
    acknowledgementTimerRef.current = null;
  }, []);
  const releaseCapture = useCallback(() => {
    if (captureTimerRef.current !== null) window.clearInterval(captureTimerRef.current);
    captureTimerRef.current = null;
    if (processorRef.current) {
      processorRef.current.onaudioprocess = null;
      processorRef.current.disconnect();
    }
    sourceRef.current?.disconnect();
    streamRef.current?.getTracks().forEach(track => { track.onended = null; track.stop(); });
    const context = contextRef.current;
    if (context && context.state !== 'closed') void context.close().catch(() => undefined);
    processorRef.current = null;
    sourceRef.current = null;
    streamRef.current = null;
    contextRef.current = null;
    remainderRef.current = new Int16Array(0);
  }, []);

  const cancel = useCallback((message = '') => {
    const session = sessionRef.current;
    sessionRef.current = null;
    if (session) {
      session.accepting = false;
      sendMessage({ type: 'cancel_asr', sessionId: session.id });
    }
    clearAcknowledgement();
    releaseCapture();
    updatePhase('idle');
    if (mountedRef.current && message) setNotice(message);
  }, [clearAcknowledgement, releaseCapture, sendMessage, updatePhase]);

  const armAcknowledgement = useCallback((session: RecordingSession, delay: number, message: string) => {
    clearAcknowledgement();
    acknowledgementTimerRef.current = window.setTimeout(() => {
      if (sessionRef.current === session) cancel(message);
    }, delay);
  }, [cancel, clearAcknowledgement]);

  const sendPcm = useCallback((pcm: Int16Array, flush = false) => {
    const packed = packPcm16Frames(remainderRef.current, pcm, flush);
    for (const frame of packed.frames) if (!sendBinary(frame)) return false;
    remainderRef.current = packed.remainder;
    return true;
  }, [sendBinary]);

  const stop = useCallback((discardPendingResults = false) => {
    const session = sessionRef.current;
    if (!session) return;
    if (discardPendingResults || !session.started) {
      cancel();
      return;
    }
    if (phaseRef.current === 'finishing') return;
    // Prevent a queued audio callback from sending more frames after stop.
    updatePhase('finishing');
    const flushed = sendPcm(new Int16Array(0), true);
    releaseCapture();
    if (!flushed || !sendMessage({ type: 'stop_asr', sessionId: session.id })) {
      cancel('语音连接已断开，已保留当前文字，请重新录音。');
      return;
    }
    armAcknowledgement(session, FINISH_TIMEOUT_MS, '识别收尾超时，已保留当前文字，可以重新录音。');
  }, [armAcknowledgement, cancel, releaseCapture, sendMessage, sendPcm, updatePhase]);

  const stopRef = useRef(stop);
  stopRef.current = stop;

  const handleServerEvent = useCallback((msg: WebSocketMessage) => {
    const session = sessionRef.current;
    // Missing IDs are intentionally rejected by new clients. The new server
    // remains compatible with old clients, not the reverse.
    if (!session || !session.accepting || msg.sessionId !== session.id) return;
    if (msg.type === 'asr_started') {
      if (phaseRef.current !== 'starting' || session.started) return;
      clearAcknowledgement();
      session.started = true;
      session.startedAt = Date.now();
      session.lastActivityAt = session.startedAt;
      updatePhase('recording');
      captureTimerRef.current = window.setInterval(() => {
        if (sessionRef.current !== session || phaseRef.current !== 'recording') return;
        const elapsed = Date.now() - session.startedAt;
        setRemainingSeconds(Math.max(0, Math.ceil((VOICE_INPUT_MAX_DURATION_MS - elapsed) / 1000)));
        if (elapsed >= VOICE_INPUT_MAX_DURATION_MS) {
          stopRef.current(false);
          setNotice('单次语音输入已达到 5 分钟，录音已自动停止。');
        } else if (Date.now() - session.lastActivityAt >= VOICE_INPUT_SILENCE_TIMEOUT_MS) {
          stopRef.current(false);
          setNotice('连续静音 30 秒，语音输入已自动停止。');
        }
      }, 250);
      return;
    }
    if (msg.type === 'asr_partial' || msg.type === 'asr_final') {
      if (!session.started || optionsRef.current.disabled || typeof msg.content !== 'string') return;
      const text = msg.content;
      if (!text.trim()) return;
      // Recognition output is never evidence of microphone activity: silence
      // hallucinations must not renew the recording deadline.
      if (msg.type === 'asr_final') session.finalText += text;
      const recognized = session.finalText + (msg.type === 'asr_partial' ? text : '');
      const separator = session.prefix && !/\s$/.test(session.prefix) && !/^[，。！？；：,.!?;:]/.test(recognized) ? ' ' : '';
      optionsRef.current.onChange(session.prefix + separator + recognized);
      return;
    }
    if (msg.type === 'asr_stopping') {
      releaseCapture();
      updatePhase('finishing');
      armAcknowledgement(session, FINISH_TIMEOUT_MS, '识别收尾超时，已保留当前文字，可以重新录音。');
      if (msg.reason === 'silence_timeout') setNotice('连续静音 30 秒，语音输入已自动停止。');
      if (msg.reason === 'max_duration') setNotice('单次语音输入已达到 5 分钟，录音已自动停止。');
      return;
    }
    if (msg.type === 'asr_error' || msg.type === 'asr_complete') {
      sessionRef.current = null;
      session.accepting = false;
      clearAcknowledgement();
      releaseCapture();
      updatePhase('idle');
      if (msg.type === 'asr_error') setNotice('语音识别暂时不可用，已保留当前文字，请重新录音。');
      else if (msg.reason === 'finish_timeout') setNotice('尾句识别等待超时，已保留当前文字，请检查后发送。');
    }
  }, [armAcknowledgement, clearAcknowledgement, releaseCapture, updatePhase]);

  useEffect(() => {
    mountedRef.current = true;
    const unsubscribe = ['asr_started', 'asr_partial', 'asr_final', 'asr_stopping', 'asr_error', 'asr_complete']
      .map(type => subscribe(type, handleServerEvent));
    unsubscribe.push(onStateChange(state => {
      setConnected(state === WebSocketState.CONNECTED);
      if (state !== WebSocketState.CONNECTED && state !== WebSocketState.CONNECTING && sessionRef.current) {
        cancel('语音连接已断开，已保留当前文字，连接恢复后可重新录音。');
      }
    }));
    const onHidden = () => {
      if (document.hidden && sessionRef.current) {
        stopRef.current(false);
        setNotice('页面已进入后台，已停止录音并保留文字。');
      }
    };
    document.addEventListener('visibilitychange', onHidden);
    return () => {
      mountedRef.current = false;
      unsubscribe.forEach(unsub => unsub());
      document.removeEventListener('visibilitychange', onHidden);
      cancel();
    };
  }, [cancel, handleServerEvent, onStateChange, subscribe]);

  useEffect(() => {
    if (options.disabled) cancel();
  }, [options.disabled, cancel]);

  const toggle = useCallback(async () => {
    if (optionsRef.current.disabled) return;
    if (sessionRef.current) {
      stop(phaseRef.current === 'starting');
      return;
    }
    const session: RecordingSession = {
      id: crypto.randomUUID(), prefix: optionsRef.current.value, finalText: '',
      started: false, accepting: true, startedAt: 0, lastActivityAt: 0,
    };
    sessionRef.current = session;
    setNotice('');
    setRemainingSeconds(300);
    updatePhase('starting');
    armAcknowledgement(session, 30_000, '麦克风或连接等待超时，请确认权限后重新录音。');
    try {
      if (!isConnected() && !await connect()) throw new Error('connection');
      if (sessionRef.current !== session) return;
      if (!navigator.mediaDevices?.getUserMedia) throw new Error('microphone-unavailable');
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      if (sessionRef.current !== session) {
        stream.getTracks().forEach(track => track.stop());
        return;
      }
      streamRef.current = stream;
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      const context = new AudioContextClass();
      contextRef.current = context;
      if (context.state === 'suspended') await context.resume();
      if (sessionRef.current !== session) {
        stream.getTracks().forEach(track => track.stop());
        if (context.state !== 'closed') void context.close().catch(() => undefined);
        return;
      }
      const source = context.createMediaStreamSource(stream);
      const processor = context.createScriptProcessor(4096, 1, 1);
      sourceRef.current = source;
      processorRef.current = processor;
      processor.onaudioprocess = event => {
        if (sessionRef.current !== session || phaseRef.current !== 'recording' || !session.started) return;
        const input = event.inputBuffer.getChannelData(0);
        if (hasVoiceActivity(input, VOICE_ACTIVITY_RMS_THRESHOLD)) session.lastActivityAt = Date.now();
        if (!sendPcm(resampleTo16kPcm(input, context.sampleRate))) cancel('语音连接已断开，请重新录音。');
      };
      stream.getTracks().forEach(track => { track.onended = () => {
        if (sessionRef.current === session) cancel('麦克风已断开，已保留当前文字。');
      }; });
      source.connect(processor);
      processor.connect(context.destination);
      armAcknowledgement(session, 8_000, '语音服务启动超时，请重新录音。');
      if (!sendMessage({ type: 'start_asr', sessionId: session.id })) throw new Error('connection');
    } catch {
      if (sessionRef.current === session) cancel('无法启动录音，请检查麦克风权限、HTTPS 和网络后重试。');
    }
  }, [armAcknowledgement, cancel, connect, isConnected, sendMessage, sendPcm, stop, updatePhase]);

  const edit = useCallback((value: string) => {
    if (sessionRef.current) cancel('已切换为手工编辑，录音已停止。');
    optionsRef.current.onChange(value);
  }, [cancel]);

  return { phase, notice, remainingSeconds, connected, toggle, stop, edit, clearNotice: () => setNotice('') };
}

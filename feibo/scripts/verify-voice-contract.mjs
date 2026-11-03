import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDirectory, '..');

const loadStandaloneTypeScriptModule = async (relativePath) => {
  const source = await readFile(path.join(projectRoot, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: relativePath,
  }).outputText;
  const moduleUrl = `data:text/javascript;base64,${Buffer.from(output).toString('base64')}`;
  return import(moduleUrl);
};

const { StreamingTtsChunker } = await loadStandaloneTypeScriptModule(
  'src/services/streamingTts.ts',
);
const { TtsPrefetchQueue } = await loadStandaloneTypeScriptModule(
  'src/services/ttsPrefetchQueue.ts',
);
const { hasVoiceActivity, packPcm16Frames, resampleTo16kPcm } = await loadStandaloneTypeScriptModule(
  'src/services/asrAudio.ts',
);

const createDeferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

const flushPromises = async () => {
  await Promise.resolve();
  await Promise.resolve();
};

{
  const chunker = new StreamingTtsChunker();
  assert.deepEqual(chunker.push('您好，'), []);
  assert.deepEqual(
    chunker.push('请先测温。继续观察'),
    ['您好，请先测温。'],
  );
  assert.deepEqual(chunker.finish(), ['继续观察']);
  assert.deepEqual(chunker.finish(), [], 'finish 必须幂等，不能重复播报尾段');
}

{
  const chunker = new StreamingTtsChunker({
    firstSegmentMinimumLength: 1,
    minimumSegmentLength: 1,
    maximumSegmentLength: 100,
  });
  assert.deepEqual(chunker.push('体温是37.'), [], '末尾英文句点需要等待前瞻');
  assert.deepEqual(chunker.push('5℃。'), ['体温是37.5℃。'], '小数点不能被拆句');
}

{
  const chunker = new StreamingTtsChunker({
    firstSegmentMinimumLength: 5,
    minimumSegmentLength: 5,
    maximumSegmentLength: 20,
  });
  const segments = chunker.push(`${'甲'.repeat(19)}😀${'乙'.repeat(20)}`);
  assert.ok(segments.length > 0, '无标点长文本应按上限产生分段');
  assert.ok(segments.every(segment => segment.length <= 20));
  assert.ok(!/[\uD800-\uDBFF]$/.test(segments[0]), '不能截断 surrogate pair');
}

{
  const synthesis = new Map([
    ['第一句。', createDeferred()],
    ['第二句。', createDeferred()],
    ['第三句。', createDeferred()],
  ]);
  const events = [];
  const queue = new TtsPrefetchQueue(async text => {
    events.push(`synthesize:${text}:start`);
    const value = await synthesis.get(text).promise;
    events.push(`synthesize:${text}:ready`);
    return value;
  });

  queue.enqueueMany(['第一句。', '第二句。', '第三句。']);
  await flushPromises();
  assert.deepEqual(
    events,
    ['synthesize:第一句。:start'],
    '队列尚未开始播放时只能预取队首，不能一次打满 TTS 接口',
  );

  const firstTask = queue.take();
  assert.equal(firstTask.input, '第一句。');
  await flushPromises();
  assert.deepEqual(
    events,
    ['synthesize:第一句。:start', 'synthesize:第二句。:start'],
    '取出当前句后必须立即预合成下一句',
  );

  synthesis.get('第二句。').resolve('audio-2');
  await flushPromises();
  assert.equal(firstTask.state, 'synthesizing');
  assert.equal(queue.length, 2);
  assert.ok(events.includes('synthesize:第二句。:ready'));
  assert.ok(!events.some(event => event.includes('第三句。')),
    '单槽前瞻只能保留下一句，第三句不得提前合成');

  synthesis.get('第一句。').resolve('audio-1');
  const firstResult = await firstTask.result;
  assert.deepEqual(firstResult, { ok: true, value: 'audio-1' });
  events.push('play:第一句。');

  const secondTask = queue.take();
  assert.equal(secondTask.input, '第二句。', '合成完成先后不能改变播放顺序');
  const secondResult = await secondTask.result;
  assert.deepEqual(secondResult, { ok: true, value: 'audio-2' });
  events.push('play:第二句。');
  await flushPromises();
  assert.ok(
    events.indexOf('synthesize:第二句。:ready') < events.indexOf('play:第一句。'),
    '下一句应能在当前句播放结束前准备完成',
  );
  assert.ok(
    events.indexOf('play:第一句。') < events.indexOf('play:第二句。'),
    '预合成不得打乱播报顺序',
  );
  assert.ok(events.includes('synthesize:第三句。:start'),
    '消费第二句时应继续预取第三句');
}

{
  const controller = new AbortController();
  const queue = new TtsPrefetchQueue((text) => new Promise((resolve, reject) => {
    const abort = () => reject(new DOMException(`${text} 已取消`, 'AbortError'));
    if (controller.signal.aborted) abort();
    else controller.signal.addEventListener('abort', abort, { once: true });
  }));
  queue.enqueueMany(['当前句', '预取句']);
  const currentTask = queue.take();
  await flushPromises();
  controller.abort();
  const prefetchedTask = queue.take();
  const [currentResult, prefetchedResult] = await Promise.all([
    currentTask.result,
    prefetchedTask.result,
  ]);
  assert.equal(currentResult.ok, false, '停止时必须取消当前句合成');
  assert.equal(prefetchedResult.ok, false, '停止时必须取消后台预取合成');
  assert.equal(currentResult.error.name, 'AbortError');
  assert.equal(prefetchedResult.error.name, 'AbortError');
}

{
  const queue = new TtsPrefetchQueue(async () => {
    throw new Error('预取失败');
  });
  const task = queue.enqueue('失败句');
  const result = await task.result;
  assert.equal(result.ok, false, '预取失败应作为可消费结果返回，不能形成裸 Promise 拒绝');
  assert.match(result.error.message, /预取失败/);
}

{
  assert.equal(hasVoiceActivity(new Float32Array(128)), false, '全静音样本不得续期静音计时');
  assert.equal(
    hasVoiceActivity(Float32Array.from({ length: 128 }, (_, i) => i % 2 ? 0.02 : -0.02)),
    true,
    '超过 RMS 阈值的音频应视为语音活动',
  );
  assert.equal(
    hasVoiceActivity(new Float32Array(128).fill(0.005)),
    false,
    '低于 RMS 阈值的底噪不应视为语音活动',
  );
  assert.throws(
    () => hasVoiceActivity(new Float32Array(1), 1.1),
    /语音活动阈值无效/,
  );

  const source = new Float32Array(480);
  source[0] = -1;
  source[3] = 1;
  const pcm = resampleTo16kPcm(source, 48_000);
  assert.equal(pcm.length, 160, '48kHz 的 480 个样本应重采样为 160 个样本');
  assert.equal(pcm[0], -32768);
  assert.equal(pcm[1], 32767);

  const packed = packPcm16Frames(new Int16Array(100), new Int16Array(300));
  assert.equal(packed.frames.length, 1);
  assert.equal(packed.frames[0].byteLength, 640);
  assert.equal(packed.remainder.length, 80, '跨回调尾帧必须保留');

  const endian = packPcm16Frames(
    new Int16Array(0),
    new Int16Array([-32768, 32767]),
    true,
    2,
  );
  assert.deepEqual([...endian.frames[0]], [0, 128, 255, 127], 'PCM16 必须使用小端字节序');
}

const componentSource = await readFile(
  path.join(projectRoot, 'src/components/ZhibiYiyan.tsx'),
  'utf8',
);
const voiceInputSource = await readFile(path.join(projectRoot, 'src/hooks/useVoiceInput.ts'), 'utf8');
const ttsPlaybackSource = await readFile(
  path.join(projectRoot, 'src/hooks/useTtsPlayback.ts'),
  'utf8',
);
const socketSource = await readFile(
  path.join(projectRoot, 'src/services/websocketService.ts'),
  'utf8',
);
const backendHandlerSource = await readFile(
  path.resolve(
    projectRoot,
    '../feibo_backend_master/digital-doctor-infrastructure/src/main/java/com/hzmwmt/infrastructure/gateway/netty/server/NettyServerHandler.java',
  ),
  'utf8',
);

assert.match(socketSource, /public sendBinary\(/, 'WebSocket 服务必须支持二进制帧');
assert.match(voiceInputSource, /sendBinary\(frame\)/, 'ASR 音频必须以二进制帧发送');
assert.doesNotMatch(componentSource, /type:\s*['"]audio['"]/, 'ASR 不得再发送 JSON 音频帧');
assert.match(voiceInputSource, /type:\s*['"]stop_asr['"]/, '停止指令必须与后端契约一致');
for (const eventName of ['asr_started', 'asr_partial', 'asr_final', 'asr_stopping', 'asr_error', 'asr_complete']) {
  assert.ok(voiceInputSource.includes(`'${eventName}'`), `必须订阅 ${eventName}`);
}
assert.match(componentSource, /let handledStructuredData = false;/);
assert.match(componentSource, /if \(!handledStructuredData\)/, '结构化 SSE 不得再次按普通文本累计');
assert.match(componentSource, /enqueueTtsStream\(replyTtsStreamId, delta\)/);
assert.match(componentSource, /currentEventType === ['"]thought['"]/);
assert.match(backendHandlerSource, /['"]asr_complete['"]/, '后端必须通知 ASR 会话完成');
assert.match(ttsPlaybackSource, /new TtsPrefetchQueue<string, PreparedTtsAudio>/,
  'TTS 播放必须使用有界有序预取队列');
assert.match(ttsPlaybackSource, /const task = context\.currentTask \?\? context\.pipeline\.take\(\);/);
assert.match(ttsPlaybackSource, /context\.pipeline\.enqueueMany\(segments\);/,
  '播放期间新到达的句子也必须进入预取队列');
assert.match(ttsPlaybackSource, /return prepareAudioBlob\(audioBlob, controller\.signal\);/,
  '下一句合成完成后必须同时预加载到备用播放器');
assert.doesNotMatch(ttsPlaybackSource, /AUDIO_PRELOAD_TIMEOUT_MS|addEventListener\(['"]canplay['"]/,
  '后台预加载不得让当前句等待 canplay 或固定超时才能播放');
assert.match(ttsPlaybackSource, /\.play\(\)\.then\(handlePlaying\)/,
  '正在播放状态必须由媒体确认开始，而非仅完成合成');

assert.match(voiceInputSource, /msg\.sessionId !== session\.id/, '所有ASR回调必须先校验会话标识');
assert.match(voiceInputSource, /discardPendingResults = false/, '普通停止与丢弃结果必须分开');
assert.match(voiceInputSource, /type: 'cancel_asr', sessionId: session\.id/, '取消必须带原会话标识');
assert.match(voiceInputSource, /sessionRef\.current !== session/, '异步授权与音频回调必须拒绝过期会话');
assert.match(voiceInputSource, /const FINISH_TIMEOUT_MS = 5_000;/, '识别收尾必须有有限超时');
assert.match(voiceInputSource, /const VOICE_INPUT_SILENCE_TIMEOUT_MS = 30_000;/);
assert.match(voiceInputSource, /const VOICE_INPUT_MAX_DURATION_MS = 5 \* 60_000;/);
assert.match(voiceInputSource, /onStateChange\(state =>/, '断线必须恢复输入状态');
assert.match(componentSource, /cancelActiveInteraction = \(\) => \{\s*stopVoiceInput\(true\);/, '换上下文必须停止录音');
assert.match(componentSource, /editVoiceInput\(e.target.value\)/, '手工编辑不能被后续识别覆盖');
assert.match(componentSource, /disabled=\{isSending \|\| isLoading\}/, 'AI回答未完时输入框保持禁用');
const callbackStart = voiceInputSource.indexOf("msg.type === 'asr_partial' ||");
const callbackEnd = voiceInputSource.indexOf("msg.type === 'asr_stopping'", callbackStart);
assert.doesNotMatch(voiceInputSource.slice(callbackStart, callbackEnd), /lastActivityAt\s*=/,
  '识别文本不能为静音计时续期');
const sendHandlerStart = componentSource.indexOf('const handleSendMessage = async () => {');
const sendHandlerEnd = componentSource.indexOf('const generateAIResponse', sendHandlerStart);
assert.ok(sendHandlerStart >= 0 && sendHandlerEnd > sendHandlerStart);
const sendHandlerSource = componentSource.slice(sendHandlerStart, sendHandlerEnd);
const snapshotIndex = sendHandlerSource.indexOf('const messageContent = inputValue.trim();');
const stopIndex = sendHandlerSource.indexOf('stopVoiceInput(true);');
const clearIndex = sendHandlerSource.indexOf("setInputValue('');");
assert.ok(snapshotIndex >= 0 && stopIndex > snapshotIndex && clearIndex > stopIndex,
  '发送必须先快照文字，再停止录音并清空输入框');
await import('./verify-voice-input-lifecycle.mjs');

await import('./verify-tts-streaming-text.mjs');
await import('./verify-tts-playback-lifecycle.mjs');
await import('./verify-tts-api-lifecycle.mjs');
await import('./verify-voice-toolbar.mjs');

console.log('Voice input and streaming TTS contracts verified.');

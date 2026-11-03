import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';

const projectRoot = process.env.TTS_TEST_PROJECT_ROOT
  ? path.resolve(process.env.TTS_TEST_PROJECT_ROOT)
  : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const compile = async relativePath => ts.transpileModule(
  await readFile(path.join(projectRoot, relativePath), 'utf8'),
  { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } },
).outputText;
const [hookCode, queueCode, chunkerCode, markdownCode] = await Promise.all([
  compile('src/hooks/useTtsPlayback.ts'),
  compile('src/services/ttsPrefetchQueue.ts'),
  compile('src/services/streamingTts.ts'),
  compile('src/services/streamingTtsMarkdown.ts'),
]);

const loadModule = (code, globals = {}) => {
  const exports = {};
  vm.runInNewContext(code, { exports, AbortController, DOMException, Error, ...globals });
  return exports;
};
const queueModule = loadModule(queueCode);
const chunkerModule = loadModule(chunkerCode);
const markdownModule = loadModule(markdownCode);
const flush = async () => {
  for (let index = 0; index < 30; index += 1) await Promise.resolve();
};
const firstText = '第一句已经准备完成，可以开始播放。';
const secondText = '第二句随后到达，也应该提前准备。';
const thirdText = '第三句保持顺序，不能提前抢先播放。';

// Execute the production hook with injected React hook primitives and Audio.
// The host models rerenders, dependency identity and effect cleanup; media events
// are controlled explicitly. This is lifecycle coverage, not real-device audio QA.
const createHarness = ({ ignoreSynthesisAbort = false, delayPlaybackStart = false, failCancellation = false } = {}) => {
  const events = [];
  const requests = [];
  const players = [];
  const objectUrls = new Map();
  const revokedUrls = [];
  const blobText = new WeakMap();
  const timers = new Map();
  const cancelledSessions = [];
  const slots = [];
  let cursor = 0;
  let mounted = true;
  let scheduled = false;
  let currentHook;
  let nextUrl = 0;
  let nextTimer = 0;
  let nextSession = 0;
  let effects = [];
  let hook;

  const scheduleRender = () => {
    if (!mounted || scheduled) return;
    scheduled = true;
    queueMicrotask(() => {
      scheduled = false;
      if (mounted) render();
    });
  };
  const sameDependencies = (left, right) => left?.length === right?.length
    && left.every((value, index) => Object.is(value, right[index]));
  const react = {
    useRef(initialValue) {
      const index = cursor++;
      slots[index] ??= { current: initialValue };
      return slots[index];
    },
    useState(initialValue) {
      const index = cursor++;
      slots[index] ??= { value: initialValue };
      const setValue = value => {
        slots[index].value = typeof value === 'function' ? value(slots[index].value) : value;
        scheduleRender();
      };
      return [slots[index].value, setValue];
    },
    useCallback(callback, dependencies) {
      const index = cursor++;
      if (!sameDependencies(slots[index]?.dependencies, dependencies)) {
        slots[index] = { callback, dependencies };
      }
      return slots[index].callback;
    },
    useEffect(effect, dependencies) {
      const index = cursor++;
      if (sameDependencies(slots[index]?.dependencies, dependencies)) return;
      const previous = slots[index];
      slots[index] = { dependencies, cleanup: previous?.cleanup };
      effects.push(() => {
        slots[index].cleanup?.();
        slots[index].cleanup = effect();
      });
    },
  };

  class MockAudio extends EventTarget {
    constructor() {
      super();
      this.id = players.length;
      this.src = '';
      this.paused = true;
      this.readyState = 0;
      this.error = null;
      players.push(this);
    }
    load() {
      this.error = null;
      this.readyState = 0;
      if (this.src) events.push({ type: 'load', player: this.id, text: objectUrls.get(this.src) });
      // Intentionally never emit canplay automatically: mobile loading can wait
      // for play(). Tests must still reach playback without advancing any timer.
    }
    play() {
      events.push({ type: 'play', player: this.id, text: objectUrls.get(this.src) });
      if (delayPlaybackStart) {
        return new Promise(resolve => {
          this.completePlay = () => { this.paused = false; resolve(); };
        });
      }
      this.paused = false;
      return Promise.resolve();
    }
    pause() {
      this.paused = true;
    }
    removeAttribute(name) {
      if (name === 'src') this.src = '';
    }
    finish() {
      this.paused = true;
      this.onended?.();
      this.dispatchEvent(new Event('ended'));
    }
    canPlay() {
      this.readyState = 3;
      this.dispatchEvent(new Event('canplay'));
    }
  }

  const synthesis = (text, { signal, sessionId, requestId }) => new Promise((resolve, reject) => {
    const request = {
      text, signal, sessionId, requestId,
      resolve() {
        const blob = new Blob([text], { type: 'audio/mpeg' });
        blobText.set(blob, text);
        resolve(blob);
      },
      reject,
    };
    requests.push(request);
    events.push({ type: 'synthesize', text });
    const abort = () => {
      events.push({ type: 'abort', text });
      if (!ignoreSynthesisAbort) reject(new DOMException('cancelled', 'AbortError'));
    };
    if (signal.aborted) abort();
    else signal.addEventListener('abort', abort, { once: true });
  });

  const module = loadModule(hookCode, {
    Audio: MockAudio,
    URL: {
      createObjectURL(blob) {
        const url = `blob:test-${++nextUrl}`;
        objectUrls.set(url, blobText.get(blob));
        return url;
      },
      revokeObjectURL(url) {
        assert.ok(objectUrls.has(url), '每个音频 URL 必须恰好释放一次');
        objectUrls.delete(url);
        revokedUrls.push(url);
      },
    },
    window: {
      setTimeout(callback, delay) {
        const id = ++nextTimer;
        timers.set(id, { callback, delay });
        return id;
      },
      clearTimeout(id) { timers.delete(id); },
    },
    require(name) {
      if (name === 'react') return react;
      if (name === '@/services/ttsApi') {
        return {
          synthesizeTtsSegment: synthesis,
          TTS_MAX_SEGMENT_LENGTH: 500,
          createTtsSessionId: () => `test-session-${++nextSession}`,
          cancelTtsSession: async sessionId => {
            cancelledSessions.push(sessionId);
            if (failCancellation) throw new Error('cancel unavailable');
          },
        };
      }
      if (name === '@/services/ttsPrefetchQueue') return queueModule;
      if (name === '@/services/streamingTts') return chunkerModule;
      if (name === '@/services/streamingTtsMarkdown') return markdownModule;
      throw new Error(`Unexpected dependency: ${name}`);
    },
  });
  hook = module.useTtsPlayback;
  function render() {
    cursor = 0;
    currentHook = hook();
    const pendingEffects = effects;
    effects = [];
    pendingEffects.forEach(effect => effect());
  }
  render();

  return {
    get api() { return currentHook; },
    events, requests, players, objectUrls, revokedUrls, timers, cancelledSessions,
    fireTextTimers() {
      const pending = [...timers.entries()];
      for (const [id, timer] of pending) { timers.delete(id); timer.callback(); }
    },
    playedTexts: () => events.filter(event => event.type === 'play' && event.text !== undefined).map(event => event.text),
    unmount() {
      mounted = false;
      slots.forEach(slot => slot?.cleanup?.());
    },
  };
};

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText);
  h.api.finishStream(stream);
  await flush();
  h.requests[0].resolve();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText], '首句无 canplay 时也必须立即调用 play()');
  assert.equal(h.timers.size, 0, '不能通过等待固定 15 秒来启动当前句');
  h.players[0].finish();
  await flush();
  assert.equal(h.api.status, 'idle');
  assert.equal(h.objectUrls.size, 0);
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText);
  await flush();
  h.requests[0].resolve();
  await flush();
  h.api.enqueueStream(stream, secondText + thirdText);
  h.api.finishStream(stream);
  await flush();
  assert.equal(h.requests.length, 2, '播放期间迟到的下一句应立即合成，第三句仍等待');
  h.requests[1].resolve();
  await flush();
  assert.equal(h.players[0].paused, false);
  assert.ok(h.events.some(event => event.type === 'load' && event.text === secondText && event.player === 1),
    '当前句仍播放时，迟到的第二句必须已经加载到备用播放器');
  assert.deepEqual(h.playedTexts(), [firstText]);
  h.players[0].finish();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText, secondText]);
  assert.equal(h.requests.length, 3, '开始第二句时补齐第三句预取');
  h.requests[2].resolve();
  await flush();
  h.players[1].finish();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText, secondText, thirdText]);
  h.players[0].finish();
  await flush();
  assert.equal(h.objectUrls.size, 0);
  assert.equal(h.revokedUrls.length, 3);
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText + secondText);
  h.api.finishStream(stream);
  await flush();
  h.requests[1].resolve();
  await flush();
  h.players[0].canPlay();
  await flush();
  assert.deepEqual(h.playedTexts(), [], '第二句先 ready 也不得越过尚未完成的首句');
  h.requests[0].resolve();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText]);
  h.players[1].finish();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText, secondText]);
  h.players[0].finish();
  await flush();
  assert.equal(h.objectUrls.size, 0);
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText + secondText + thirdText);
  await flush();
  h.requests[0].resolve();
  await flush();
  h.api.stop();
  await flush();
  assert.ok(h.requests.every(request => request.signal.aborted), '停止必须取消当前和预取的请求');
  assert.equal(h.requests.length, 2, '停止后不能启动第三句');
  assert.equal(h.objectUrls.size, 0);
  assert.ok(h.players.every(player => player.paused && !player.src));
  assert.equal(h.api.status, 'idle');
  assert.deepEqual(h.cancelledSessions, ['test-session-1'], '停止必须独立通知后端取消对应会话');
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText + secondText);
  await flush();
  h.requests.forEach(request => request.resolve());
  await flush();
  assert.equal(h.objectUrls.size, 2);
  h.unmount();
  await flush();
  assert.equal(h.objectUrls.size, 0, '卸载必须同时回收正在播放和后台预加载的 URL');
  assert.equal(h.revokedUrls.length, 2);
  assert.ok(h.requests.every(request => request.signal.aborted));
  assert.ok(h.players.every(player => player.paused && !player.src));
}

{
  const h = createHarness({ ignoreSynthesisAbort: true });
  const oldStream = h.api.startStream();
  h.api.enqueueStream(oldStream, firstText);
  await flush();
  const newStream = h.api.startStream();
  h.api.enqueueStream(newStream, secondText);
  h.api.finishStream(newStream);
  await flush();
  h.requests[0].resolve();
  await flush();
  assert.deepEqual(h.playedTexts(), [], '无法取消的旧请求迟到也不能进入播放器');
  assert.equal(h.objectUrls.size, 0);
  h.requests[1].resolve();
  await flush();
  assert.deepEqual(h.playedTexts(), [secondText]);
  assert.equal(h.api.status, 'playing', '旧流完成不得覆盖新流状态');
  h.api.stop();
  await flush();
  assert.equal(h.objectUrls.size, 0);
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText + secondText);
  h.api.finishStream(stream);
  await flush();
  h.requests.forEach(request => request.resolve());
  await flush();
  h.players[1].error = { code: 4 };
  h.players[0].finish();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText], '后台加载已失败的音频不能继续播放');
  assert.equal(h.api.status, 'error');
  assert.match(h.api.error, /预加载失败/);
  assert.equal(h.objectUrls.size, 0, '后台加载失败也必须回收全部 URL');
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText + secondText);
  h.api.finishStream(stream);
  await flush();
  h.requests.forEach(request => request.resolve());
  await flush();
  h.players[0].currentTime = 2.5;
  h.api.pause();
  await flush();
  assert.equal(h.api.status, 'paused');
  assert.equal(h.players[0].paused, true);
  h.api.setPlaybackRate(1.25);
  h.api.resume();
  await flush();
  assert.equal(h.players[0].currentTime, 2.5, '继续播放必须保留当前句位置');
  assert.equal(h.players[0].playbackRate, 1.25);
  assert.equal(h.players[1].playbackRate, 1.25, '备用播放器也需应用新的语速');
  assert.equal(h.requests.length, 2, '暂停、继续和改语速不得再次调用供应商');
  assert.equal(h.api.status, 'playing');
  h.api.stop();
  await flush();
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText);
  await flush();
  h.api.pause();
  h.requests[0].resolve();
  await flush();
  assert.deepEqual(h.playedTexts(), [], '合成中暂停后，合成完成也不能自动开始播放');
  assert.equal(h.api.status, 'paused');
  h.api.resume();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText]);
  h.api.stop();
  await flush();
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, firstText + secondText);
  await flush();
  h.requests[0].reject(new Error('暂时繁忙'));
  h.requests[1].resolve();
  await flush();
  assert.equal(h.api.status, 'error');
  assert.equal(h.api.canRetry, true);
  h.api.enqueueStream(stream, thirdText);
  h.api.finishStream(stream);
  await flush();
  assert.equal(h.requests.length, 2, '失败后收到后文不能擅自重试当前句');
  assert.equal(h.objectUrls.size, 1, '必须保留已经预加载的下一句');
  h.api.retry();
  await flush();
  assert.equal(h.requests.length, 3);
  assert.equal(h.requests[2].requestId, h.requests[0].requestId, '显式重试必须保持逻辑请求ID');
  assert.equal(h.requests[2].sessionId, h.requests[0].sessionId);
  h.requests[2].resolve();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText]);
  h.players[1].finish();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText, secondText]);
  assert.equal(h.requests.length, 4);
  assert.equal(h.requests[3].text, thirdText);
  h.requests[3].resolve();
  await flush();
  h.players[0].finish();
  await flush();
  assert.deepEqual(h.playedTexts(), [firstText, secondText, thirdText]);
  h.players[1].finish();
  await flush();
  assert.equal(h.api.metrics.retries, 1);
  assert.equal(h.api.metrics.errors, 1);
  assert.equal(h.api.metrics.completedSegments, 3);
  assert.ok(Object.values(h.api.metrics).every(value => value === null || typeof value === 'number'),
    '度量中不得包含正文、音频或用户标识');
  assert.equal(h.objectUrls.size, 0);
  h.unmount();
}

{
  const h = createHarness({ delayPlaybackStart: true });
  const stream = h.api.startStream();
  await flush();
  assert.equal(h.api.status, 'waiting-text');
  h.api.enqueueStream(stream, firstText);
  await flush();
  h.requests[0].resolve();
  await flush();
  assert.equal(h.api.status, 'synthesizing', 'play Promise 尚未成功不能显示正在播放');
  assert.equal(h.api.metrics.firstAudioMs, null);
  h.players[0].completePlay();
  await flush();
  assert.equal(h.api.status, 'playing');
  assert.ok(h.api.metrics.firstAudioMs >= 0);
  h.players[0].finish();
  await flush();
  assert.equal(h.api.status, 'waiting-text', '流未结束且句子读完时应显示等待后文');
  h.api.finishStream(stream);
  await flush();
  assert.equal(h.api.status, 'idle');
  h.unmount();
}

{
  const h = createHarness();
  const stream = h.api.startStream();
  h.api.enqueueStream(stream, '这段回复虽然没有句号但也应该及时开始朗读');
  await flush();
  assert.equal(h.requests.length, 0);
  assert.equal(h.timers.size, 1);
  assert.equal([...h.timers.values()][0].delay, 1200);
  h.fireTextTimers();
  await flush();
  assert.equal(h.requests.length, 1, '无句号的稳定中文短语应在有界等待后开始合成');
  h.api.stop();
  await flush();
  assert.equal(h.timers.size, 0);
  h.unmount();
}

{
  const h = createHarness({ failCancellation: true });
  h.api.speak(firstText);
  await flush();
  h.api.stop();
  await flush();
  assert.equal(h.api.status, 'idle', '远端取消暂时失败也必须立即停止本地播放');
  assert.equal(h.api.metrics.cancellationFailures, 1, '取消未确认只能记录失败，不能假报成功');
  assert.ok(h.requests[0].signal.aborted);
  h.unmount();
}

console.log('TTS playback lifecycle verified (13 injected Audio/hook scenarios).');

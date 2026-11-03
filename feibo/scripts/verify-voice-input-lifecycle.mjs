import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';

const root = process.env.VOICE_TEST_PROJECT_ROOT || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const compile = async file => ts.transpileModule(await readFile(path.join(root, file), 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;
const hookCode = await compile('src/hooks/useVoiceInput.ts');
const audioCode = await compile('src/services/asrAudio.ts');
const audioExports = {};
vm.runInNewContext(audioCode, { exports: audioExports });
const flush = async () => { for (let n = 0; n < 15; n++) await Promise.resolve(); };

function createHarness({ pendingPermission = false } = {}) {
  let clock = 0, sequence = 0, timerId = 0, cursor = 0, mounted = true, scheduled = false;
  let value = '', disabled = false, hook, current, pendingEffects = [], connected = true, resolvePermission;
  const slots = [], commands = [], frames = [], tracks = [], processors = [];
  const timers = new Map(), listeners = new Map(), stateListeners = new Set(), documentListeners = new Map();
  const same = (a, b) => a?.length === b?.length && a.every((v, i) => Object.is(v, b[i]));
  const schedule = () => { if (scheduled || !mounted) return; scheduled = true; queueMicrotask(() => { scheduled = false; if (mounted) render(); }); };
  const react = {
    useRef(value) { const i = cursor++; slots[i] ??= { current: value }; return slots[i]; },
    useState(value) {
      const i = cursor++;
      slots[i] ??= { value, set(next) { slots[i].value = typeof next === 'function' ? next(slots[i].value) : next; schedule(); } };
      return [slots[i].value, slots[i].set];
    },
    useCallback(callback, deps) { const i = cursor++; if (!same(slots[i]?.deps, deps)) slots[i] = { callback, deps }; return slots[i].callback; },
    useEffect(effect, deps) {
      const i = cursor++; if (same(slots[i]?.deps, deps)) return;
      const previous = slots[i]; slots[i] = { deps, cleanup: previous?.cleanup };
      pendingEffects.push(() => { slots[i].cleanup?.(); slots[i].cleanup = effect(); });
    },
  };
  const websocket = {
    sendMessage(message) { if (!connected) return false; commands.push(message); return true; },
    sendBinary(frame) { if (!connected) return false; frames.push(frame); return true; },
    subscribe(type, callback) { if (!listeners.has(type)) listeners.set(type, new Set()); listeners.get(type).add(callback); return () => listeners.get(type).delete(callback); },
    onStateChange(callback) { stateListeners.add(callback); return () => stateListeners.delete(callback); },
    isConnected: () => connected,
    connect: async () => { connected = true; return true; },
  };
  class AudioContext {
    state = 'running'; sampleRate = 48_000; destination = {};
    createMediaStreamSource() { return { connect() {}, disconnect() {} }; }
    createScriptProcessor() { const node = { connect() {}, disconnect() {}, onaudioprocess: null }; processors.push(node); return node; }
    resume() { return Promise.resolve(); }
    close() { this.state = 'closed'; return Promise.resolve(); }
  }
  const doc = { hidden: false, addEventListener(type, cb) { documentListeners.set(type, cb); }, removeEventListener(type) { documentListeners.delete(type); } };
  const getStream = () => {
    const track = { stopped: false, stop() { this.stopped = true; }, onended: null };
    tracks.push(track);
    return { getTracks: () => [track] };
  };
  const exports = {};
  vm.runInNewContext(hookCode, {
    exports,
    require(name) {
      if (name === 'react') return react;
      if (name === './useWebSocket') return { useWebSocket: () => websocket };
      if (name.endsWith('asrAudio')) return audioExports;
      if (name.endsWith('websocketService')) return { WebSocketState: { CONNECTED: 'connected', CONNECTING: 'connecting' } };
      throw new Error(name);
    },
    Date: { now: () => clock }, crypto: { randomUUID: () => `session-${++sequence}` }, document: doc,
    navigator: { mediaDevices: { getUserMedia: () => pendingPermission ? new Promise(resolve => { resolvePermission = () => resolve(getStream()); }) : Promise.resolve(getStream()) } },
    window: {
      AudioContext,
      setTimeout(cb, delay) { const id = ++timerId; timers.set(id, { cb, at: clock + delay }); return id; },
      clearTimeout(id) { timers.delete(id); },
      setInterval(cb, delay) { const id = ++timerId; timers.set(id, { cb, at: clock + delay, interval: delay }); return id; },
      clearInterval(id) { timers.delete(id); },
    },
  });
  hook = exports.useVoiceInput;
  const onChange = next => { value = next; schedule(); };
  function render() { cursor = 0; pendingEffects = []; current = hook({ value, onChange, disabled }); const effects = pendingEffects; effects.forEach(effect => effect()); }
  render();
  const emit = (type, content = '', sessionId = commands.findLast(command => command.type === 'start_asr')?.sessionId, reason) => {
    for (const cb of listeners.get(type) || []) cb({ type, content, sessionId, reason });
  };
  return {
    get hook() { return current; }, get value() { return value; }, commands, frames, tracks, processors, timers,
    emit,
    async start() { await current.toggle(); emit('asr_started'); await flush(); },
    async advance(ms) {
      const end = clock + ms;
      for (;;) {
        const due = [...timers].filter(([, timer]) => timer.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
        if (!due) break;
        const [id, timer] = due; clock = timer.at;
        if (timer.interval) timer.at += timer.interval; else timers.delete(id);
        timer.cb(); await flush();
      }
      clock = end; await flush();
    },
    input(amplitude = 0.05) {
      processors.at(-1)?.onaudioprocess?.({ inputBuffer: { getChannelData: () => Float32Array.from({ length: 4096 }, (_, i) => amplitude * Math.sin(i * 0.2)) } });
    },
    disconnect() { connected = false; stateListeners.forEach(cb => cb('disconnected')); },
    disable() { disabled = true; render(); },
    allow() { disabled = false; render(); },
    resolvePermission: () => resolvePermission(),
    hide() { doc.hidden = true; documentListeners.get('visibilitychange')?.(); },
    unmount() { mounted = false; slots.forEach(slot => slot.cleanup?.()); },
  };
}

{
  const h = createHarness(); await h.start(); h.input();
  assert.equal(h.hook.phase, 'recording'); assert.ok(h.frames.length > 0);
  h.emit('asr_partial', '错误旧内容', 'older-session'); h.emit('asr_final', '无标识内容', null);
  assert.equal(h.value, '');
  h.emit('asr_partial', '我的话'); assert.equal(h.value, '我的话');
  h.hook.stop(true); h.emit('asr_final', '迟到内容'); await flush();
  assert.equal(h.value, '我的话'); assert.equal(h.hook.phase, 'idle'); assert.ok(h.tracks[0].stopped);
  await h.start(); h.emit('asr_complete', '', 'session-1'); await flush();
  assert.equal(h.hook.phase, 'recording'); h.unmount(); assert.equal(h.timers.size, 0);
}
{
  const h = createHarness(); await h.start(); h.emit('asr_partial', '待发送');
  h.hook.stop(true); h.hook.edit(''); h.disable(); h.emit('asr_final', '不能回填', 'session-1');
  await h.hook.toggle(); await flush(); assert.equal(h.value, ''); assert.equal(h.commands.filter(c => c.type === 'start_asr').length, 1);
  h.allow(); await h.start(); assert.equal(h.hook.phase, 'recording'); h.unmount();
}
{
  const h = createHarness(); await h.start(); h.emit('asr_partial', '尾'); h.hook.stop(false); await flush();
  assert.equal(h.hook.phase, 'finishing'); assert.ok(h.tracks[0].stopped);
  h.emit('asr_final', '完整尾句'); h.emit('asr_complete'); await flush();
  assert.equal(h.value, '完整尾句'); assert.equal(h.hook.phase, 'idle'); h.unmount();
}
{
  const h = createHarness(); await h.start(); h.hook.stop(false); await h.advance(5_000);
  assert.equal(h.hook.phase, 'idle'); assert.match(h.hook.notice, /超时/);
  await h.start(); h.hook.stop(false); h.disconnect(); await flush();
  assert.equal(h.hook.phase, 'idle'); assert.match(h.hook.notice, /断开/); h.unmount();
}
{
  const h = createHarness(); await h.start(); h.emit('asr_partial', '旧前缀'); h.hook.edit('手工修改');
  h.emit('asr_final', '覆盖内容', 'session-1'); await flush();
  assert.equal(h.value, '手工修改'); assert.equal(h.hook.phase, 'idle'); h.unmount();
}
{
  const h = createHarness({ pendingPermission: true }); const starting = h.hook.toggle(); await flush();
  h.hook.stop(true); h.resolvePermission(); await starting; await flush();
  assert.equal(h.hook.phase, 'idle'); assert.ok(h.tracks[0].stopped); assert.equal(h.commands.filter(c => c.type === 'start_asr').length, 0); h.unmount();
}
{
  const h = createHarness(); await h.start();
  for (let i = 0; i < 3; i++) { h.emit('asr_partial', '模拟上游静音误识别'); await h.advance(10_000); }
  assert.equal(h.hook.phase, 'finishing'); assert.match(h.hook.notice, /静音 30 秒/);
  h.emit('asr_complete'); await flush(); h.unmount();
}
{
  const h = createHarness(); await h.start();
  for (let i = 0; i < 30; i++) { h.input(); await h.advance(10_000); }
  assert.equal(h.hook.phase, 'finishing'); assert.match(h.hook.notice, /5 分钟/); assert.ok(h.tracks[0].stopped); h.unmount();
}
{
  const h = createHarness(); await h.start(); h.emit('asr_stopping', '', 'session-1', 'silence_timeout'); await flush();
  assert.equal(h.hook.phase, 'finishing'); assert.ok(h.tracks[0].stopped); h.emit('asr_final', '尾句'); h.emit('asr_complete'); await flush(); assert.equal(h.value, '尾句');
  await h.start(); h.hide(); await flush(); assert.equal(h.hook.phase, 'finishing'); h.unmount();
}
{
  const h = createHarness({ pendingPermission: true }); const starting = h.hook.toggle(); await flush();
  await h.advance(30_000); assert.equal(h.hook.phase, 'idle'); assert.match(h.hook.notice, /等待超时/);
  h.resolvePermission(); await starting; await flush();
  assert.ok(h.tracks[0].stopped, 'A late permission grant must immediately release its microphone');
  assert.equal(h.commands.filter(command => command.type === 'start_asr').length, 0);
  assert.equal(h.timers.size, 0); h.unmount();
}
{
  const h = createHarness(); await h.hook.toggle(); await flush(); h.input();
  assert.equal(h.hook.phase, 'starting'); assert.equal(h.frames.length, 0, 'No PCM may precede asr_started');
  await h.advance(8_000); assert.equal(h.hook.phase, 'idle'); assert.ok(h.tracks[0].stopped);
  h.emit('asr_started', '', 'session-1'); await flush();
  assert.equal(h.hook.phase, 'idle', 'An acknowledgement after timeout cannot restart capture');
  assert.equal(h.timers.size, 0); h.unmount();
}
{
  const h = createHarness(); await h.start(); const oldAudioCallback = h.processors[0].onaudioprocess;
  h.hook.stop(true); await h.start();
  const before = h.frames.length;
  oldAudioCallback({ inputBuffer: { getChannelData: () => new Float32Array(4096) } });
  assert.equal(h.frames.length, before, 'An already queued old processor must not send into the new session');
  h.input(); assert.ok(h.frames.length > before); h.unmount();
}
{
  const h = createHarness(); await h.start(); h.emit('asr_partial', '已识别文字');
  h.tracks[0].onended(); await flush();
  assert.equal(h.hook.phase, 'idle'); assert.match(h.hook.notice, /麦克风已断开/);
  h.emit('asr_final', '迟到设备结果', 'session-1'); assert.equal(h.value, '已识别文字');
  assert.equal(h.timers.size, 0); h.unmount();
}
console.log('Voice input lifecycle: 13 controlled scenarios passed (not real microphone acceptance).');

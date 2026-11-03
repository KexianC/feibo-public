import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';

const projectRoot = process.env.TTS_TEST_PROJECT_ROOT
  ? path.resolve(process.env.TTS_TEST_PROJECT_ROOT)
  : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = await readFile(path.join(projectRoot, 'src/services/ttsApi.ts'), 'utf8');
const output = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const exports = {};
const calls = [];
const timers = new Map();
let nextTimer = 0;
let bodyMode = 'audio';
vm.runInNewContext(output, {
  exports, Blob, AbortController, DOMException, Error, Uint8Array,
  crypto: { randomUUID: () => 'test-session-uuid' },
  window: {
    setTimeout(callback, delay) { const id = ++nextTimer; timers.set(id, { callback, delay }); return id; },
    clearTimeout(id) { timers.delete(id); },
  },
  require(name) {
    if (name === '@/config/api') return { API_CONFIG: { BASE_URL: 'https://api.example.test' } };
    if (name === '@/services/http/authenticatedRawRequest') {
      return {
        authenticatedRawRequest: async (url, options, config) => {
          calls.push({ url, options, config });
          if (url.endsWith('/cancel')) return new Response('{}', { headers: { 'content-type': 'application/json' } });
          if (bodyMode === 'pending') {
            return new Response(new ReadableStream({}), { headers: { 'content-type': 'audio/mpeg' } });
          }
          return new Response(new Uint8Array([73, 68, 51]), { headers: { 'content-type': 'audio/mpeg' } });
        },
      };
    }
    throw new Error(`Unexpected dependency: ${name}`);
  },
});

const controller = new AbortController();
const options = { sessionId: 'voice_session_1', requestId: 'voice_session_1-1', signal: controller.signal };
const audio = await exports.synthesizeTtsSegment('第一句测试。', options);
assert.equal(audio.size, 3);
assert.deepEqual(JSON.parse(calls[0].options.body), {
  text: '第一句测试。', sessionId: options.sessionId, requestId: options.requestId,
});
assert.equal(calls[0].config.idempotencyKey, options.requestId);
await exports.synthesizeTtsSegment('第一句测试。', options);
assert.equal(calls[1].config.idempotencyKey, calls[0].config.idempotencyKey,
  '显式重试同一句必须透传相同的后端去重身份');
assert.equal(timers.size, 0);

controller.abort();
await exports.cancelTtsSession(options.sessionId);
const cancel = calls.at(-1);
assert.equal(cancel.url, 'https://api.example.test/api/v1/ai/tts/cancel');
assert.equal(cancel.options.signal, undefined, '取消请求不能复用已中断的合成 signal');
assert.equal(cancel.options.timeoutMs, 5000);
assert.equal(cancel.options.keepalive, true);
assert.deepEqual(JSON.parse(cancel.options.body), { sessionId: options.sessionId });
assert.equal(cancel.config.idempotencyKey, `${options.sessionId}-cancel`);

const count = calls.length;
await assert.rejects(exports.synthesizeTtsSegment('测试', { sessionId: '../invalid' }), /标识无效/);
await assert.rejects(exports.cancelTtsSession('x'.repeat(129)), /标识无效/);
assert.equal(calls.length, count, '无效请求身份应在发出请求前拒绝');

bodyMode = 'pending';
const interrupted = new AbortController();
const reading = exports.synthesizeTtsSegment('仍在下载音频。', {
  sessionId: 'stream_body', requestId: 'stream_body-1', signal: interrupted.signal,
});
for (let index = 0; index < 10; index += 1) await Promise.resolve();
interrupted.abort();
await assert.rejects(reading, error => error.name === 'AbortError');
assert.equal(timers.size, 0, '响应体读取中取消必须结束等待并清理超时');

console.log('TTS API identity, cancellation and body-read lifecycle verified.');

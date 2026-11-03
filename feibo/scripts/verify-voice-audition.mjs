import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';

const source = await readFile(new URL('../src/services/digitalHumanApi.ts', import.meta.url), 'utf8');
const output = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const exports = {};
const calls = [];
vm.runInNewContext(output, {
  exports,
  require(name) {
    if (name === './authApi') return {
      authenticatedRequest: async (url, options) => {
        calls.push({ url, body: JSON.parse(options.body) });
        return { data: { url: 'https://audio.example.test/preview.mp3' } };
      },
    };
    if (name === './http/backendClient') return {};
    if (name === 'uuid') return { v4: () => 'unused' };
    throw new Error(`Unexpected dependency: ${name}`);
  },
});

for (const speaker of [
  'galaxy_fastv2_moqingyan',
  '11db287fb8dab21a1250f8fc32b9d24b',
  'galaxy_fastv7||||galaxy_fastv7||||11db287fb8dab21a1250f8fc32b9d24b',
]) {
  const result = await exports.auditionVoice({
    text: '欢迎来到飞驳数字医生工作台', speaker: ` ${speaker} `, speed: 1, volume: 1,
  });
  assert.equal(calls.at(-1).url, '/digital-human/open/voice/audition');
  assert.equal(calls.at(-1).body.speaker, speaker, '复合音色标识必须完整发送，不能只保留末尾 ID');
  assert.equal(calls.at(-1).body.audioType, 'mp3');
  assert.equal(result.url, 'https://audio.example.test/preview.mp3');
}
console.log('Voice audition: standard and cloned speaker identities preserved.');

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';

const projectRoot = process.env.TTS_TEST_PROJECT_ROOT
  ? path.resolve(process.env.TTS_TEST_PROJECT_ROOT)
  : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const load = async relativePath => {
  const source = await readFile(path.join(projectRoot, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const exports = {};
  vm.runInNewContext(output, { exports });
  return exports;
};
const { StreamingTtsMarkdown } = await load('src/services/streamingTtsMarkdown.ts');
const { StreamingTtsChunker } = await load('src/services/streamingTts.ts');
const normalize = text => text.replace(/\s+/g, ' ').trim();

const fixtures = [
  {
    source: '下面是仅供查看的示例。\n```text\n这段代码不应读出。\n```\n最后是正常说明。',
    expected: '下面是仅供查看的示例。 最后是正常说明。',
  },
  {
    source: '请看说明[点这里](https://www.example.com/path?foo=bar)。',
    expected: '请看说明点这里。',
  },
  {
    source: '请参考[疾病管理指南。补充说明](https://example.com/path(a)?foo=bar)继续了解。',
    expected: '请参考疾病管理指南。补充说明继续了解。',
  },
  {
    source: '先读正文。<thought>这里是思考过程，不得读出。</thought>再读结论。',
    expected: '先读正文。 再读结论。',
  },
  {
    source: '详情 https://www.example.com/path?foo=bar。请观察体温 37.5℃。',
    expected: '详情 。请观察体温 37.5℃。',
  },
  {
    source: '## 医疗建议\n1. 先观察。\n2. 再记录。\n**不要**自行调整 `Metformin`。',
    expected: '医疗建议 先观察。 再记录。 不要自行调整 Metformin。',
  },
  {
    source: '图片![体温测量方法](https://example.com/a.png)与[注意事项][ref]。',
    expected: '图片体温测量方法与注意事项。',
  },
  {
    source: 'A &amp; B，血糖 &lt; 7。注意😀结束。',
    expected: 'A 和 B，血糖 小于 7。注意😀结束。',
  },
  {
    source: '1.5 mmol/L\n>7 时记录，<3 时也记录。',
    expected: '1.5 mmol/L >7 时记录，<3 时也记录。',
  },
];

for (const fixture of fixtures) {
  // Exercise every single split position, plus one UTF-16 code unit per delta.
  const variants = [fixture.source.split('')];
  for (let index = 0; index <= fixture.source.length; index += 1) {
    variants.push([fixture.source.slice(0, index), fixture.source.slice(index)]);
  }
  for (const deltas of variants) {
    const cleaner = new StreamingTtsMarkdown();
    let result = '';
    for (const delta of deltas) result += cleaner.push(delta);
    result += cleaner.finish();
    assert.equal(normalize(result), fixture.expected, `Markdown 跨 delta 清理失败: ${JSON.stringify(deltas)}`);
    assert.equal(cleaner.finish(), '', '结束清理必须幂等');
  }
}

{
  const cleaner = new StreamingTtsMarkdown();
  const chunker = new StreamingTtsChunker();
  const spoken = [];
  const source = '请看说明[点这里](https://www.example.com/path?foo=bar)。下面是代码。\n```text\n代码秘密不能读出。\n```\n最后结束。';
  for (const character of source) spoken.push(...chunker.push(cleaner.push(character)));
  spoken.push(...chunker.push(cleaner.finish()), ...chunker.finish());
  const text = spoken.join('');
  assert.doesNotMatch(text, /foo=|https|```|代码秘密|\]\(|\[/,
    '必须先进行增量清理，再分句，不能把 URL 或围栏碎片传入 TTS');
  assert.match(text, /点这里/);
  assert.match(text, /最后结束/);
}

{
  const chunker = new StreamingTtsChunker();
  chunker.push('这里是一段没有标点但应及时开始的中文描述');
  assert.ok(chunker.flushPending().length > 0, '等待到期应能产出稳定中文短语');
  assert.equal(chunker.hasPendingText, false);
  chunker.push('请继续观察体温37.');
  const first = chunker.flushPending().join('');
  assert.doesNotMatch(first, /37\./, '定时切段也不能读出尚未完整的数字');
  const rest = [...chunker.push('5℃。'), ...chunker.finish()].join('');
  assert.match(rest, /37\.5℃/);
}

console.log(`Streaming TTS text verified (${fixtures.length} fixtures at every delta boundary).`);

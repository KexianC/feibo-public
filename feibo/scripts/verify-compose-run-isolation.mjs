import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { Buffer } from 'node:buffer';
import ts from 'typescript';

const guardSource = await readFile(
  new URL('../src/services/composeRunGuard.ts', import.meta.url),
  'utf8',
);
const transpiled = ts.transpileModule(guardSource, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText;
const { ComposeRunGuard } = await import(
  `data:text/javascript;base64,${Buffer.from(transpiled).toString('base64')}`
);

const guard = new ComposeRunGuard();
const firstRun = guard.begin();
let visibleStatus = 'processing-first';

assert.equal(guard.runIfCurrent(firstRun, () => {
  visibleStatus = 'progress-first';
}), true);

const secondRun = guard.begin();
visibleStatus = 'processing-second';

assert.equal(guard.isCurrent(firstRun), false);
assert.equal(guard.isCurrent(secondRun), true);
assert.equal(guard.runIfCurrent(firstRun, () => {
  visibleStatus = 'completed-first';
}), false, '上一条任务完成时不得覆盖下一条任务的进度弹窗');
assert.equal(visibleStatus, 'processing-second');

assert.equal(guard.runIfCurrent(secondRun, () => {
  visibleStatus = 'completed-second';
}), true);
assert.equal(visibleStatus, 'completed-second');

const panelSource = await readFile(
  new URL('../src/components/BottomPanel.tsx', import.meta.url),
  'utf8',
);
assert.match(panelSource, /composeRunGuardRef\.current\.begin\(\)/);
assert.match(panelSource, /setCurrentTaskId\(''\)/);
assert.match(
  panelSource,
  /\(progress, status\) => \{[\s\S]{0,220}runIfCurrent/,
  '轮询回调必须经过当前合成任务校验',
);

console.log('compose run isolation contract: PASS');

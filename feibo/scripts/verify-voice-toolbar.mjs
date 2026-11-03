import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { Download, MessageCircle, Pause, Play, RotateCcw, Loader2, VolumeX, Volume2 } from 'lucide-react';
import ts from 'typescript';

const projectRoot = process.env.VOICE_TEST_PROJECT_ROOT
  ?? path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = await readFile(path.join(projectRoot, 'src/components/ZhibiYiyan.tsx'), 'utf8');
const sourceFile = ts.createSourceFile('ZhibiYiyan.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
assert.equal(sourceFile.parseDiagnostics.length, 0, 'The component must parse before its toolbar can be tested.');

const nodes = [];
const visit = node => { nodes.push(node); ts.forEachChild(node, visit); };
visit(sourceFile);
const hasHandler = (node, handler) => {
  let found = false;
  const search = candidate => {
    if (ts.isJsxAttribute(candidate)
      && candidate.name.getText(sourceFile) === 'onClick'
      && candidate.initializer && ts.isJsxExpression(candidate.initializer)
      && candidate.initializer.expression?.getText(sourceFile) === handler) found = true;
    ts.forEachChild(candidate, search);
  };
  search(node);
  return found;
};
const toolbar = nodes.filter(node => ts.isJsxElement(node)
  && hasHandler(node, 'handleDownloadChat') && hasHandler(node, 'toggleVoiceDialogue'))
  .sort((a, b) => a.getWidth(sourceFile) - b.getWidth(sourceFile))[0];
assert.ok(toolbar, 'Find the real bottom toolbar shared by export and voice mode.');
assert.equal(nodes.filter(node => ts.isJsxElement(node)
  && node.openingElement.tagName.getText(sourceFile) === 'button'
  && hasHandler(node, 'pauseTts')).length, 1,
  'Move the existing playback controls; do not leave a duplicate status row above the toolbar.');

// Render actual source JSX under controlled UI state, without invoking API hooks,
// microphone permissions, model requests, or a separately maintained JSX fixture.
const noop = () => {};
const baseProps = {
  Download, MessageCircle, Pause, Play, RotateCcw, Loader2, VolumeX, Volume2,
  handleDownloadChat: noop, toggleAllHistory: noop, toggleVoiceDialogue: noop,
  pauseTts: noop, resumeTts: noop, retryTts: noop, cancelTts: noop, unlockTts: noop,
  setPlaybackRate: noop, isAllHistoryEnabled: true, voiceDialogueEnabled: false,
  ttsStatus: 'idle', ttsError: null, currentTtsSegment: 7, totalTtsSegments: 14,
  canPauseTts: false, canResumeTts: false, canRetryTts: false, isLoading: false,
  playbackRate: 1, voiceInputPhase: 'idle', isRecording: false,
  isAwaitingAsrCompletion: false, voiceRemainingSeconds: 299, voiceInputNotice: '',
};
const compileJsx = jsx => {
  const output = ts.transpileModule(
    `function SourceFragment(props) { const { ${Object.keys(baseProps).join(', ')} } = props; return (${jsx}); }`,
    { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext, jsx: ts.JsxEmit.React } },
  ).outputText;
  return new Function('React', `${output}\nreturn SourceFragment;`)(React);
};
const Toolbar = compileJsx(toolbar.getText(sourceFile));
const render = (props = {}) => renderToStaticMarkup(React.createElement(Toolbar, { ...baseProps, ...props }));
const plainText = markup => markup.replace(/<[^>]*>/g, '');
const speedLabel = 'aria-label="朗读语速"';
const assertBeforeSpeed = (markup, text) => {
  const textIndex = markup.indexOf(text);
  const speedIndex = markup.indexOf(speedLabel);
  assert.ok(textIndex >= 0 && speedIndex > textIndex, `${text} must precede speed in the toolbar DOM.`);
};

const off = render();
assert.ok(!off.includes(speedLabel), 'Voice mode off hides speed.');
assert.ok(!off.includes('role="status"'), 'Idle voice mode off does not leave playback status behind.');
assert.match(plainText(off), /导出.*全部历史.*语音对话/, 'Existing export/history/mode controls remain.');

const ready = render({ voiceDialogueEnabled: true });
assert.ok(ready.includes(speedLabel), 'Voice mode on reveals speed.');
assertBeforeSpeed(ready, '语音已就绪');
assert.equal((ready.match(/<option /g) ?? []).length, 4, 'All four existing playback speeds remain.');

for (const fixture of [
  { props: { ttsStatus: 'playing', canPauseTts: true }, labels: ['正在播报 7/14', '暂停', '停止朗读'] },
  { props: { ttsStatus: 'paused', canResumeTts: true }, labels: ['朗读已暂停', '继续', '停止朗读'] },
  { props: { ttsStatus: 'synthesizing' }, labels: ['正在合成语音…', '停止朗读'] },
  { props: { ttsStatus: 'waiting-text' }, labels: ['等待 AI 后续文字…', '停止朗读'] },
  { props: { ttsStatus: 'error', ttsError: '测试网络错误', canResumeTts: true, canRetryTts: true }, labels: ['测试网络错误', '从失败句继续', '停止朗读'] },
]) {
  const markup = render({ voiceDialogueEnabled: true, ...fixture.props });
  for (const label of fixture.labels) assertBeforeSpeed(markup, label);
  assert.ok(markup.indexOf(speedLabel) < markup.indexOf('aria-pressed="true"'), 'Speed stays left of the voice mode toggle.');
  if (fixture.props.canRetryTts) assert.equal((markup.match(/>继续<\/button>/g) ?? []).length, 0,
    'Errors do not show a duplicate resume action.');
}

const replay = render({ ttsStatus: 'playing', canPauseTts: true });
assert.ok(!replay.includes(speedLabel), 'Single-message replay does not reveal speed while voice mode is off.');
assert.match(plainText(replay), /正在播报 7\/14.*暂停.*停止朗读/, 'Single-message replay retains playback controls even with voice mode off.');

const recordingExpression = nodes.find(node => ts.isJsxExpression(node)
  && node.expression && ts.isBinaryExpression(node.expression)
  && node.expression.left.getText(sourceFile) === "voiceInputPhase !== 'idle'");
assert.ok(recordingExpression, 'Recording lifecycle has an independent source expression.');
assert.ok(recordingExpression.end <= toolbar.pos || recordingExpression.pos >= toolbar.end,
  'Recording status stays separate from the playback toolbar.');
const RecordingStatus = compileJsx(recordingExpression.expression.getText(sourceFile));
const recording = renderToStaticMarkup(React.createElement(RecordingStatus, {
  ...baseProps, voiceInputPhase: 'recording', isRecording: true,
}));
assert.match(plainText(recording), /正在录音.*剩余 4:59.*静音 30 秒自动停止/,
  'Recording status remains visible independently of automatic voice dialogue.');
const finishing = renderToStaticMarkup(React.createElement(RecordingStatus, {
  ...baseProps, voiceInputPhase: 'finishing', isAwaitingAsrCompletion: true,
}));
assert.match(plainText(finishing), /正在整理最后一句…/, 'The independent ASR finishing notice is preserved.');

console.log('Voice toolbar source-render regression verified: 10 UI states; no microphone or provider requests.');

export const renderVoiceToolbarFixture = render;

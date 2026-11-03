import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';

const source = await readFile(new URL('../src/components/ZhibiYiyan.tsx', import.meta.url), 'utf8');
const ast = ts.createSourceFile('ZhibiYiyan.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
const declarations = new Map();
const visit = node => {
  if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer) {
    declarations.set(node.name.text, node.initializer.getText(ast));
  }
  ts.forEachChild(node, visit);
};
visit(ast);
const executable = ts.transpileModule(
  `const resolveModelConfigId = ${declarations.get('resolveModelConfigId')}; globalThis.send = ${declarations.get('handleSendMessage')};`,
  { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } },
).outputText;

const fallback = 'event: model_fallback\ndata: {"provider":"deepseek","model":"deepseek-chat"}\n\n';
const event = (type, content) => `event: ${type}\ndata: ${content}\n\n`;

async function run(chunks, { voice = true, headerId = null, cancelVoiceAt = -1 } = {}) {
  let chats = [
    { id: 'local-chat', messages: [], active: true },
    { id: 'other-chat', messages: [{ id: 'other-ai', sender: 'ai', content: 'keep me' }], active: false },
  ];
  const snapshots = [];
  const audio = [];
  let pendingAudio = [];
  let streamId = 0;
  let readIndex = 0;
  let cancelCount = 0;
  const ref = value => ({ current: value });
  const context = {
    console, Date, AbortController, TextDecoder,
    inputValue: 'hello', isSending: false, isLoading: false,
    selectedAgent: { id: '2077567890123456789', name: 'Original model' },
    currentConversationId: headerId ? null : 'local-chat', isAllHistoryEnabled: true,
    API_CONFIG: { BASE_URL: '' },
    getCurrentChat: () => chats[0],
    getCurrentMessages: () => chats[0].messages,
    setChatList: update => {
      chats = update(chats);
      snapshots.push(structuredClone(chats));
    },
    getDisplayName: agent => agent.name,
    setVoiceInputNotice() {}, clearVoiceInputNotice() {}, stopVoiceInput() {}, setInputValue() {},
    setIsSending(value) { context.isSending = value; },
    setIsLoading(value) { context.isLoading = value; },
    setCurrentConversationId(value) { context.currentConversationId = value; },
    scrollToBottom() {}, normalizeMarkdownContent: text => text,
    activeResponseControllerRef: ref(null), activeResponseReaderRef: ref(null),
    responseRunIdRef: ref(0), ttsContextVersionRef: ref(0),
    voiceDialogueEnabledRef: ref(voice), firstReplyShownRef: ref(false),
    currentAiMessageRef: ref(''), currentThoughtContentRef: ref(''),
    cancelTts() {
      context.ttsContextVersionRef.current += 1;
      pendingAudio = [];
      audio.push({ type: 'stop' });
    },
    startTtsStream() {
      audio.push({ type: 'start', streamId: ++streamId });
      return streamId;
    },
    enqueueTtsStream(id, content) {
      audio.push({ type: 'enqueue', streamId: id, content });
      pendingAudio.push(content);
    },
    finishTtsStream(id) { audio.push({ type: 'finish', streamId: id }); },
    authSession: { capture: () => ({}), assertCurrent() {} },
    isSessionExpiredCode: () => false,
    AuthSessionExpiredError: class extends Error {},
    authenticatedRawRequest: async () => ({
      ok: true,
      headers: { get: () => headerId },
      body: { getReader: () => ({
        read: async () => {
          if (readIndex === cancelVoiceAt) context.cancelTts();
          return readIndex < chunks.length
            ? { done: false, value: new TextEncoder().encode(chunks[readIndex++]) }
            : { done: true };
        },
        cancel: async () => { cancelCount += 1; },
      }) },
    }),
  };
  vm.createContext(context);
  vm.runInContext(executable, context);
  await context.send();
  assert.equal(chats[1].messages[0].content, 'keep me', 'a different chat must not be modified');
  assert.equal(context.selectedAgent.name, 'Original model', 'fallback must not persistently change model selection');
  assert.equal(context.isSending, false);
  assert.equal(context.isLoading, false);
  return { context, message: chats[0].messages.find(message => message.sender === 'ai'), snapshots, audio, pendingAudio, readIndex, cancelCount };
}

{
  const result = await run([fallback, event('reply', 'New answer')]);
  assert.equal(result.message.content, 'New answer');
  assert.equal(result.message.agentName, 'deepseek-chat');
  assert.deepEqual(result.pendingAudio, ['New answer']);
}

{
  const result = await run([
    event('thought', 'Old reasoning') + event('reply', 'Old partial answer'),
    fallback.slice(0, 30), fallback.slice(30),
    event('thought', 'New reasoning') + event('reply', 'New answer'),
    event('aiConversationId', '2077567890123456789'),
  ], { headerId: '2077567890123456789' });
  assert.equal(result.message.content, '<thought>New reasoning</thought>\n\nNew answer');
  assert.equal(result.context.currentAiMessageRef.current, 'New answer');
  assert.equal(result.context.currentThoughtContentRef.current, 'New reasoning');
  assert.deepEqual(result.pendingAudio, ['New answer']);
  assert.equal(result.audio.filter(item => item.type === 'start').length, 2);
  assert.ok(result.snapshots.some(chats => chats[0].messages.some(message => message.sender === 'ai'
    && message.agentName === 'deepseek-chat' && message.content === '')), 'clear the old answer immediately when switching');
}

{
  const result = await run([event('reply', 'Discard me'), fallback.trimEnd()]);
  assert.equal(result.message.content, '', 'consume a fallback control payload at EOF without a final newline');
  assert.equal(result.context.currentAiMessageRef.current, '');
  assert.deepEqual(result.pendingAudio, []);
}

{
  const result = await run([event('reply', 'Old answer'), fallback, event('reply', 'New answer')], { cancelVoiceAt: 1 });
  assert.equal(result.message.content, 'New answer');
  assert.equal(result.audio.filter(item => item.type === 'start').length, 1, 'manual speech cancellation must remain in force');
  assert.deepEqual(result.pendingAudio, []);
}

{
  const result = await run(['event: model_fallback\ndata: malformed\n\n', event('reply', 'New answer')], { voice: false });
  assert.equal(result.message.content, 'New answer');
  assert.equal(result.message.agentName, 'DeepSeek');
  assert.equal(result.audio.filter(item => item.type === 'start').length, 0);
}

const safeError = '模型服务暂时不可用，请稍后重试。';
const assertTerminalError = result => {
  assert.equal(result.message.content, `发生错误: ${safeError}`);
  assert.equal(result.context.currentAiMessageRef.current, '');
  assert.equal(result.context.currentThoughtContentRef.current, '');
  assert.equal(result.context.firstReplyShownRef.current, false);
  assert.deepEqual(result.pendingAudio, []);
  assert.equal(result.audio.filter(item => item.type === 'finish').length, 0, 'a terminal error must never finish speech as a successful reply');
  assert.equal(result.cancelCount, 1, 'terminate the reader when the server reports failure');
};

{
  const result = await run([
    event('thought', 'Old reasoning') + event('reply', 'Old partial answer'),
    fallback,
    event('thought', 'Fallback reasoning') + event('reply', 'Fallback partial answer'),
    event('error_message', safeError),
    event('reply', 'Must not be processed after failure'),
  ], { headerId: '2077567890123456789' });
  assertTerminalError(result);
  assert.equal(result.readIndex, 4);
}

{
  const result = await run([event('error_message', safeError)]);
  assertTerminalError(result);
}

{
  const result = await run([
    event('thought', 'Discard reasoning') + event('reply', 'Discard answer'),
    event('error_message', safeError).trimEnd(),
  ]);
  assertTerminalError(result);
}

{
  const result = await run(['event: error_message\ndata:']);
  assertTerminalError(result);
}

console.log('PASS: fallback replacement, chunk splitting, new conversation ID, EOF control data, speech isolation, unchanged model selection, and terminal errors before/after fallback without partial output');

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const sources = new Map();
for (const relativePath of [
  'src/services/http/errors.ts',
  'src/services/websocketService.ts',
  'src/providers/AuthProvider.tsx',
  'src/components/Login.tsx',
  'src/components/QRCodeModal.tsx',
  'src/services/aiConversationApi.ts',
  'src/services/digitalHumanApi.ts',
  'src/components/ApplicationTemplates.tsx',
  'src/components/BottomPanel.tsx',
  'src/components/Canvas.tsx',
  'src/components/ThumbnailGenerator.tsx',
  'src/components/UserFeedbackModal.tsx',
  'src/components/VideoAccount.tsx',
  'src/components/PublishVideo.tsx',
  'src/components/ZhiheYishen.tsx',
  'vite.config.ts',
]) {
  sources.set(relativePath, await readFile(path.join(root, relativePath), 'utf8'));
}

const all = [...sources.values()].join('\n');
for (const forbidden of [
  'raw?: unknown',
  'this.raw =',
  'debug: true',
  "this.log('发送消息:', messageWithTimestamp)",
  "this.log('收到消息:', message)",
  "this.log('解析消息失败:', error, event.data)",
  "console.log('传入的URL:', url)",
  "console.log('清理后的URL:', cleanUrl)",
  'originalRequest: request',
  "console.log('应用模板文案:', template.script)",
  "console.log('数字人元素properties:', digitalHumanElement.properties)",
  "console.log('发送合成请求:', composeRequest)",
  "console.log('ASR Result:', result)",
  'console.log(accountForm)',
]) {
  assert.equal(all.includes(forbidden), false, `sensitive logging pattern remains: ${forbidden}`);
}

assert.match(
  sources.get('src/services/websocketService.ts'),
  /console\.log\('\[WebSocket\]', message\)/,
  'WebSocket diagnostics must log only the fixed event label',
);
assert.match(
  sources.get('src/services/digitalHumanApi.ts'),
  /数字人合成请求，预计时长/,
  'digital-human composition diagnostics must be metadata-only',
);
assert.match(
  sources.get('src/components/UserFeedbackModal.tsx'),
  /problemDescription\.length/,
  'feedback diagnostics must log only content length',
);
assert.match(
  sources.get('vite.config.ts'),
  /mode === 'production'[\s\S]*drop: \['console' as const, 'debugger' as const\]/,
  'production builds must remove browser console and debugger statements',
);
assert.match(
  sources.get('vite.config.ts'),
  /sourcemap: mode === 'production' \? false/,
  'production source maps must not be published',
);

console.log('Sensitive logging contract checks passed.');

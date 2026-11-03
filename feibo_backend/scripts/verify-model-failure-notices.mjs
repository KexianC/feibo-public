import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/services/modelFailureNoticeService.ts', import.meta.url), 'utf8');
const calls = [];
const page = {
  records: [{ id: '2077567890123456789', read: false }],
  total: 1, current: 2, size: 10, unreadCount: 1,
};
globalThis.__noticeRequest = async (url, options) => {
  calls.push({ url, ...options });
  return { data: url.endsWith('/unread-count') ? 1 : page };
};
const output = ts.transpileModule(source.replace(
  "import { authenticatedRequest } from './auth';",
  'const authenticatedRequest = globalThis.__noticeRequest;',
), { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
try {
  const { modelFailureNoticeService: service } = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`);
  assert.equal(await service.list(2, 10), page);
  assert.equal(await service.unreadCount(), 1);
  await service.markRead(page.records[0].id);
  await service.markAllRead();
  assert.deepEqual(calls, [
    { url: '/api/v1/admin/model-failure-notices?pageNum=2&pageSize=10', method: 'GET' },
    { url: '/api/v1/admin/model-failure-notices/unread-count', method: 'GET' },
    { url: '/api/v1/admin/model-failure-notices/2077567890123456789/read', method: 'POST' },
    { url: '/api/v1/admin/model-failure-notices/read-all', method: 'POST' },
  ]);
  await assert.rejects(service.markRead(2077567890123456789));
  await assert.rejects(service.markRead('../read-all'));
  assert.equal(calls.length, 4, 'invalid IDs must not issue requests');
} finally {
  delete globalThis.__noticeRequest;
}
console.log('PASS: notification pagination, unread count, per-notice/read-all actions, and lossless ID handling');

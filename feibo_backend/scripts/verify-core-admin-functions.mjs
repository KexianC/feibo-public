import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
let result; const calls = [];
const authenticatedRequest = async (url, options = {}) => { calls.push({ url, options }); return { code: 200, data: result }; };
async function load(name) {
  const source = await readFile(new URL('../src/services/' + name + '.ts', import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', compiled)(specifier => {
    if (specifier !== './auth') throw new Error('Unexpected dependency: ' + specifier);
    return { authenticatedRequest };
  }, module, module.exports);
  return module.exports.default;
}
const categories = await load('copywritingCategoryService');
const templates = await load('templateService');
const feedback = await load('feedbackService');
const users = await load('userService');
const copywriting = await load('copywritingApprovalService');
let passed = 0;
async function check(name, action) { await action(); passed++; console.log('PASS ' + name); }
const body = () => JSON.parse(calls.at(-1).options.body);
await check('category create maps name and enabled status and requires a real id', async () => {
  result = { id: 5, name: 'Science', status: false };
  const item = await categories.create({ title: ' Science ', isShow: 1 });
  assert.deepEqual(body(), { name: 'Science', status: false }); assert.equal(item.isShow, 1); assert.equal(item.id, '5');
  result = true; await assert.rejects(categories.create({ title: 'Science' }), /未确认/);
});
await check('category edit and delete reject boolean false', async () => {
  result = false; await assert.rejects(categories.update({ id: '5', title: 'Science', isShow: 0 }), /未更新/);
  assert.deepEqual(body(), { id: '5', name: 'Science', status: true }); await assert.rejects(categories.delete('5'), /未删除/);
});
await check('category search and response use persisted fields', async () => {
  result = { records: [{ id: 5, name: 'Science', status: true }], total: 1 };
  const page = await categories.getPage({ page: 2, size: 20, title: ' Science ' });
  const query = new URL(calls.at(-1).url, 'http://local').searchParams;
  assert.equal(query.get('pageNum'), '2'); assert.equal(query.get('title'), 'Science'); assert.equal(page.records[0].isShow, 0);
});
await check('template content writes body and requires actual created record', async () => {
  result = { id: 6, title: 'Template', body: '{"active":0}' };
  const item = await templates.create({ title: 'Template', content: '{"active":0}' });
  assert.deepEqual(body(), { title: 'Template', body: '{"active":0}' }); assert.equal(item.content, '{"active":0}');
  result = false; await assert.rejects(templates.update({ id: '6', title: 'Template', content: '{}' }), /未更新/); await assert.rejects(templates.delete('6'), /未删除/);
});
await check('feedback filters preserve int64 id and every selected filter', async () => {
  result = { records: [], total: 0, size: 10, current: 1, pages: 0 };
  await feedback.getFeedbackPage({ pageNum: 1, pageSize: 10, userId: '9007199254740993', type: 2, status: 0, priority: 0, startDate: '2026-09-09', endDate: '2026-09-09' });
  const query = new URL(calls.at(-1).url, 'http://local').searchParams;
  for (const [key, value] of Object.entries({ userId: '9007199254740993', type: '2', status: '0', priority: '0', startDate: '2026-09-09', endDate: '2026-09-09' })) assert.equal(query.get(key), value);
});
await check('feedback actions send expected version and reject unconfirmed updates', async () => {
  result = { id: 1, version: 3 }; await feedback.replyFeedback('1', 'response', 2, 3);
  assert.deepEqual(body(), { reply: 'response', version: 2, priority: 3 });
  await feedback.resolveFeedback('1', 2); assert.deepEqual(body(), { version: 2 });
  result = { id: 1, version: 2 }; await assert.rejects(feedback.closeFeedback('1', 2), /未确认/);
  result = true; await assert.rejects(feedback.replyFeedback('1', 'response', 2), /未确认/);
});
await check('copywriting deletion uses supplied database id and checks result', async () => {
  result = false; await assert.rejects(copywriting.deleteById('17'), /未删除/); assert.equal(calls.at(-1).url, '/api/admin/function/copywriting/17');
  result = true; await copywriting.deleteById('17');
});
await check('bulk import returns skipped-row errors and rejects missing response', async () => {
  const file = new File(['fixture'], 'users.xlsx'); result = ['第2行手机号已存在'];
  assert.deepEqual(await users.createUsersByExcel(file), result); assert.equal(calls.at(-1).options.body.get('file').name, 'users.xlsx');
  result = undefined; await assert.rejects(users.createUsersByExcel(file), /不完整/);
});
console.log('Core admin service checks passed: ' + passed);

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const calls = [];
let response = { data: true };
const auth = { authenticatedRequest: async (url, options) => {
  calls.push({ url, ...options });
  return response;
} };
const modules = new Map();
function load(name) {
  if (name === './auth') return auth;
  if (modules.has(name)) return modules.get(name);
  const source = readFileSync(new URL(`../src/services/${name.replace('./', '')}.ts`, import.meta.url), 'utf8');
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const exports = {};
  new Function('require', 'exports', output)(load, exports);
  modules.set(name, exports);
  return exports;
}

const packages = load('./packageService').default;
const plans = load('./planService').default;
const { filterEntitlementProducts } = load('./packageContracts');
const input = { name: ' 合成额度 ', description: '100 分钟', price: 29.9, value: 6000, status: 0, aiFeatureTemplateId: '1960611825770013186' };
await packages.createPackage({ ...input, duration: 30, features: ['unused'], isDisplay: 1 });
let payload = JSON.parse(calls.at(-1).body);
assert.equal(payload.value, 6000, 'An unrelated duration must never overwrite the purchased amount');
assert.equal(payload.name, '合成额度');
assert.equal(payload.aiFeatureTemplateId, input.aiFeatureTemplateId, 'Keep large IDs as strings');
assert.equal(payload.isActive, 0);
assert.ok(!('duration' in payload) && !('features' in payload) && !('isDisplay' in payload));
await packages.updatePackage({ ...input, id: '42', value: 0, status: 1 });
payload = JSON.parse(calls.at(-1).body);
assert.equal(payload.id, '42');
assert.equal(payload.value, 0);
assert.equal(payload.isActive, 1);
await assert.rejects(packages.createPackage({ ...input, value: undefined, duration: 30 }), /算力额度/);
await assert.rejects(packages.createPackage({ ...input, value: -1 }), /算力额度/);
for (const malformed of [false, null, undefined, 'false', 'true', 1, {}]) {
  response = { data: malformed };
  await assert.rejects(packages.createPackage(input), /未创建/, 'Create requires the literal backend boolean true');
  await assert.rejects(packages.updatePackage({ ...input, id: '42' }), /未更新/, 'Update requires the literal backend boolean true');
}
response = { data: false };
await assert.rejects(packages.updatePackage({ ...input, id: '42' }), /未更新/);
await assert.rejects(packages.deletePackage('42'), /未删除/);
await assert.rejects(plans.createPlan({ name: '月度会员', price: 30, duration: 30, status: 0 }), /未创建/);
await assert.rejects(plans.updateStatus('42', 1), /未更新/);
response = { data: { records: [], total: 0, current: 1, size: 10, pages: 0 } };
await packages.getPackagesPage({ page: 2, size: 10, name: '视频', status: 0, minPrice: 0, maxPrice: 0 });
const query = new URL(calls.at(-1).url, 'http://test.local').searchParams;
assert.equal(query.get('pageNum'), '2');
assert.equal(query.get('status'), '0');
assert.equal(query.get('maxPrice'), '0', 'Zero-priced products must remain filterable');
await plans.getPlansPage({ pageNum: 1, pageSize: 10, packageName: ' 月度 ' });
assert.equal(new URL(calls.at(-1).url, 'http://test.local').searchParams.get('planName'), '月度');
assert.ok(!calls.at(-1).url.includes('packageName'));
response = { data: true };
await plans.updatePlan('42', { name: '会员', price: 10, duration: -1, status: 1 });
assert.deepEqual(JSON.parse(calls.at(-1).body), { id: '42', name: '会员', price: 10, durationDays: -1, isActive: false });

const products = [
  { productId: '1', name: '免费会员', price: 0, active: true },
  { productId: '2', name: '视频会员', price: 10, active: false },
  { productId: '3', name: '视频充值包', price: 20, active: true },
];
assert.deepEqual(filterEntitlementProducts(products, { minPrice: 0, maxPrice: 0 }), [products[0]]);
assert.deepEqual(filterEntitlementProducts(products, { keyword: '视频', active: false, minPrice: 10, maxPrice: 10 }), [products[1]]);
assert.deepEqual(filterEntitlementProducts(products, {}), products, 'Reset must restore the complete product list');
assert.equal(products.length, 3, 'Filtering cannot mutate the cached server list');
console.log('Package regressions passed: amount/validity separation, zero price, status, request fields, failed writes, and filters.');

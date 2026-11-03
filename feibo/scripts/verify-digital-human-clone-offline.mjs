// Offline only: the HTML runs in a VM with a stubbed fetch, never a network client.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const html = readFileSync(new URL('../digital-human-clone-test.html', import.meta.url), 'utf8');
const source = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const previewInput = html.match(/<input\b[^>]*id="previewOnly"[^>]*>/)[0];
const previewByDefault = /\bchecked\b/.test(previewInput);
const fixedTime = 1789720618123;
const fixture = { appKey: 'offline-key', appSecret: 'offline-secret-测试' };
const digest = text => createHash('md5').update(text, 'utf8').digest('hex');

function harness(credentials = fixture, protocol = 'file:') {
    const elements = new Map();
    const requests = [];
    let loadedScripts = 0;
    let ready;
    const element = id => {
        if (!elements.has(id)) {
            elements.set(id, {
                checked: id === 'previewOnly' && previewByDefault, textContent: '', value: '', innerHTML: '',
                classList: { add() {}, remove() {} },
                addEventListener() {}
            });
        }
        return elements.get(id);
    };
    const window = { location: { protocol } };
    const sandbox = vm.createContext({
        window, TextEncoder,
        Date: class extends Date {
            constructor(...args) { super(...(args.length ? args : [fixedTime])); }
            static now() { return fixedTime; }
        },
        document: {
            getElementById: element,
            addEventListener(event, listener) { if (event === 'DOMContentLoaded') ready = listener; },
            createElement() { return { remove() {} }; },
            head: { appendChild(script) {
                loadedScripts++;
                assert.equal(script.src, '../.local-config/digital-human-clone.credentials.js');
                if (credentials === null) script.onerror();
                else {
                    window.DIGITAL_HUMAN_CLONE_AUTH = credentials;
                    script.onload();
                }
            } }
        },
        fetch: async (url, options) => {
            requests.push({ url, ...options });
            return { ok: true, json: async () => ({ ok: true, data: 'offline-task' }) };
        }
    });
    vm.runInContext(source, sandbox, { filename: 'digital-human-clone-test.html' });
    return { sandbox, requests, element, ready, loadedScripts: () => loadedScripts };
}

assert.equal(previewByDefault, false, 'The page must default to real submission');
const ctx = harness();
ctx.ready();
await Promise.resolve();
assert.equal(ctx.requests.length, 0, 'Page initialization must not call the provider');
assert.match(ctx.element('authStatus').textContent, /已加载/);
const headers = await ctx.sandbox.getAuthHeaders();
assert.equal(headers.appKey, fixture.appKey);
assert.equal(headers.timestamp, '1789720618');
assert.equal(headers.signature, digest(`${fixture.appKey}+${fixture.appSecret}+${headers.timestamp}`));
assert.equal('appSecret' in headers, false);
for (const input of ['', 'abc', '测试 UTF-8', 'a'.repeat(55), 'a'.repeat(56), 'a'.repeat(64), 'a'.repeat(1000)]) {
    assert.equal(ctx.sandbox.md5(input), digest(input), 'MD5 must match the Java UTF-8 contract');
}

const form = new Map([['videoUrl', 'https://example.invalid/training.mp4'], ['avatarName', '离线验证']]);
ctx.element('previewOnly').checked = true;
await ctx.sandbox.submitCloneTask(form);
await ctx.sandbox.queryTaskStatus('offline /?任务');
assert.equal(ctx.requests.length, 0, 'Preview submission and query must send zero requests');
assert.equal(ctx.element('taskId').value, '', 'Preview must not fabricate a provider task');
const output = ctx.element('responseContent').textContent;
assert.match(output, /未发送 API 请求/);
assert.equal(output.includes(fixture.appKey), false);
assert.equal(output.includes(fixture.appSecret), false);
assert.equal(output.includes(headers.signature), false);

// Explicit mode is exercised only with the in-memory stub above.
ctx.element('previewOnly').checked = false;
await ctx.sandbox.submitCloneTask(form);
await ctx.sandbox.queryTaskStatus('offline /?任务');
assert.equal(ctx.requests.length, 2);
assert.equal(ctx.requests[0].url, 'https://openman.weta365.com/metaman/open/avatar/online/clone');
assert.equal(ctx.requests[0].method, 'POST');
assert.equal(ctx.requests[0].headers.signature, headers.signature);
assert.deepEqual(JSON.parse(ctx.requests[0].body), {
    avatarType: 'fixed_avatar', videoUrl: form.get('videoUrl'), avatarName: form.get('avatarName'),
    videoBgMatting: true, needAuthorized: false
});
assert.equal(ctx.requests[1].url, `https://openman.weta365.com/metaman/open/avatar/online/clone/status/${encodeURIComponent('offline /?任务')}`);
assert.equal(ctx.requests[1].method, 'GET');
assert.equal(ctx.element('taskId').value, 'offline-task');
assert.equal(ctx.loadedScripts(), 1);

for (const credentials of [null, {}, { appKey: '', appSecret: 'secret' }, { appKey: 'key', appSecret: '  ' }]) {
    const invalid = harness(credentials);
    invalid.element('previewOnly').checked = false;
    await invalid.sandbox.submitCloneTask(form);
    await invalid.sandbox.queryTaskStatus('task');
    assert.equal(invalid.requests.length, 0, 'Missing credentials must fail before fetch');
    assert.match(invalid.element('responseContent').textContent, /已阻止发送请求/);
}
const hosted = harness(fixture, 'https:');
await hosted.sandbox.submitCloneTask(form);
assert.equal(hosted.requests.length, 0);
assert.equal(hosted.loadedScripts(), 0, 'A hosted page must not load local credentials');

if (process.argv.includes('--local-config')) {
    const configSandbox = { window: {} };
    vm.runInNewContext(readFileSync(new URL('../../.local-config/digital-human-clone.credentials.js', import.meta.url), 'utf8'), configSandbox);
    const credentials = configSandbox.window.DIGITAL_HUMAN_CLONE_AUTH;
    const local = harness(credentials);
    const signed = await local.sandbox.getAuthHeaders();
    assert.ok(signed.appKey.trim(), 'Local appKey must be configured');
    assert.ok(credentials.appSecret.trim(), 'Local appSecret must be configured');
    assert.equal(signed.signature === digest(`${credentials.appKey.trim()}+${credentials.appSecret.trim()}+${signed.timestamp}`), true,
        'Local credentials must produce the expected signature');
    local.element('previewOnly').checked = true;
    await local.sandbox.submitCloneTask(form);
    assert.equal(local.requests.length, 0);
    console.log('Local credential loading and signature verified offline (values hidden).');
}
console.log('PASS: auth headers, UTF-8 MD5, preview, submit/query stubs, missing-config guards. External API calls: 0.');

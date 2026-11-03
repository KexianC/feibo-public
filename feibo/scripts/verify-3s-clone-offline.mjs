// Offline regression only. The page is isolated in a VM whose fetch never uses the network.
import assert from 'node:assert/strict';
import { File } from 'node:buffer';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const html = readFileSync(new URL('../3s-clone-test.html', import.meta.url), 'utf8');
const source = [...html.matchAll(/<script\b(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)]
    .map(match => match[1]).join('\n');
const ids = new Set([...html.matchAll(/\bid=["']([^"']+)["']/g)].map(match => match[1]));
const fixedTime = 1789720618123;
const fixture = { appKey: 'offline-3s-key', appSecret: 'offline-3s-secret-测试🎙️' };
const digest = value => createHash('md5').update(value, 'utf8').digest('hex');
const signatureFor = config => digest(`${config.appKey.trim()}+${config.appSecret.trim()}+1789720618`);

function createElement(tag = 'div') {
    let text = '';
    let markup = '';
    const classes = new Set();
    const listeners = new Map();
    const element = {
        tagName: tag.toUpperCase(), value: '', checked: false, disabled: false,
        files: [], style: {}, children: [], listeners,
        get textContent() { return text + this.children.map(child => child.textContent ?? '').join(''); },
        set textContent(value) { text = String(value ?? ''); markup = ''; this.children = []; },
        get innerHTML() { return markup; },
        set innerHTML(value) { markup = String(value ?? ''); text = markup.replace(/<[^>]*>/g, ''); this.children = []; },
        get className() { return [...classes].join(' '); },
        set className(value) { classes.clear(); String(value).split(/\s+/).filter(Boolean).forEach(item => classes.add(item)); },
        classList: {
            add(...names) { names.forEach(name => classes.add(name)); },
            remove(...names) { names.forEach(name => classes.delete(name)); },
            contains(name) { return classes.has(name); }
        },
        addEventListener(name, listener) { listeners.set(name, listener); },
        appendChild(child) { this.children.push(child); return child; },
        replaceChildren(...children) { text = ''; markup = ''; this.children = children; },
        setAttribute(name, value) { this[name] = String(value); },
        removeAttribute(name) { delete this[name]; },
        remove() {}, focus() {}
    };
    return element;
}

function harness(credentials = fixture, protocol = 'file:') {
    const elements = new Map([...ids].map(id => [id, createElement()]));
    const requests = [];
    const messages = [];
    const responses = [];
    const readyListeners = [];
    let loadedScripts = 0;
    for (const match of html.matchAll(/<input\b[^>]*>/gi)) {
        const id = match[0].match(/\bid=["']([^"']+)["']/)?.[1];
        if (id && elements.has(id)) elements.get(id).checked = /\bchecked\b/.test(match[0]);
    }
    const waiting = html.match(/<div\b[^>]*id="responseContent"[^>]*>([\s\S]*?)<\/div>/)?.[1];
    if (waiting) elements.get('responseContent').innerHTML = waiting;
    const element = id => elements.get(id) ?? null;
    const window = { location: { protocol } };
    const sandbox = vm.createContext({
        window, TextEncoder, FormData, File, URL,
        Date: class extends Date {
            constructor(...args) { super(...(args.length ? args : [fixedTime])); }
            static now() { return fixedTime; }
        },
        console: Object.fromEntries(['log', 'info', 'warn', 'error'].map(level => [level,
            (...args) => messages.push(args.map(value => typeof value === 'string' ? value : JSON.stringify(value)).join(' '))])),
        alert: message => messages.push(String(message)),
        document: {
            getElementById: element,
            addEventListener(event, listener) { if (event === 'DOMContentLoaded') readyListeners.push(listener); },
            createElement,
            head: { appendChild(script) {
                loadedScripts++;
                assert.equal(script.src, '../.local-config/digital-human-clone.credentials.js');
                if (credentials === null) script.onerror?.(new Error('Offline missing config fixture'));
                else {
                    window.DIGITAL_HUMAN_CLONE_AUTH = credentials;
                    script.onload?.();
                }
            } }
        },
        fetch: async (url, options) => {
            requests.push({ url, ...options });
            const response = responses.shift() ?? { code: 0, speaker: 'offline-speaker' };
            if (typeof response === 'function') return await response();
            if (response instanceof Error) throw response;
            return {
                ok: response.httpStatus === undefined || response.httpStatus < 400,
                status: response.httpStatus ?? 200,
                statusText: response.httpStatus ? 'Offline HTTP fixture' : 'OK',
                json: async () => response.body ?? response,
                text: async () => JSON.stringify(response.body ?? response)
            };
        }
    });
    vm.runInContext(source, sandbox, { filename: '3s-clone-test.html' });
    const output = () => [...elements.values()].map(node => `${node.textContent}\n${node.innerHTML}`).join('\n')
        + '\n' + messages.join('\n');
    return {
        sandbox, element, requests, responses, output, loadedScripts: () => loadedScripts,
        async ready() { for (const listener of readyListeners) await listener(); await Promise.resolve(); },
        async submit() { await sandbox.submitCloneTask({ preventDefault() {} }); },
        audioUrl() { element('wavUri').value = 'https://example.invalid/offline-training.wav'; }
    };
}

function assertRedacted(context, config = fixture) {
    // Boolean assertions prevent assertion diagnostics from ever printing credential values.
    const output = context.output();
    for (const secret of [config.appKey.trim(), config.appSecret.trim(), signatureFor(config)]) {
        assert.equal(output.includes(secret), false, 'Page output must redact authentication values');
    }
}

function assertSettled(context) {
    assert.equal(context.element('submitButton').disabled, false, 'Submit must be re-enabled after completion');
    assert.equal(context.element('loading').classList.contains('show'), false, 'Loading must finish');
    assert.equal(context.element('responseContent').textContent.includes('等待API调用'), false,
        'A completed attempt must replace the initial waiting text');
}

// Start with a genuine cryptographic regression, so the unmodified page fails for a functional reason.
const vectors = harness();
for (const input of ['', 'a', 'abc', 'message digest', 'abcdefghijklmnopqrstuvwxyz',
    'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789',
    '1234567890'.repeat(8), '测试 UTF-8', '🎙️ UTF-8\r\n测试', '\ud800',
    ...[55, 56, 63, 64, 65, 119, 120, 127, 128, 1000].map(length => 'a'.repeat(length))]) {
    assert.equal(vectors.sandbox.md5(input), digest(input), 'MD5 must match standard UTF-8 and block-boundary vectors');
}
for (const id of ['authStatus', 'statusResult', 'submitButton', 'previewOnly']) {
    assert.equal(ids.has(id), true, `Required DOM element is missing: ${id}`);
}
assert.equal(vectors.element('previewOnly').checked, false, 'The page must default to real submission');
await vectors.ready();
assert.equal(vectors.requests.length, 0, 'Page initialization must never call the provider');
const config = await vectors.sandbox.loadAuthConfig();
assert.match(vectors.element('authStatus').textContent, /已加载/);
assert.equal(vectors.sandbox.generateSignature('1789720618', config), signatureFor(fixture));

// File upload contract: multipart keys and string booleans must not drift to the avatar API headers.
const upload = harness();
const audioFile = new File(['offline fixture bytes'], 'offline-training.wav', { type: 'audio/wav' });
upload.element('audioFile').files = [audioFile];
upload.element('text').value = '  离线语音测试文本  ';
upload.element('willStripSilence').checked = true;
await upload.submit();
assert.equal(upload.requests.length, 1);
const fileRequest = upload.requests[0];
assert.equal(fileRequest.url, 'https://open.mobvoi.com/clone');
assert.equal(fileRequest.method, 'POST');
assert.equal(fileRequest.body instanceof FormData, true);
assert.equal(Object.keys(fileRequest.headers ?? {}).some(key => key.toLowerCase() === 'content-type'), false,
    'Browser must supply the multipart boundary');
assert.deepEqual([...fileRequest.body.keys()].sort(),
    ['appkey', 'signature', 'timestamp', 'source', 'file', 'text', 'willReduceNoise', 'willStripSilence'].sort());
assert.equal(fileRequest.body.get('appkey'), fixture.appKey);
assert.equal(fileRequest.body.get('signature'), signatureFor(fixture));
assert.equal(fileRequest.body.get('timestamp'), '1789720618');
assert.equal(fileRequest.body.get('source'), 'galaxy_fastv7');
assert.equal(fileRequest.body.get('file'), audioFile);
assert.equal(fileRequest.body.get('text'), '离线语音测试文本');
assert.equal(fileRequest.body.get('willReduceNoise'), 'false');
assert.equal(fileRequest.body.get('willStripSilence'), 'true');
assert.match(upload.element('statusResult').textContent, /offline-speaker/);
assert.doesNotMatch(upload.output(), /Cannot (?:read|set)|TypeError/);
assertSettled(upload);
assertRedacted(upload);

// URL upload is checked independently and must omit the unused file/text fields.
const remote = harness();
remote.audioUrl();
remote.element('text').value = '  ';
remote.element('willReduceNoise').checked = true;
await remote.submit();
assert.equal(remote.requests.length, 1);
const urlRequest = remote.requests[0];
assert.equal(urlRequest.url, 'https://open.mobvoi.com/clone');
assert.equal(urlRequest.method, 'POST');
assert.equal(urlRequest.body instanceof FormData, true);
assert.deepEqual([...urlRequest.body.keys()].sort(),
    ['appkey', 'signature', 'timestamp', 'source', 'wavUri', 'willReduceNoise', 'willStripSilence'].sort());
assert.equal(urlRequest.body.get('appkey'), fixture.appKey);
assert.equal(urlRequest.body.get('signature'), signatureFor(fixture));
assert.equal(urlRequest.body.get('timestamp'), '1789720618');
assert.equal(urlRequest.body.get('source'), 'galaxy_fastv7');
assert.equal(urlRequest.body.get('wavUri'), 'https://example.invalid/offline-training.wav');
assert.equal(urlRequest.body.get('willReduceNoise'), 'true');
assert.equal(urlRequest.body.get('willStripSilence'), 'false');
assert.equal(urlRequest.body.has('appKey'), false);
assert.equal(urlRequest.body.has('appSecret'), false);
assertSettled(remote);
assertRedacted(remote);

for (const audioMode of ['file', 'url']) {
    const preview = harness();
    preview.element('previewOnly').checked = true;
    if (audioMode === 'file') preview.element('audioFile').files = [audioFile];
    else preview.audioUrl();
    await preview.submit();
    assert.equal(preview.requests.length, 0, 'Preview must send zero requests');
    assert.match(preview.output(), /未发送.*API|未调用.*API|仅预览|仅.*预览/);
    assertRedacted(preview);
    assertSettled(preview);
}

for (const credentials of [null, {}, { appKey: '', appSecret: 'secret' }, { appKey: 'key', appSecret: '  ' }]) {
    const invalid = harness(credentials);
    invalid.audioUrl();
    await invalid.submit();
    assert.equal(invalid.requests.length, 0, 'Invalid credentials must block fetch');
    assert.match(invalid.output(), /配置|认证|凭据/);
    assertSettled(invalid);
}
const noAudio = harness();
await noAudio.submit();
assert.equal(noAudio.requests.length, 0, 'Missing audio must block fetch');
assert.match(noAudio.output(), /音频/);
const hosted = harness(fixture, 'https:');
hosted.audioUrl();
await hosted.submit();
assert.equal(hosted.requests.length, 0);
assert.equal(hosted.loadedScripts(), 0, 'Hosted pages must not load local credentials');

// Run a success first to prove subsequent failures cannot retain a stale success banner.
remote.responses.push({ code: 10001, message: 'Parameters are missing' });
await remote.submit();
assert.match(remote.element('responseContent').textContent, /10001/);
assert.match(remote.output(), /Parameters are missing/);
assert.doesNotMatch(remote.element('statusResult').textContent, /offline-speaker|克隆成功/);
assert.match(remote.element('statusResult').textContent, /失败|未成功|错误|缺少|缺失/);
assertSettled(remote);
assertRedacted(remote);
assert.equal(remote.loadedScripts(), 1, 'Credentials should load only once per page');

for (const response of [
    { httpStatus: 503, body: { message: 'Offline provider unavailable' } },
    new Error('Offline network failure')
]) {
    const failed = harness();
    failed.audioUrl();
    failed.responses.push(response);
    await failed.submit();
    assert.equal(failed.requests.length, 1);
    assert.match(failed.output(), response instanceof Error ? /Offline network failure/ : /503/);
    assert.doesNotMatch(failed.element('statusResult').textContent, /克隆成功/);
    assertSettled(failed);
    assertRedacted(failed);
}

const concurrent = harness();
concurrent.audioUrl();
let finish;
concurrent.responses.push(() => new Promise(resolve => { finish = resolve; }));
const first = concurrent.submit();
const second = concurrent.submit();
for (let step = 0; step < 10 && !finish; step++) await Promise.resolve();
assert.equal(typeof finish, 'function', 'The first request should reach the offline fetch stub');
assert.equal(concurrent.requests.length, 1, 'Concurrent submit events must produce only one request');
assert.equal(concurrent.element('submitButton').disabled, true);
finish({ ok: true, status: 200, json: async () => ({ code: 0, speaker: 'offline-speaker' }) });
await Promise.all([first, second]);
assert.equal(concurrent.requests.length, 1);
assertSettled(concurrent);

if (process.argv.includes('--local-config')) {
    const configSandbox = { window: {} };
    vm.runInNewContext(readFileSync(new URL('../../.local-config/digital-human-clone.credentials.js', import.meta.url), 'utf8'),
        configSandbox, { filename: 'local-credentials-offline-only' });
    const credentials = configSandbox.window.DIGITAL_HUMAN_CLONE_AUTH;
    assert.equal(typeof credentials?.appKey, 'string', 'Local appKey must be configured');
    assert.equal(typeof credentials?.appSecret, 'string', 'Local appSecret must be configured');
    assert.equal(Boolean(credentials.appKey.trim() && credentials.appSecret.trim()), true,
        'Local credentials must be non-empty');
    const local = harness(credentials);
    const loaded = await local.sandbox.loadAuthConfig();
    assert.equal(local.sandbox.generateSignature('1789720618', loaded) === signatureFor(credentials), true,
        'Local credentials must produce the expected signature');
    local.audioUrl();
    local.element('previewOnly').checked = true;
    await local.submit();
    assert.equal(local.requests.length, 0);
    assertRedacted(local, credentials);
    console.log('Local credential loading, signature and preview verified offline (values hidden).');
}
console.log('PASS: UTF-8 MD5, file/URL multipart contracts, redaction, preview, guards, success/errors, duplicate-submit guard. External API calls: 0.');

import { createRequire } from 'node:module';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mkdir, writeFile, readFile, readdir } from 'node:fs/promises';
import { createServer } from 'node:http';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(resolve(root, 'feibo/package.json'));
const { build } = require('esbuild');
const output = resolve(root, 'tmp/video-generation-ui-preview');
await mkdir(output, { recursive: true });
const assets = resolve(root, 'feibo/dist/assets');
const baseCss = (await readdir(assets)).find(file => /^index.*\.css$/.test(file));
if (!baseCss) throw new Error('Build the frontend before running the preview');
await writeFile(resolve(output, 'base.css'), await readFile(resolve(assets, baseCss)));
await build({ entryPoints: [resolve(root, 'ops/video-generation-qa/entry.tsx')], bundle: true, outfile: resolve(output, 'app.js'),
  define: { 'import.meta.env': '{}' },
  jsx: 'automatic', loader: { '.woff2': 'file', '.woff': 'file', '.ttf': 'file' },
  nodePaths: [resolve(root, 'feibo/node_modules')],
  plugins: [{ name: 'isolated-ui-test', setup(b) {
    b.onResolve({ filter: /^react(?:\/.*)?$|^react-dom(?:\/.*)?$/ }, args => ({ path: require.resolve(args.path) }));
    b.onResolve({ filter: /^(?:@\/services\/auth|\.\/http\/backendClient|\.\/authApi)$/ }, () => ({ path: resolve(root, 'ops/video-generation-qa/api.ts') }));
    b.onResolve({ filter: /^@\/components\/RechargeModal$/ }, () => ({ path: resolve(root, 'ops/video-generation-qa/recharge.tsx') }));
    b.onResolve({ filter: /^@\// }, args => {
      const path = resolve(root, args.importer.includes('feibo_backend') ? 'feibo_backend/src' : 'feibo/src', args.path.slice(2));
      return { path: ['.ts', '.tsx', '/index.ts'].map(extension => path + extension).find(existsSync) || path };
    });
  }}],
});
await writeFile(resolve(output, 'index.html'), '<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>AI 视频生成 · 本地功能预览</title><link rel="stylesheet" href="/base.css"><link rel="stylesheet" href="/app.css"><style>body{margin:0;font-family:system-ui,sans-serif}button{cursor:pointer}*{box-sizing:border-box}</style></head><body><div id="root"></div><script src="/app.js"></script></body></html>');
createServer(async (request, response) => {
  const file = request.url === '/' ? 'index.html' : (request.url || '').slice(1);
  if (!/^[A-Za-z0-9._-]+$/.test(file)) { response.writeHead(404).end(); return; }
  try { const data = await readFile(resolve(output, file)); response.setHeader('Content-Type', file.endsWith('.js') ? 'application/javascript' : file.endsWith('.css') ? 'text/css' : file.endsWith('.webm') ? 'video/webm' : 'text/html; charset=utf-8'); response.end(data); }
  catch { response.writeHead(404).end(); }
}).listen(5198, '127.0.0.1', () => console.log('UI fixture preview: http://127.0.0.1:5198 (no live AI requests)'));

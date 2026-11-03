import { access, cp, rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const source = fileURLToPath(new URL('../dist/', import.meta.url))
const workspaceRoot = path.resolve(fileURLToPath(new URL('../../../../', import.meta.url)))
const targets = [
  path.join(workspaceRoot, 'feibo_backend', 'public', 'geo-agent-app'),
]

await access(path.join(source, 'index.html'))

for (const target of targets) {
  const relative = path.relative(workspaceRoot, target)
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error(`Refusing to sync outside workspace: ${target}`)
  }
  await rm(target, { recursive: true, force: true })
  await cp(source, target, { recursive: true })
  process.stdout.write(`Synced GEO frontend to ${relative}\n`)
}
